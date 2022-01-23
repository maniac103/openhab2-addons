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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.ecovacs.internal.api.model.CleanMode;
import org.openhab.binding.ecovacs.internal.api.model.MoppingWaterAmount;
import org.openhab.binding.ecovacs.internal.api.model.SuctionPower;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link EcovacsBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class EcovacsBindingConstants {
    private static final String BINDING_ID = "ecovacs";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_API = new ThingTypeUID(BINDING_ID, "ecovacsapi");
    public static final ThingTypeUID THING_TYPE_VACUUM = new ThingTypeUID(BINDING_ID, "vacuum");

    // List of all channel UIDs
    public static final String CHANNEL_ID_BATTERY_LEVEL = "status#battery";
    public static final String CHANNEL_ID_CLEANING_TIME = "status#current-cleaning-time";
    public static final String CHANNEL_ID_CLEANED_AREA = "status#current-cleaned-area";
    public static final String CHANNEL_ID_COMMAND = "actions#command";
    public static final String CHANNEL_ID_DUST_FILTER_LIFETIME = "consumables#dust-filter-lifetime";
    public static final String CHANNEL_ID_ERROR_CODE = "status#error-code";
    public static final String CHANNEL_ID_ERROR_DESCRIPTION = "status#error-description";
    public static final String CHANNEL_ID_LAST_CLEAN_START = "last-clean#last-clean-start";
    public static final String CHANNEL_ID_LAST_CLEAN_DURATION = "last-clean#last-clean-duration";
    public static final String CHANNEL_ID_LAST_CLEAN_AREA = "last-clean#last-clean-area";
    public static final String CHANNEL_ID_LAST_CLEAN_MODE = "last-clean#last-clean-mode";
    public static final String CHANNEL_ID_LAST_CLEAN_MAP = "last-clean#last-clean-map";
    public static final String CHANNEL_ID_MAIN_BRUSH_LIFETIME = "consumables#main-brush-lifetime";
    public static final String CHANNEL_ID_SIDE_BRUSH_LIFETIME = "consumables#side-brush-lifetime";
    public static final String CHANNEL_ID_STATE = "status#state";
    public static final String CHANNEL_ID_SUCTION_POWER = "settings#suction-power";
    public static final String CHANNEL_ID_TOTAL_CLEANING_TIME = "total-stats#total-cleaning-time";
    public static final String CHANNEL_ID_TOTAL_CLEANED_AREA = "total-stats#total-cleaned-area";
    public static final String CHANNEL_ID_TOTAL_CLEAN_RUNS = "total-stats#total-clean-runs";
    public static final String CHANNEL_ID_VOICE_VOLUME = "settings#voice-volume";
    public static final String CHANNEL_ID_WATER_PLATE_PRESENT = "status#water-system-present";
    public static final String CHANNEL_ID_WATER_AMOUNT = "settings#water-amount";
    public static final String CHANNEL_ID_WIFI_RSSI = "status#wifi-rssi";

    public static final String CMD_AUTO_CLEAN = "clean";
    public static final String CMD_PAUSE = "pause";
    public static final String CMD_RESUME = "resume";
    public static final String CMD_CHARGE = "charge";
    public static final String CMD_STOP = "stop";

    public static final Map<CleanMode, String> CLEAN_MODE_MAPPING = new HashMap<>() {
        {
            put(CleanMode.AUTO, "auto");
            put(CleanMode.EDGE, "edge");
            put(CleanMode.SPOT, "spot");
            put(CleanMode.SPOT_AREA, "spotArea");
            put(CleanMode.CUSTOM_AREA, "customArea");
            put(CleanMode.SINGLE_ROOM, "singleRoom");
            put(CleanMode.PAUSE, "pause");
            put(CleanMode.STOP, "stop");
            put(CleanMode.RETURNING, "returning");
        }
    };

    public static final Map<MoppingWaterAmount, String> WATER_AMOUNT_MAPPING = new HashMap<>() {
        {
            put(MoppingWaterAmount.LOW, "low");
            put(MoppingWaterAmount.MEDIUM, "medium");
            put(MoppingWaterAmount.HIGH, "high");
            put(MoppingWaterAmount.VERY_HIGH, "veryhigh");
        }
    };

    public static final Map<SuctionPower, String> SUCTION_POWER_MAPPING = new HashMap<>() {
        {
            put(SuctionPower.SILENT, "silent");
            put(SuctionPower.NORMAL, "normal");
            put(SuctionPower.HIGH, "high");
            put(SuctionPower.HIGHER, "higher");
        }
    };
}
