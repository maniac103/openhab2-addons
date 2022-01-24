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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.deviceapi.MapSetReport;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.AbstractPortalIotCommandResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandJsonResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandXmlResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.google.gson.Gson;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class GetMapSpotAreasWithMapIdCommand extends IotDeviceCommand<List<String>> {
    private final String mapId;

    public GetMapSpotAreasWithMapIdCommand(String mapId) {
        super("GetMapSet", "getMapSet");
        this.mapId = mapId;
    }

    @Override
    protected void applyXmlPayload(Document doc, Element ctl) {
        ctl.setAttribute("tp", "sa");
    }

    @Override
    protected @Nullable Object getJsonPayloadArgs() {
        Map<String, String> args = new HashMap<>();
        args.put("mid", mapId);
        args.put("type", "ar");
        return args;
    }

    @Override
    public List<String> convertResponse(AbstractPortalIotCommandResponse response, Gson gson) throws Exception {
        if (response instanceof PortalIotCommandJsonResponse) {
            MapSetReport resp = ((PortalIotCommandJsonResponse) response).getResponsePayloadAs(gson,
                    MapSetReport.class);
            return resp.subsets.stream().map(i -> i.id).collect(Collectors.toList());
        } else {
            String payload = ((PortalIotCommandXmlResponse) response).getResponsePayloadXml();
            NodeList mapIds = getXPathMatches(payload, "//m/@mid");
            List<String> result = new ArrayList<>();
            for (int i = 0; i < mapIds.getLength(); i++) {
                result.add(mapIds.item(i).getNodeValue());
            }
            return result;
        }
    }
}
