/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.ecovacs.internal.api.model;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class NetworkInfo {
    public final @Nullable String ipAddress;
    public final @Nullable String macAddress;
    public final @Nullable String wifiSsid;
    public final int wifiRssi;

    public NetworkInfo(@Nullable String ip, @Nullable String mac, @Nullable String ssid, int rssi) {
        this.ipAddress = ip;
        this.macAddress = mac;
        this.wifiSsid = ssid;
        this.wifiRssi = rssi;
    }
}
