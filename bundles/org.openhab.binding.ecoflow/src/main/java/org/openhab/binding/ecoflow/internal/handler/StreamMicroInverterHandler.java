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

import static org.openhab.binding.ecoflow.internal.EcoflowBindingConstants.StreamMicroInverterChannels.*;

import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Command;

import com.google.gson.JsonObject;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class StreamMicroInverterHandler extends AbstractEcoflowHandler {
    private static final List<ChannelMapping> MAPPINGS = List.of(
            new ChannelMapping("", "gridConnectionPower", CHANNEL_ID_GRID_POWER, 1, Units.WATT),
            new ChannelMapping("", "gridConnectionVol", CHANNEL_ID_GRID_VOLTAGE, 0.001, Units.VOLT),
            new ChannelMapping("", "gridConnectionAmp", CHANNEL_ID_GRID_CURRENT, 0.001, Units.AMPERE),
            new ChannelMapping("", "gridConnectionFreq", CHANNEL_ID_GRID_FREQUENCY, 1, Units.HERTZ),

            new ChannelMapping("", "plugInInfoPvVol", CHANNEL_ID_PV_IN_VOLTAGE, 0.001, Units.VOLT),
            new ChannelMapping("", "plugInInfoPvAmp", CHANNEL_ID_PV_IN_CURRENT, 0.001, Units.AMPERE),
            new ChannelMapping("", "powGetPv", CHANNEL_ID_PV_IN_POWER, 1, Units.WATT),

            new ChannelMapping("", "plugInInfoPv2Vol", CHANNEL_ID_PV_IN2_VOLTAGE, 0.001, Units.VOLT),
            new ChannelMapping("", "plugInInfoPv2Amp", CHANNEL_ID_PV_IN2_CURRENT, 0.001, Units.AMPERE),
            new ChannelMapping("", "powGetPv2", CHANNEL_ID_PV_IN2_POWER, 1, Units.WATT),

            new ChannelMapping("", "invNtcTemp3", CHANNEL_ID_TEMPERATURE, 1, SIUnits.CELSIUS));

    public StreamMicroInverterHandler(Thing thing) {
        super(thing, MAPPINGS);
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
        return Optional.empty();
    }
}
