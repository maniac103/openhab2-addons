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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.pott.sucks.api.EcovacsApi;
import dev.pott.sucks.api.dto.response.portal.Device;
import dev.pott.sucks.api.dto.response.portal.IotProduct;
import dev.pott.sucks.api.dto.response.portal.PortalLoginResponse;

@NonNullByDefault
public class EcovacsDeviceDiscoveryService extends AbstractDiscoveryService implements ThingHandlerService {
    private final Logger logger = LoggerFactory.getLogger(EcovacsDeviceDiscoveryService.class);

    private static final int DISCOVER_TIMEOUT_SECONDS = 5;

    private @NonNullByDefault({}) EcovacsApiHandler apiHandler;
    private @Nullable List<IotProduct> products;
    private @Nullable Future<?> scanFuture;

    public EcovacsDeviceDiscoveryService() {
        super(DISCOVER_TIMEOUT_SECONDS);
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
            PortalLoginResponse loginData = apiHandler.getLoginData();
            if (api == null || loginData == null) {
                return;
            }

            if (products == null) {
                products = api.getIotProductMap(loginData).getProducts();
            }
            for (Device device : api.getDevices(loginData).getDevices()) {
                deviceDiscovered(device);
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

    private void deviceDiscovered(Device device) {
        Optional<IotProduct> product = products.stream()
                .filter(prod -> prod.getClassId().equals(device.getDeviceClass())).findFirst();
        if (!product.isPresent()) {
            logger.debug("Device {} has unknown class {}, ignoring.", device.getDid(), device.getDeviceClass());
            return;
        }

        // TODO: check whether device actually is a vacuum cleaner - how?

        ThingUID thingUID = new ThingUID(EcovacsBindingConstants.THING_TYPE_VACUUM, apiHandler.getThing().getUID(),
                device.getDid());
        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID)
                .withBridge(apiHandler.getThing().getUID()).withProperty("id", device.getDid())
                .withProperty("serial", device.getName()).withProperty("type", product.get().getDefinition().name)
                .withRepresentationProperty("type").build();
        thingDiscovered(discoveryResult);
    }
}
