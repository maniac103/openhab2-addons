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
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.openhab.binding.ecovacs.internal.api.EcovacsApiConfiguration;
import org.openhab.binding.ecovacs.internal.api.EcovacsApiException;
import org.openhab.binding.ecovacs.internal.api.EcovacsDevice;
import org.openhab.binding.ecovacs.internal.api.commands.GetFirmwareVersionCommand;
import org.openhab.binding.ecovacs.internal.api.commands.IotDeviceCommand;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.Device;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandXmlResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalLoginResponse;
import org.openhab.binding.ecovacs.internal.api.model.CleanLogRecord;
import org.openhab.binding.ecovacs.internal.api.model.DeviceCapability;
import org.openhab.core.io.net.http.TrustAllTrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class EcovacsXmppDevice implements EcovacsDevice {
    private final Logger logger = LoggerFactory.getLogger(EcovacsXmppDevice.class);

    private final Device device;
    private final DeviceDescription desc;
    private final EcovacsApiImpl api;
    private final Gson gson;
    private @Nullable XMPPTCPConnection connection;
    private @Nullable Jid ownAddress;
    private @Nullable Jid targetAddress;

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
        XMPPConnection conn = this.connection;
        Jid from = this.ownAddress;
        Jid to = this.targetAddress;
        if (conn == null || from == null || to == null) {
            throw new EcovacsApiException("Not connected to device");
        }

        try {
            IQ request = new CommandIQ(command, from, to);
            logger.trace("{}: sending command XML {}", getSerialNumber(), request.toXML(""));
            IQ response = conn.sendIqRequestAndWaitForResponse(request);
            logger.trace("{}: received command response XML {}", getSerialNumber(), response.toXML(""));

            PortalIotCommandXmlResponse responseObj = new PortalIotCommandXmlResponse("", "",
                    response.getChildElementXML().toString(), "");
            return command.convertResponse(responseObj, ProtocolVersion.XML, gson);
        } catch (Exception e) {
            throw new EcovacsApiException(e);
        }
    }

    @Override
    public List<CleanLogRecord> getCleanLogs() throws EcovacsApiException {
        // TODO
        return Collections.emptyList();
    }

    @Override
    public void listenForEvents(final EventListener listener) throws EcovacsApiException {
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
                    .setCustomX509TrustManager(TrustAllTrustManager.getInstance()).setSendPresence(false).build();

            XMPPTCPConnection conn = new XMPPTCPConnection(connConfig);
            conn.addConnectionListener(new ConnectionListener() {
                @Override
                public void connected(@Nullable XMPPConnection connection) {
                    logger.trace("{}: XMPP connected", getSerialNumber());
                }

                @Override
                public void authenticated(@Nullable XMPPConnection connection, boolean resumed) {
                    logger.trace("{}: XMPP authenticated", getSerialNumber());
                    try {
                        listener.onFirmwareVersionChanged(EcovacsXmppDevice.this,
                                sendCommand(new GetFirmwareVersionCommand()));
                    } catch (EcovacsApiException e) {
                        listener.onEventStreamFailure(EcovacsXmppDevice.this, e);
                    }
                }

                @Override
                public void connectionClosed() {
                    logger.trace("{}: XMPP connection closed", getSerialNumber());
                }

                @Override
                public void connectionClosedOnError(@Nullable Exception e) {
                    logger.trace("{}: XMPP connection failed", getSerialNumber(), e);
                    if (e != null) {
                        listener.onEventStreamFailure(EcovacsXmppDevice.this, e);
                    }
                }
            });

            final ReportParser parser = new XmlReportParser(this, listener, gson);

            conn.addAsyncStanzaListener(packet -> {
                logger.trace("{}: Incoming XMPP packet {}", getSerialNumber(), packet);
                IQ iq = (IQ) packet;
                logger.trace("{}: ID {}, type {}, XML {}", getSerialNumber(), iq.getStanzaId(), iq.getType(),
                        iq.getChildElementXML());
                try {
                    parser.handleMessage("TODO", "TODO");
                } catch (Exception e) {
                    listener.onEventStreamFailure(this, e);
                }
            }, new StanzaTypeFilter(IQ.class));

            conn.connect();
            this.connection = conn;

            conn.login();

            ReconnectionManager reconnectionManager = ReconnectionManager.getInstanceFor(conn);
            reconnectionManager.enableAutomaticReconnection();

            // TODO: ping timer
        } catch (XMPPException | SmackException | InterruptedException | IOException e) {
            throw new EcovacsApiException(e);
        }
    }

    @Override
    public void stopListeningForEvents() {
        XMPPTCPConnection conn = this.connection;
        // TODO: kill ping timer
        if (conn != null) {
            conn.disconnect();
        }
    }

    private static class CommandIQ extends IQ {
        private final String payload;

        public CommandIQ(IotDeviceCommand<?> cmd, Jid from, Jid to) throws Exception {
            super("query", "com:ctl");
            setType(Type.set);
            setTo(to);
            setFrom(from);
            this.payload = cmd.getXmlPayload();
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
    }
}
