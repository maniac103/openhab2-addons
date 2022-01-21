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
public class SetVolumeCommand extends IotDeviceCommand<Void> {
    private final int volume;

    public SetVolumeCommand(int volume) {
        super("", "setVolume");
        if (volume < 0 || volume > 10) {
            throw new IllegalArgumentException("Volume must be between 0 and 10");
        }
        this.volume = volume;
    }

    protected Object getJsonPayloadArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put("volume", volume);
        return args;
    }

    protected void applyXmlPayload(Document doc, Element ctl) {
        throw new IllegalStateException("Command only supported for JSON API");
    }

    public Void convertResponse(AbstractPortalIotCommandResponse response, Gson gson) throws Exception {
        return null;
    }
}
