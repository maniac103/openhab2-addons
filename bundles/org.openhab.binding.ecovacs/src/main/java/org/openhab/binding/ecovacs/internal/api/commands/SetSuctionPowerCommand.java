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
import org.openhab.binding.ecovacs.internal.api.model.SuctionPower;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.gson.Gson;

/**
 * @author Danny Baumann - Initial contribution
 */
public class SetSuctionPowerCommand extends IotDeviceCommand<Void> {
    private final SuctionPower power;

    public SetSuctionPowerCommand(SuctionPower power) {
        super("SetCleanSpeed", "setSpeed");
        this.power = power;
    }

    protected Object getJsonPayloadArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put("speed", power.toJsonValue());
        return args;
    }

    protected void applyXmlPayload(Document doc, Element ctl) {
        ctl.setAttribute("speed", power.toXmlValue());
    }

    public Void convertResponse(AbstractPortalIotCommandResponse response, Gson gson) throws Exception {
        return null;
    }
}
