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

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.ecovacs.internal.api.EcovacsApiConfiguration;
import org.openhab.binding.ecovacs.internal.api.EcovacsApiException;
import org.openhab.binding.ecovacs.internal.api.EcovacsDevice;
import org.openhab.binding.ecovacs.internal.api.commands.GetFirmwareVersionCommand;
import org.openhab.binding.ecovacs.internal.api.commands.IotDeviceCommand;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.deviceapi.BatteryReport;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.deviceapi.ChargeReport;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.deviceapi.CleanReport;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.deviceapi.ErrorReport;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.deviceapi.StatsReport;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.deviceapi.WaterInfoReport;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.Device;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandJsonResponse.JsonResponsePayloadWrapper;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalLoginResponse;
import org.openhab.binding.ecovacs.internal.api.model.CleanLogRecord;
import org.openhab.binding.ecovacs.internal.api.model.DeviceCapability;
import org.openhab.binding.ecovacs.internal.api.model.MoppingWaterAmount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth;

import io.netty.handler.ssl.util.SimpleTrustManagerFactory;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class EcovacsIotMqDevice implements EcovacsDevice {
    private final Logger logger = LoggerFactory.getLogger(EcovacsIotMqDevice.class);

    private final Device device;
    private final DeviceDescription desc;
    private final String firmwareVersion;
    private final EcovacsApiImpl api;
    private final Gson gson;
    private @Nullable Mqtt3AsyncClient mqttClient;

    EcovacsIotMqDevice(Device device, DeviceDescription desc, EcovacsApiImpl api, Gson gson)
            throws EcovacsApiException {
        this.device = device;
        this.desc = desc;
        this.firmwareVersion = api.sendIotCommand(device, desc, new GetFirmwareVersionCommand());
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
    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    @Override
    public <T> T sendCommand(IotDeviceCommand<T> command) throws EcovacsApiException {
        return api.sendIotCommand(device, desc, command);
    }

    @Override
    public List<CleanLogRecord> getCleanLogs() throws EcovacsApiException {
        return api.fetchCleanLogs(device).stream().sorted((lhs, rhs) -> Long.compare(rhs.timestamp, lhs.timestamp))
                .map(record -> new CleanLogRecord(record.timestamp, record.duration, record.area,
                        Optional.ofNullable(record.imageUrl), record.type))
                .collect(Collectors.toList());
    }

    @Override
    public void listenForEvents(final EventListener listener) throws EcovacsApiException {
        EcovacsApiConfiguration config = api.getConfig();
        PortalLoginResponse loginData = api.getLoginData();
        if (loginData == null) {
            throw new EcovacsApiException("Can not connect when not logged in");
        }

        // TOOD: use realm from config
        String userName = loginData.getUserId() + "@ecouser";
        String host = String.format("mq-%s.ecouser.net", config.getContinent());

        Mqtt3SimpleAuth auth = Mqtt3SimpleAuth.builder().username(userName).password(loginData.getToken().getBytes())
                .build();

        MqttClientSslConfig sslConfig = MqttClientSslConfig.builder().trustManagerFactory(createTrustManagerFactory())
                .build();

        final Mqtt3AsyncClient client = MqttClient.builder().useMqttVersion3()
                .identifier(userName + "/" + loginData.getResource()).simpleAuth(auth).serverHost(host).serverPort(8883)
                .sslConfig(sslConfig).buildAsync();

        client.connect().whenComplete((connAck, connError) -> {
            if (connError != null) {
                listener.onEventStreamFailure(this, connError);
                return;
            }

            logger.debug("Established MQTT connection to device {}", getSerialNumber());
            final MessageHandler messageHandler = desc.usesJsonApi ? new JsonMessageHandler(listener)
                    : new XmlMessageHandler();
            String topic = String.format("iot/atr/+/%s/%s/%s/+", device.getDid(), device.getDeviceClass(),
                    device.getResource());
            client.subscribeWith().topicFilter(topic).callback(publish -> {
                String payload = new String(publish.getPayloadAsBytes());
                try {
                    messageHandler.handleMessage(publish.getTopic().toString(), payload);
                } catch (Exception e) {
                    listener.onEventStreamFailure(this, e);
                }
            }).send().whenComplete((subAck, subError) -> {
                if (subError != null) {
                    listener.onEventStreamFailure(this, subError);
                }
            });
        });

        this.mqttClient = client;
    }

    @Override
    public void stopListeningForEvents() {
        Mqtt3AsyncClient client = this.mqttClient;
        if (client != null) {
            client.disconnect();
        }
    }

    private TrustManagerFactory createTrustManagerFactory() {
        final TrustManager noOpTrustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(final X509Certificate @Nullable [] chain, final @Nullable String authType) {
            }

            @Override
            public void checkServerTrusted(final X509Certificate @Nullable [] chain, final @Nullable String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };

        return new SimpleTrustManagerFactory() {
            @Override
            protected void engineInit(@Nullable KeyStore keyStore) throws Exception {
            }

            @Override
            protected void engineInit(@Nullable ManagerFactoryParameters managerFactoryParameters) throws Exception {
            }

            @Override
            protected TrustManager[] engineGetTrustManagers() {
                return new TrustManager[] { noOpTrustManager };
            }
        };
    }

    private interface MessageHandler {
        void handleMessage(String topic, String payload);
    }

    private class XmlMessageHandler implements MessageHandler {
        @Override
        public void handleMessage(String topic, String payload) {
            logger.debug("{}: Got MQTT message on topic {}: {}", getSerialNumber(), topic, payload);
            // TODO: parse XML
        }
    }

    private class JsonMessageHandler implements MessageHandler {
        private final EventListener listener;

        JsonMessageHandler(EventListener listener) {
            this.listener = listener;
        }

        @Override
        public void handleMessage(String topic, String payload) {
            String eventName = topic.split("/")[2].toLowerCase();
            JsonResponsePayloadWrapper response = gson.fromJson(payload, JsonResponsePayloadWrapper.class);
            if (response == null) {
                return;
            }
            // TODO: update FW version?

            if (eventName.startsWith("on")) {
                eventName = eventName.substring(2);
            } else if (eventName.startsWith("report")) {
                eventName = eventName.substring(6);
            }
            if (eventName.endsWith("_v2")) {
                eventName = eventName.substring(0, eventName.length() - 3);
            }

            logger.trace("{}: Got MQTT message on topic {}: {}", getSerialNumber(), topic, payload);

            switch (eventName) {
                case "battery": {
                    BatteryReport report = payloadAs(response, BatteryReport.class);
                    listener.onBatteryLevelUpdated(EcovacsIotMqDevice.this, report.percent);
                    break;
                }
                case "chargestate": {
                    ChargeReport report = payloadAs(response, ChargeReport.class);
                    listener.onChargingStateUpdated(EcovacsIotMqDevice.this, report.isCharging != 0);
                    break;
                }
                case "cleaninfo": {
                    CleanReport report = payloadAs(response, CleanReport.class);
                    listener.onCleaningModeUpdated(EcovacsIotMqDevice.this, report.determineCleanMode(gson));
                    break;
                }
                case "error": {
                    ErrorReport report = payloadAs(response, ErrorReport.class);
                    for (Integer code : report.errorCodes) {
                        listener.onErrorReported(EcovacsIotMqDevice.this, code);
                    }
                }
                case "evt": {
                    // EventReport report = payloadAs(reponse, EventReport.class);
                    break;
                }
                case "lifespan": {
                    // ComponentLifeSpanReport report = payloadAs(response, ComponentLifeSpanReport.class);
                    break;
                }
                case "speed": {
                    // SpeedReport report = payloadAs(response, SpeedReport.class);
                    // SuctionPower power = SuctionPower.fromJsonValue(report.speedLevel);
                    // TODO: report change
                    break;
                }
                case "stats": {
                    StatsReport report = payloadAs(response, StatsReport.class);
                    listener.onCleaningStatsUpdated(EcovacsIotMqDevice.this, report.area, report.timeInSeconds);
                    break;
                }
                case "waterinfo": {
                    WaterInfoReport report = payloadAs(response, WaterInfoReport.class);
                    listener.onWaterSystemUpdated(EcovacsIotMqDevice.this, report.waterPlatePresent != 0,
                            MoppingWaterAmount.fromApiValue(report.waterAmount));
                    break;
                }
            }
        }

        private <T> T payloadAs(JsonResponsePayloadWrapper response, Class<T> clazz) {
            @Nullable
            T payload = gson.fromJson(response.body.payload, clazz);
            if (payload == null) {
                throw new IllegalArgumentException("Null payload");
            }
            return payload;
        }
    }
}
