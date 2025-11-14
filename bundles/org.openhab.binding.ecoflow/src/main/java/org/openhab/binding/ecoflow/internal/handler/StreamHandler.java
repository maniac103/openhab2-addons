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

package org.openhab.binding.ecoflow.internal.handler;

import static org.openhab.binding.ecoflow.internal.EcoflowBindingConstants.StreamChannels.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Command;

import com.google.gson.JsonObject;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class StreamHandler extends AbstractEcoflowHandler {
    private static final ValueConverter PERCENT_DECIMAL_CONVERTER = value -> new DecimalType(value.getAsNumber());
    private static final ValueConverter PERCENT_DIMMER_CONVERTER = value -> new PercentType(value.getAsInt());
    private static final ValueConverter SWITCH_CONVERTER = value -> value.getAsBoolean() ? OnOffType.ON : OnOffType.OFF;

    private static final ValueConverter FEED_GRID_MODE_CONVERTER = value -> value.getAsInt() == 2 ? OnOffType.ON
            : OnOffType.OFF;
    private static final ValueConverter OPERATING_MODE_CONVERTER = value -> new StringType(
            value.getAsBoolean() ? "ai-schedule" : "self-powered");

    private static final List<ChannelMapping> STANDARD_MAPPINGS = List.of(
            new ChannelMapping("", "powGetSysLoad", CHANNEL_ID_LOAD_POWER, 1, Units.WATT),
            new ChannelMapping("", "energyStrategyOperateMode.operateIntelligentScheduleModeOpen",
                    CHANNEL_ID_OPERATING_MODE, OPERATING_MODE_CONVERTER),
            new ChannelMapping("", "feedGridMode", CHANNEL_ID_GRID_FEED_ENABLED, FEED_GRID_MODE_CONVERTER),
            new ChannelMapping("", "gridConnectionPower", CHANNEL_ID_GRID_POWER, 1, Units.WATT),
            new ChannelMapping("", "powGetPvSum", CHANNEL_ID_PV_IN_POWER, 1, Units.WATT),
            new ChannelMapping("", "cmsBattSoc", CHANNEL_ID_BATTERY_SOC, PERCENT_DECIMAL_CONVERTER),
            new ChannelMapping("", "cmsMaxChgSoc", CHANNEL_ID_BATTERY_CHARGE_LIMIT, PERCENT_DIMMER_CONVERTER),
            new ChannelMapping("", "cmsMinDsgSoc", CHANNEL_ID_BATTERY_DISCHARGE_LIMIT, PERCENT_DIMMER_CONVERTER),
            new ChannelMapping("", "backupReverseSoc", CHANNEL_ID_BATTERY_BACKUP_RESERVE, PERCENT_DIMMER_CONVERTER),
            new ChannelMapping("", "powGetBpCms", CHANNEL_ID_BATTERY_CHARGE_POWER, 1, Units.WATT));

    private static final ChannelMapping AC_OUTPUT_MAPPING = new ChannelMapping("", "relay2Onoff", CHANNEL_ID_AC_OUTPUT1,
            SWITCH_CONVERTER);
    private static final ChannelMapping AC_OUTPUT2_MAPPING = new ChannelMapping("", "relay3Onoff",
            CHANNEL_ID_AC_OUTPUT2, SWITCH_CONVERTER);

    public StreamHandler(Thing thing, int acOutputCount) {
        super(thing, buildMappings(acOutputCount));
    }

    @Override
    protected Optional<String> extractGroupKeyFromMqttMessage(JsonObject payload) {
        return Optional.of("");
    }

    @Override
    protected JsonObject extractParamsFromQuotaMessage(JsonObject payload) {
        return payload;
    }

    @Override
    protected Optional<JsonObject> convertCommand(String channelId, Command command) {
        if (CHANNEL_ID_AC_OUTPUT1.equals(channelId) && command instanceof OnOffType value) {
            JsonObject params = new JsonObject();
            params.addProperty("cfgRelay2Onoff", value == OnOffType.ON);
            return Optional.of(createControlRequest(params));
        } else if (CHANNEL_ID_AC_OUTPUT2.equals(channelId) && command instanceof OnOffType value) {
            JsonObject params = new JsonObject();
            params.addProperty("cfgRelay3Onoff", value == OnOffType.ON);
            return Optional.of(createControlRequest(params));
        } else if (CHANNEL_ID_BATTERY_BACKUP_RESERVE.equals(channelId) && command instanceof DecimalType value) {
            JsonObject params = new JsonObject();
            params.addProperty("cfgBackupReverseSoc", value.intValue());
            return Optional.of(createControlRequest(params));
        } else if (CHANNEL_ID_GRID_FEED_ENABLED.equals(channelId) && command instanceof OnOffType value) {
            JsonObject params = new JsonObject();
            params.addProperty("cfgFeedGridMode", value == OnOffType.ON ? 2 : 1);
            return Optional.of(createControlRequest(params));
        } else if (CHANNEL_ID_OPERATING_MODE.equals(channelId) && command instanceof StringType value) {
            String modeKey = "ai-schedule".equals(value.toString()) ? "operateIntelligentScheduleModeOpen"
                    : "operateSelfPoweredOpen";
            JsonObject strategyNode = new JsonObject();
            strategyNode.addProperty(modeKey, true);
            JsonObject params = new JsonObject();
            params.add("cfgEnergyStrategyOperateMode", strategyNode);
            return Optional.of(createControlRequest(params));
        }
        return Optional.empty();
    }

    private JsonObject createControlRequest(JsonObject params) {
        JsonObject result = new JsonObject();
        result.addProperty("cmdId", 17);
        result.addProperty("cmdFunc", 254);
        result.addProperty("dirDest", 1);
        result.addProperty("dirSrc", 1);
        result.addProperty("dest", 2);
        result.addProperty("needAck", true);
        result.add("params", params);
        return result;
    }

    private static List<ChannelMapping> buildMappings(int acOutputCount) {
        ArrayList<ChannelMapping> result = new ArrayList<>(STANDARD_MAPPINGS);
        if (acOutputCount > 0) {
            result.add(AC_OUTPUT_MAPPING);
        }
        if (acOutputCount > 1) {
            result.add(AC_OUTPUT2_MAPPING);
        }
        return result;
    }
}
