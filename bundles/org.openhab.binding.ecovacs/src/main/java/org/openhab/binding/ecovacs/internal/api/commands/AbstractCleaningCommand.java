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
public abstract class AbstractCleaningCommand extends IotDeviceCommand<Void> {
    private final String xmlAction;
    private final String jsonAction;
    private final String mode;

    protected AbstractCleaningCommand(String xmlAction, String jsonAction, String mode) {
        super("Clean", "clean");
        this.xmlAction = xmlAction;
        this.jsonAction = jsonAction;
        this.mode = mode;
    }

    @Override
    protected void applyXmlPayload(Document doc, Element ctl) {
        Element clean = doc.createElement("clean");
        if (mode != null) {
            clean.setAttribute("type", mode);
        }
        clean.setAttribute("speed", "standard");
        clean.setAttribute("act", xmlAction);
        ctl.appendChild(clean);
    }

    @Override
    protected Object getJsonPayloadArgs() {
        Map<String, String> args = new HashMap<>();
        args.put("act", jsonAction);
        if (mode != null) {
            args.put("type", mode);
        }
        return args;
    }

    @Override
    public Void convertResponse(AbstractPortalIotCommandResponse response, Gson gson) throws Exception {
        return null;
    }
}
