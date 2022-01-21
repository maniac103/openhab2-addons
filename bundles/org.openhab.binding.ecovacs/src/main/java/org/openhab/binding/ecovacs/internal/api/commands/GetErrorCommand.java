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

import org.openhab.binding.ecovacs.internal.api.impl.dto.response.deviceapi.ErrorReport;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.AbstractPortalIotCommandResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandJsonResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandXmlResponse;
import org.openhab.binding.ecovacs.internal.api.model.ErrorDescription;

import com.google.gson.Gson;

/**
 * @author Danny Baumann - Initial contribution
 */
public class GetErrorCommand extends IotDeviceCommand<ErrorDescription> {
    public GetErrorCommand() {
        super("GetError", "getError");
    }

    @Override
    public ErrorDescription convertResponse(AbstractPortalIotCommandResponse response, Gson gson) throws Exception {
        if (response instanceof PortalIotCommandJsonResponse) {
            ErrorReport resp = ((PortalIotCommandJsonResponse) response).getResponsePayloadAs(gson, ErrorReport.class);
            if (resp.errorCodes.isEmpty()) {
                return null;
            }
            return new ErrorDescription(resp.errorCodes.get(0));
        } else {
            String payload = ((PortalIotCommandXmlResponse) response).getResponsePayloadXml();
            int errorCode = Integer.valueOf(getFirstXPathMatch(payload, "//@errs").getNodeValue());
            return new ErrorDescription(errorCode);
        }
    }
}
