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

import org.openhab.binding.ecovacs.internal.api.impl.dto.response.deviceapi.CleanReport;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.AbstractPortalIotCommandResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandJsonResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandXmlResponse;
import org.openhab.binding.ecovacs.internal.api.model.CleanMode;

import com.google.gson.Gson;

/**
 * @author Danny Baumann - Initial contribution
 */
public class GetCleanStateCommand extends IotDeviceCommand<CleanMode> {
    public GetCleanStateCommand() {
        super("GetCleanState", "getCleanInfo");
    }

    @Override
    public CleanMode convertResponse(AbstractPortalIotCommandResponse response, Gson gson) throws Exception {
        if (response instanceof PortalIotCommandJsonResponse) {
            CleanReport resp = ((PortalIotCommandJsonResponse) response).getResponsePayloadAs(gson, CleanReport.class);
            return resp.determineCleanMode(gson);
        } else {
            String payload = ((PortalIotCommandXmlResponse) response).getResponsePayloadXml();
            String mode = getFirstXPathMatch(payload, "//clean/@type").getNodeValue();
            return gson.fromJson(mode, CleanMode.class);
        }
    }
}
