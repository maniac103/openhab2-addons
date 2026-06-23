/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.ecoflow.internal.discovery;

import static org.openhab.binding.ecoflow.internal.EcoflowBindingConstants.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.ecoflow.internal.api.EcoflowApi;
import org.openhab.binding.ecoflow.internal.api.EcoflowApiException;
import org.openhab.binding.ecoflow.internal.api.dto.response.DeviceListResponseEntry;
import org.openhab.binding.ecoflow.internal.handler.EcoflowApiHandler;
import org.openhab.binding.ecoflow.internal.util.SchedulerTask;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EcoflowDeviceDiscoveryService} is used for discovering devices registered in the cloud account.
 *
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
@Component(scope = ServiceScope.PROTOTYPE, service = DiscoveryService.class, configurationPid = "discovery.ecoflow")
public class EcoflowDeviceDiscoveryService extends AbstractThingHandlerDiscoveryService<EcoflowApiHandler> {
    private final Logger logger = LoggerFactory.getLogger(EcoflowDeviceDiscoveryService.class);

    private static final int DISCOVER_TIMEOUT_SECONDS = 10;

    private static final Map<String, ThingTypeUID> PRODUCT_NAME_TO_THING_TYPE = Map.ofEntries(
            Map.entry("DELTA 2", THING_TYPE_DELTA2), Map.entry("DELTA 2 Max", THING_TYPE_DELTA2MAX),
            Map.entry("PowerStream", THING_TYPE_POWERSTREAM),
            Map.entry("STREAM Microinverter", THING_TYPE_STREAM_MICROINVERTER),
            Map.entry("STREAM AC", THING_TYPE_STREAM_AC), Map.entry("STREAM Max", THING_TYPE_STREAM_MAX),
            Map.entry("STREAM Pro", THING_TYPE_STREAM_PRO_ULTRA),
            Map.entry("STREAM AC Pro", THING_TYPE_STREAM_PRO_ULTRA),
            Map.entry("STREAM Ultra", THING_TYPE_STREAM_PRO_ULTRA),
            Map.entry("STREAM Ultra X", THING_TYPE_STREAM_PRO_ULTRA));

    private Optional<EcoflowApi> api = Optional.empty();
    private final SchedulerTask onDemandScanTask = new SchedulerTask(scheduler, logger, "OnDemandScan",
            this::scanForDevices);
    private final SchedulerTask backgroundScanTask = new SchedulerTask(scheduler, logger, "BackgroundScan",
            this::scanForDevices);

    public EcoflowDeviceDiscoveryService() {
        super(EcoflowApiHandler.class, Set.of(THING_TYPE_DELTA2), DISCOVER_TIMEOUT_SECONDS, false);
    }

    @Override
    public void initialize() {
        thingHandler.setDiscoveryService(this);
        super.initialize();
    }

    @Override
    protected synchronized void startBackgroundDiscovery() {
        stopBackgroundDiscovery();
        backgroundScanTask.scheduleRecurring(60);
    }

    @Override
    protected synchronized void stopBackgroundDiscovery() {
        backgroundScanTask.cancel();
    }

    public synchronized void startScanningWithApi(EcoflowApi api) {
        this.api = Optional.of(api);
        onDemandScanTask.cancel();
        startScan();
    }

    @Override
    public synchronized void startScan() {
        logger.debug("Starting Ecoflow discovery scan");
        onDemandScanTask.submit();
    }

    @Override
    public synchronized void stopScan() {
        logger.debug("Stopping Ecoflow discovery scan");
        onDemandScanTask.cancel();
        super.stopScan();
    }

    private void scanForDevices() {
        this.api.ifPresent(api -> {
            Instant timestampOfLastScan = getTimestampOfLastScan();
            try {
                List<DeviceListResponseEntry> devices = api.getDeviceList();
                if (devices != null) {
                    logger.debug("Ecoflow discovery found {} devices", devices.size());
                    for (DeviceListResponseEntry device : devices) {
                        deviceDiscovered(device);
                    }
                    for (Thing thing : thingHandler.getThing().getThings()) {
                        String serial = thing.getUID().getId();
                        if (!devices.stream().anyMatch(d -> serial.equals(d.serialNumber))) {
                            thingRemoved(thing.getUID());
                        }
                    }
                } else {
                    logger.debug("No devices available in Ecoflow API");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (EcoflowApiException e) {
                logger.debug("Could not retrieve devices from Ecoflow API", e);
            } finally {
                removeOlderResults(timestampOfLastScan);
            }
        });
    }

    private void deviceDiscovered(DeviceListResponseEntry device) {
        Optional<String> productName = Optional.ofNullable(device.productName);
        if (productName.isEmpty()) {
            productName = PRODUCT_NAME_TO_THING_TYPE.keySet().stream().filter(pn -> device.deviceName.startsWith(pn))
                    .findFirst();
        }

        ThingTypeUID thingTypeUID = productName.map(PRODUCT_NAME_TO_THING_TYPE::get).orElse(null);
        if (thingTypeUID == null) {
            logger.debug("Found device {} named {} with unhandled product type {}", device.serialNumber,
                    device.deviceName, device.productName);
            return;
        }

        ThingUID thingUID = new ThingUID(thingTypeUID, thingHandler.getThing().getUID(), device.serialNumber);
        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID)
                .withBridge(thingHandler.getThing().getUID()).withLabel(device.deviceName)
                .withProperty(Thing.PROPERTY_SERIAL_NUMBER, device.serialNumber)
                .withProperty(Thing.PROPERTY_MODEL_ID, device.productName)
                .withRepresentationProperty(Thing.PROPERTY_SERIAL_NUMBER).build();
        thingDiscovered(discoveryResult);
    }
}
