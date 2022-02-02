/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.ecovacs.internal.api.impl;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.debugger.AbstractDebugger;
import org.jivesoftware.smack.debugger.SmackDebugger;
import org.jivesoftware.smack.debugger.SmackDebuggerFactory;
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler;
import org.jivesoftware.smack.packet.ErrorIQ;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.IQ.Type;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.openhab.binding.ecovacs.internal.api.EcovacsApiConfiguration;
import org.openhab.binding.ecovacs.internal.api.EcovacsApiException;
import org.openhab.binding.ecovacs.internal.api.EcovacsDevice;
import org.openhab.binding.ecovacs.internal.api.commands.GetCleanLogsCommand;
import org.openhab.binding.ecovacs.internal.api.commands.GetFirmwareVersionCommand;
import org.openhab.binding.ecovacs.internal.api.commands.IotDeviceCommand;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.Device;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandXmlResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalLoginResponse;
import org.openhab.binding.ecovacs.internal.api.model.CleanLogRecord;
import org.openhab.binding.ecovacs.internal.api.model.DeviceCapability;
import org.openhab.binding.ecovacs.internal.api.util.XPathUtils;
import org.openhab.core.io.net.http.TrustAllTrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;

import com.google.gson.Gson;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class EcovacsXmppDevice implements EcovacsDevice, SmackDebuggerFactory {
    private final Logger logger = LoggerFactory.getLogger(EcovacsXmppDevice.class);

    private final Device device;
    private final DeviceDescription desc;
    private final EcovacsApiImpl api;
    private final Gson gson;
    private @Nullable IncomingMessageHandler messageHandler;
    private @Nullable XMPPTCPConnection connection;
    private @Nullable Jid ownAddress;
    private @Nullable Jid targetAddress;
    private @Nullable Future<?> pingFuture;

    EcovacsXmppDevice(Device device, DeviceDescription desc, EcovacsApiImpl api, Gson gson) {
        this.device = device;
        this.desc = desc;
        this.api = api;
        this.gson = gson;
    }

    @Override
    public String getId() {
        return device.getDid();
    }

    @Override
    public String getSerialNumber() {
        return device.getName();
    }

    @Override
    public String getModelName() {
        return desc.modelName;
    }

    @Override
    public boolean hasCapability(DeviceCapability cap) {
        return desc.capabilities.contains(cap);
    }

    @Override
    public <T> T sendCommand(IotDeviceCommand<T> command) throws EcovacsApiException {
        IncomingMessageHandler handler = this.messageHandler;
        XMPPConnection conn = this.connection;
        Jid from = this.ownAddress;
        Jid to = this.targetAddress;
        if (handler == null || conn == null || from == null || to == null) {
            throw new EcovacsApiException("Not connected to device");
        }

        try {
            // Devices sometimes send no answer to commands for unknown reasons. Ecovacs'
            // app employs a similar retry mechanism, so this seems to be 'normal'.
            for (int retry = 0; retry < 3; retry++) {
                DeviceCommandIQ request = new DeviceCommandIQ(command, from, to);
                CommandResponseHolder responseHolder = new CommandResponseHolder();
                handler.registerPendingCommand(request.id, responseHolder);

                logger.trace("{}: sending command {}, retry {}", getSerialNumber(),
                        command.getName(ProtocolVersion.XML), retry);
                synchronized (responseHolder) {
                    conn.sendIqRequestAsync(request);
                    responseHolder.wait(1500);
                }
                handler.unregisterPendingCommand(request.id);

                String response = responseHolder.response;
                if (response != null) {
                    logger.trace("{}: Received command response XML {}", getSerialNumber(), response);

                    PortalIotCommandXmlResponse responseObj = new PortalIotCommandXmlResponse("", "", response, "");
                    return command.convertResponse(responseObj, ProtocolVersion.XML, gson);
                }
            }
        } catch (Exception e) {
            throw new EcovacsApiException(e);
        }

        throw new EcovacsApiException("No response for command " + command.getName(ProtocolVersion.XML));
    }

    @Override
    public List<CleanLogRecord> getCleanLogs() throws EcovacsApiException {
        return sendCommand(new GetCleanLogsCommand());
    }

    @Override
    public void connect(final EventListener listener, final ScheduledExecutorService scheduler)
            throws EcovacsApiException {
        EcovacsApiConfiguration config = api.getConfig();
        PortalLoginResponse loginData = api.getLoginData();
        if (loginData == null) {
            throw new EcovacsApiException("Can not connect when not logged in");
        }

        logger.trace("{}: Connecting to XMPP", getSerialNumber());

        String password = String.format("0/%s/%s", loginData.getResource(), loginData.getToken());
        String host = String.format("msg-%s.%s", config.getContinent(), config.getRealm());

        try {
            this.ownAddress = JidCreate.from(loginData.getUserId(), config.getRealm(), loginData.getResource());
            this.targetAddress = JidCreate.from(device.getDid(), device.getDeviceClass() + ".ecorobot.net", "atom");

            XMPPTCPConnectionConfiguration connConfig = XMPPTCPConnectionConfiguration.builder().setHost(host)
                    .setPort(5223).setUsernameAndPassword(loginData.getUserId(), password)
                    .setResource(loginData.getResource()).setXmppDomain(config.getRealm())
                    .setCustomX509TrustManager(TrustAllTrustManager.getInstance()).setSendPresence(false)
                    .setDebuggerFactory(this).build();

            XMPPTCPConnection conn = new XMPPTCPConnection(connConfig);
            conn.addConnectionListener(new ConnectionListener() {
                @Override
                public void connected(@Nullable XMPPConnection connection) {
                }

                @Override
                public void authenticated(@Nullable XMPPConnection connection, boolean resumed) {
                }

                @Override
                public void connectionClosed() {
                }

                @Override
                public void connectionClosedOnError(@Nullable Exception e) {
                    logger.trace("{}: XMPP connection failed", getSerialNumber(), e);
                    if (e != null) {
                        listener.onEventStreamFailure(EcovacsXmppDevice.this, e);
                    }
                }
            });

            messageHandler = new IncomingMessageHandler(listener);

            conn.registerIQRequestHandler(messageHandler);
            conn.connect();
            this.connection = conn;

            conn.login();

            listener.onFirmwareVersionChanged(this, sendCommand(new GetFirmwareVersionCommand()));

            ReconnectionManager reconnectionManager = ReconnectionManager.getInstanceFor(conn);
            reconnectionManager.enableAutomaticReconnection();

            pingFuture = scheduler.scheduleWithFixedDelay(this::sendPing, 30, 30, TimeUnit.SECONDS);
        } catch (EcovacsApiException e) {
            throw e;
        } catch (XMPPException | SmackException | InterruptedException | IOException e) {
            throw new EcovacsApiException(e);
        }
    }

    @Override
    public void disconnect() {
        XMPPTCPConnection conn = this.connection;
        if (conn != null) {
            conn.disconnect();
        }
        this.connection = null;
        Future<?> pingFuture = this.pingFuture;
        if (pingFuture != null) {
            pingFuture.cancel(true);
        }
        this.pingFuture = null;
        IncomingMessageHandler handler = this.messageHandler;
        if (handler != null) {
            handler.dispose();
        }
        this.messageHandler = null;
    }

    private void sendPing() {
        Jid from = this.ownAddress;
        Jid to = this.targetAddress;
        if (from == null || to == null) {
            return;
        }
        try {
            this.connection.sendStanza(new PingIQ(from, to));
        } catch (SmackException | InterruptedException e) {
            // ignored
        }
    }

    @Override
    public SmackDebugger create(@Nullable XMPPConnection connection) throws IllegalArgumentException {
        return new AbstractDebugger(connection) {
            @Override
            protected void log(@Nullable String logMessage) {
                logger.trace("{}: {}", getSerialNumber(), logMessage);
            }

            @Override
            protected void log(@Nullable String logMessage, @Nullable Throwable throwable) {
                logger.trace("{}: {}", getSerialNumber(), logMessage, throwable);
            }
        };
    }

    private class IncomingMessageHandler extends AbstractIqRequestHandler {
        private final EventListener listener;
        private final ReportParser parser;
        private final ConcurrentHashMap<String, CommandResponseHolder> pendingCommands = new ConcurrentHashMap<>();
        private boolean disposed;

        IncomingMessageHandler(EventListener listener) {
            super("query", "com:ctl", Type.set, Mode.async);
            this.listener = listener;
            this.parser = new XmlReportParser(EcovacsXmppDevice.this, listener, gson);
        }

        void registerPendingCommand(String id, CommandResponseHolder responseHolder) {
            pendingCommands.put(id, responseHolder);
        }

        void unregisterPendingCommand(String id) {
            pendingCommands.remove(id);
        }

        void dispose() {
            disposed = true;
        }

        @Override
        public @Nullable IQ handleIQRequest(@Nullable IQ iqRequest) {
            if (disposed) {
                return null;
            }

            logger.trace("{}: Incoming XMPP packet {}", getSerialNumber(), iqRequest);

            if (iqRequest instanceof DeviceCommandIQ) {
                DeviceCommandIQ iq = (DeviceCommandIQ) iqRequest;

                try {
                    if (!iq.id.isEmpty()) {
                        CommandResponseHolder responseHolder = pendingCommands.remove(iq.id);
                        if (responseHolder != null) {
                            synchronized (responseHolder) {
                                responseHolder.response = iq.payload;
                                responseHolder.notifyAll();
                            }
                        }
                    } else {
                        Optional<String> eventNameOpt = XPathUtils.getFirstXPathMatchOpt(iq.payload, "//ctl/@td")
                                .map(n -> n.getNodeValue());
                        if (eventNameOpt.isPresent()) {
                            parser.handleMessage(eventNameOpt.get(), iq.payload);
                        } else {
                            logger.debug("{}: Got unexpected XML payload {}", getSerialNumber(), iq.payload);
                        }
                    }
                } catch (Exception e) {
                    listener.onEventStreamFailure(EcovacsXmppDevice.this, e);
                }
            } else if (iqRequest instanceof ErrorIQ) {
                StanzaError error = ((ErrorIQ) iqRequest).getError();
                logger.trace("{}: Got error response {}", getSerialNumber(), error);
                listener.onEventStreamFailure(EcovacsXmppDevice.this,
                        new XMPPException.XMPPErrorException(iqRequest, error));
            }
            return null;
        }
    }

    private static class CommandResponseHolder {
        @Nullable
        String response;
    }

    private static class DeviceCommandIQ extends IQ {
        static final String TAG_NAME = "query";
        static final String NAMESPACE = "com:ctl";

        private final String payload;
        final String id;

        // request
        public DeviceCommandIQ(IotDeviceCommand<?> cmd, Jid from, Jid to) throws Exception {
            super(TAG_NAME, NAMESPACE);
            setType(Type.set);
            setTo(to);
            setFrom(from);

            this.id = createRequestId();
            this.payload = cmd.getXmlPayload(id);
        }

        // response
        public DeviceCommandIQ(@Nullable String id, String payload) {
            super(TAG_NAME, NAMESPACE);
            this.id = id != null ? id : "";
            this.payload = payload.replaceAll("\n|\r", "");
        }

        @Override
        protected @Nullable IQChildElementXmlStringBuilder getIQChildElementBuilder(
                @Nullable IQChildElementXmlStringBuilder xml) {
            if (xml != null) {
                xml.rightAngleBracket();
                xml.append(payload);
            }
            return xml;
        }

        private String createRequestId() {
            // Ecovacs' app uses numbers for request IDs, so better constrain ourselves to that as well
            int random8DigitNumber = 10000000 + new Random().nextInt(90000000);
            return Integer.toString(random8DigitNumber);
        }
    }

    private static class PingIQ extends IQ {
        public PingIQ(Jid from, Jid to) {
            super("ping", "urn:xmpp:ping");
            setType(Type.get);
            setFrom(from);
            setTo(to);
        }

        @Override
        protected @Nullable IQChildElementXmlStringBuilder getIQChildElementBuilder(
                @Nullable IQChildElementXmlStringBuilder xml) {
            return xml;
        }
    }

    private static class CommandIQProvider extends IQProvider<@Nullable DeviceCommandIQ> {
        @Override
        public @Nullable DeviceCommandIQ parse(@Nullable XmlPullParser parser, int initialDepth) throws Exception {
            @Nullable
            DeviceCommandIQ packet = null;

            if (parser == null) {
                return null;
            }

            outerloop: while (true) {
                switch (parser.next()) {
                    case XmlPullParser.START_TAG:
                        if (parser.getDepth() == initialDepth + 1) {
                            String id = parser.getAttributeValue("", "id");
                            String payload = PacketParserUtils.parseElement(parser).toString();
                            packet = new DeviceCommandIQ(id, payload);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (parser.getDepth() == initialDepth) {
                            break outerloop;
                        }
                        break;
                }
            }

            return packet;
        }
    }

    static {
        ProviderManager.addIQProvider(DeviceCommandIQ.TAG_NAME, DeviceCommandIQ.NAMESPACE, new CommandIQProvider());
    }
}
