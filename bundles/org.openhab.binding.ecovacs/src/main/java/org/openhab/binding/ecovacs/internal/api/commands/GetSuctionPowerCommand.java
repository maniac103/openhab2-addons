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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.deviceapi.SpeedReport;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.AbstractPortalIotCommandResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandJsonResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandXmlResponse;
import org.openhab.binding.ecovacs.internal.api.model.SuctionPower;

import com.google.gson.Gson;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class GetSuctionPowerCommand extends IotDeviceCommand<SuctionPower> {
    public GetSuctionPowerCommand() {
        super("GetCleanSpeed", "getSpeed");
    }

    @Override
    public SuctionPower convertResponse(AbstractPortalIotCommandResponse response, Gson gson) throws Exception {
        if (response instanceof PortalIotCommandJsonResponse) {
            SpeedReport resp = ((PortalIotCommandJsonResponse) response).getResponsePayloadAs(gson, SpeedReport.class);
            return SuctionPower.fromJsonValue(resp.speedLevel);
        } else {
            String payload = ((PortalIotCommandXmlResponse) response).getResponsePayloadXml();
            String levelString = getFirstXPathMatch(payload, "//@speed").getNodeValue(); // TODO: verify this
            SuctionPower level = gson.fromJson(levelString, SuctionPower.class);
            if (level == null) {
                throw new IllegalArgumentException("Could not parse power level " + levelString);
            }
            return level;
        }
    }
}
