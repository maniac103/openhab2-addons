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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.openhab.binding.ecovacs.internal.api.impl.dto.response.deviceapi.ComponentLifeSpanReport;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.AbstractPortalIotCommandResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandJsonResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandXmlResponse;
import org.openhab.binding.ecovacs.internal.api.model.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

/**
 * @author Danny Baumann - Initial contribution
 */
public class GetComponentLifeSpanCommand extends IotDeviceCommand<Integer> {
    private final Component type;

    public GetComponentLifeSpanCommand(Component type) {
        super("GetLifeSpan", "getLifeSpan");
        this.type = type;
    }

    @Override
    protected void applyXmlPayload(Document doc, Element ctl) {
        ctl.setAttribute("type", type.xmlValue);
    }

    @Override
    protected Object getJsonPayloadArgs() {
        List<String> args = new ArrayList<>();
        args.add(type.jsonValue);
        return args;
    }

    @Override
    public Integer convertResponse(AbstractPortalIotCommandResponse response, Gson gson) throws Exception {
        if (response instanceof PortalIotCommandJsonResponse) {
            JsonElement respPayloadRaw = ((PortalIotCommandJsonResponse) response).getResponsePayload(gson);
            Type type = new TypeToken<List<ComponentLifeSpanReport>>() {
            }.getType();
            List<ComponentLifeSpanReport> resp = gson.fromJson(respPayloadRaw, type);
            if (resp == null || resp.isEmpty()) {
                throw new IllegalArgumentException("Invalid lifespan response " + respPayloadRaw);
            }
            return (int) Math.round(100.0 * resp.get(0).left / resp.get(0).total);
        } else {
            String payload = ((PortalIotCommandXmlResponse) response).getResponsePayloadXml();
            int value = nodeValueToInt(payload, "value");
            int total = nodeValueToInt(payload, "total");
            int left = nodeValueToInt(payload, "left");
            if (value >= 0 && total >= 0) {
                return (int) Math.round(100.0 * value / total);
            } else if (value >= 0) {
                return (int) Math.round(0.01 * value);
            } else if (left >= 0 && total >= 0) {
                return (int) Math.round(100.0 * left / total);
            } else if (left >= 0) {
                return (int) Math.round((double) left / 60.0);
            }
            return -1;
        }
    }

    private int nodeValueToInt(String payload, String attrName) throws Exception {
        Node attr = getFirstXPathMatch(payload, "//ctl/@" + attrName);
        return attr != null ? Integer.valueOf(attr.getNodeValue()) : -1;
    }
}
