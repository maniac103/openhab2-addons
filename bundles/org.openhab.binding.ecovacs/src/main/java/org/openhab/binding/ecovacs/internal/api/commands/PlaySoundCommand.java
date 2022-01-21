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
package org.openhab.binding.ecovacs.internal.api.commands;

import java.util.HashMap;
import java.util.Map;

import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.AbstractPortalIotCommandResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.gson.Gson;

/**
 * @author Danny Baumann - Initial contribution
 */
public class PlaySoundCommand extends IotDeviceCommand<Void> {
    public enum SoundType {
        STARTUP(0),
        SUSPENDED(3),
        CHECK_WHEELS(4),
        HELP_ME_OUT(5),
        INSTALL_DUST_BIN(6),
        BEEP(17),
        BATTERY_LOW(18),
        POWER_ON_BEFORE_CHARGE(29),
        I_AM_HERE(30),
        PLEASE_CLEAN_BRUSH(31),
        PLEASE_CLEAN_SENSORS(35),
        BRUSH_IS_TANGLED(48),
        RELOCATING(55),
        UPGRADE_DONE(56),
        RETURNING_TO_CHARGE(63),
        CLEANING_PAUSED(65),
        CONNECTED_IN_SETUP(69),
        RESTORING_MAP(71),
        BATTERY_LOW_RETURNING_TO_DOCK(73),
        DIFFICULT_TO_LOCATE(74),
        RESUMING_CLEANING(75),
        UPGRADE_FAILED(76),
        PLACE_ON_CHARGING_DOCK(77),
        RESUME_CLEANING(79),
        STARTING_CLEANING(80),
        READY_FOR_MOPPING(84),
        REMOVE_MOPPING_PLATE(85),
        CLEANING_COMPLETE(86),
        LDS_MALFUNCTION(89),
        UPGRADING(90);

        final int id;

        private SoundType(int id) {
            this.id = id;
        }
    }

    private final int soundId;

    public PlaySoundCommand(SoundType type) {
        super("PlaySound", "playSound");
        this.soundId = type.id;
    }

    @Override
    protected void applyXmlPayload(Document doc, Element ctl) {
        ctl.setAttribute("sid", String.valueOf(soundId));
    }

    @Override
    protected Object getJsonPayloadArgs() {
        Map<String, String> args = new HashMap<>();
        args.put("sid", String.valueOf(soundId));
        return args;
    }

    @Override
    public Void convertResponse(AbstractPortalIotCommandResponse response, Gson gson) throws Exception {
        return null;
    }
}
