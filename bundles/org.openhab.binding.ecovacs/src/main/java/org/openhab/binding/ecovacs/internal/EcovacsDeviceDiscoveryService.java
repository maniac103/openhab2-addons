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
package org.openhab.binding.ecovacs.internal;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.pott.sucks.api.EcovacsApi;
import dev.pott.sucks.api.EcovacsApiException;
import dev.pott.sucks.api.EcovacsDevice;

@NonNullByDefault
@Component(service = DiscoveryService.class, configurationPid = "discovery.ecovacs")
public class EcovacsDeviceDiscoveryService extends AbstractDiscoveryService implements ThingHandlerService {
    private final Logger logger = LoggerFactory.getLogger(EcovacsDeviceDiscoveryService.class);

    private static final int DISCOVER_TIMEOUT_SECONDS = 10;

    private @NonNullByDefault({}) EcovacsApiHandler apiHandler;
    private @Nullable Future<?> scanFuture;

    public EcovacsDeviceDiscoveryService() {
        super(Collections.singleton(EcovacsBindingConstants.THING_TYPE_VACUUM), DISCOVER_TIMEOUT_SECONDS, true);
    }

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        if (handler instanceof EcovacsApiHandler) {
            this.apiHandler = (EcovacsApiHandler) handler;
            this.apiHandler.setDiscoveryService(this);
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return apiHandler;
    }

    @Override
    public void activate() {
        super.activate(null);
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    protected void startScan() {
        if (scanFuture != null) {
            logger.debug("Ecovacs device discovery scan already in progress");
            return;
        }

        logger.debug("Starting Ecovacs discovery scan");
        scanFuture = scheduler.submit(() -> {
            EcovacsApi api = apiHandler.getApi();
            if (api == null) {
                return;
            }

            try {
                List<EcovacsDevice> devices = api.getDevices();

                for (EcovacsDevice device : devices) {
                    deviceDiscovered(device);
                }
                for (Thing thing : apiHandler.getThing().getThings()) {
                    String serial = thing.getUID().getId();
                    if (!devices.stream().anyMatch(d -> serial.equals(d.getSerialNumber()))) {
                        thingRemoved(thing.getUID());
                    }
                }
            } catch (EcovacsApiException e) {
                logger.debug("Could not retrieve devices from Ecovacs API", e);
            } finally {
                removeOlderResults(getTimestampOfLastScan());
                scanFuture = null;
            }
        });
    }

    @Override
    public void stopScan() {
        logger.debug("Stopping Ecovacs discovery scan");
        final Future<?> scanFuture = this.scanFuture;
        if (scanFuture != null) {
            scanFuture.cancel(true);
        }
        super.stopScan();
    }

    private void deviceDiscovered(EcovacsDevice device) {
        // TODO: check whether device actually is a vacuum cleaner - how?

        ThingUID thingUID = new ThingUID(EcovacsBindingConstants.THING_TYPE_VACUUM, apiHandler.getThing().getUID(),
                device.getSerialNumber());
        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID)
                .withBridge(apiHandler.getThing().getUID())
                .withProperty(Thing.PROPERTY_SERIAL_NUMBER, device.getSerialNumber())
                .withProperty(Thing.PROPERTY_MODEL_ID, device.getModelName())
                .withProperty(Thing.PROPERTY_FIRMWARE_VERSION, device.getFirmwareVersion())
                .withRepresentationProperty(Thing.PROPERTY_MODEL_ID).build();
        thingDiscovered(discoveryResult);
    }
}
