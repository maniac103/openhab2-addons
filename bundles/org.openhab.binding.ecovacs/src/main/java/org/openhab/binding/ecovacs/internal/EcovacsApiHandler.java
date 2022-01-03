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

import com.google.gson.Gson;

import dev.pott.sucks.api.EcovacsApi;
import dev.pott.sucks.api.EcovacsApiConfiguration;
import dev.pott.sucks.api.dto.response.main.AccessData;
import dev.pott.sucks.api.dto.response.main.AuthCode;
import dev.pott.sucks.api.dto.response.portal.PortalLoginResponse;
import dev.pott.sucks.util.MD5Util;

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
    private @Nullable PortalLoginResponse loginData;
    private HttpClientFactory httpClientFactory;

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

    // FIXME: the API should probably deal with this internally
    @Nullable
    public PortalLoginResponse getLoginData() {
        return loginData;
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
            EcovacsConfiguration config = getConfigAs(EcovacsConfiguration.class);
            EcovacsApiConfiguration apiConfig = new EcovacsApiConfiguration(
                    MD5Util.getMD5Hash(String.valueOf(System.currentTimeMillis())), // FIXME: unique install ID
                    config.email, config.password,
                    // FIXME: can get this from locale?
                    "EU", "DE", "EN");

            api = new EcovacsApi(httpClientFactory.getCommonHttpClient(), new Gson(), apiConfig);
            loginData = null;

            AccessData ad = api.login();
            if (ad != null) {
                AuthCode ac = api.getAuthCode(ad);
                if (ac != null) {
                    loginData = api.portalLogin(ac, ad);
                }
            }

            if (loginData != null) {
                updateStatus(ThingStatus.ONLINE);
            } else if (ad != null) {
                // login was successful
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                // TODO: schedule reinit?
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
            }

            final EcovacsDeviceDiscoveryService discoveryService = this.discoveryService;
            if (discoveryService != null) {
                discoveryService.startScan();
            }
        });
    }
}
