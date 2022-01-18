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

import java.util.Collection;
import java.util.Collections;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.pott.sucks.api.ClientKeys;
import dev.pott.sucks.api.EcovacsApi;
import dev.pott.sucks.api.EcovacsApiException;
import dev.pott.sucks.api.util.MD5Util;

/**
 * The {@link EcovacsDeviceHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class EcovacsApiHandler extends BaseBridgeHandler {
    private final Logger logger = LoggerFactory.getLogger(EcovacsDeviceHandler.class);

    private @Nullable EcovacsDeviceDiscoveryService discoveryService;
    private @Nullable EcovacsApi api;
    private final HttpClientFactory httpClientFactory;

    public EcovacsApiHandler(Bridge bridge, HttpClientFactory httpClientFactory) {
        super(bridge);
        this.httpClientFactory = httpClientFactory;
    }

    public void setDiscoveryService(EcovacsDeviceDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @Nullable
    public EcovacsApi getApi() {
        return api;
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Ecovacs account '{}'", getThing().getUID().getId());
        initializeApi();
    }

    @Override
    public void dispose() {
        super.dispose();
        final EcovacsDeviceDiscoveryService discoveryService = this.discoveryService;
        if (discoveryService != null) {
            discoveryService.stopScan();
        }
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(EcovacsDeviceDiscoveryService.class);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (RefreshType.REFRESH == command) {
            logger.debug("Refreshing Ecovacs API account '{}'", getThing().getUID().getId());
            initializeApi();
        }
    }

    private void initializeApi() {
        scheduler.execute(() -> {
            EcovacsApiConfiguration config = getConfigAs(EcovacsApiConfiguration.class);
            dev.pott.sucks.api.EcovacsApiConfiguration apiConfig = new dev.pott.sucks.api.EcovacsApiConfiguration(
                    MD5Util.getMD5Hash(String.valueOf(System.currentTimeMillis())), // FIXME: unique install ID
                    config.email, config.password,
                    // FIXME: can get this from locale?
                    "EU", "DE", "EN", ClientKeys.CLIENT_KEY, ClientKeys.CLIENT_SECRET, ClientKeys.AUTH_CLIENT_KEY,
                    ClientKeys.AUTH_CLIENT_SECRET);

            EcovacsApi api = EcovacsApi.create(httpClientFactory.getCommonHttpClient(), apiConfig);
            try {
                api.loginAndGetAccessToken();
                this.api = api;
                updateStatus(ThingStatus.ONLINE);

                final EcovacsDeviceDiscoveryService discoveryService = this.discoveryService;
                if (discoveryService != null) {
                    discoveryService.startScan();
                }
            } catch (EcovacsApiException e) {
                logger.debug("Ecovacs API login failed", e);
                this.api = null;
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            }
        });
    }
}
