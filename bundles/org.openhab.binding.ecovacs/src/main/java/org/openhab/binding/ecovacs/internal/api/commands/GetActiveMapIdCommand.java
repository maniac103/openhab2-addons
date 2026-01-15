/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.ecovacs.internal.api.impl.ProtocolVersion;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.deviceapi.json.CachedMapInfoReport;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.AbstractPortalIotCommandResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandJsonResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandXmlResponse;
import org.openhab.binding.ecovacs.internal.api.util.DataParsingException;
import org.openhab.binding.ecovacs.internal.api.util.XPathUtils;

import com.google.gson.Gson;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class GetActiveMapIdCommand extends IotDeviceCommand<String> {
    public GetActiveMapIdCommand() {
    }

    @Override
    public String getName(ProtocolVersion version) {
        return version == ProtocolVersion.XML ? "GetMapM" : "getCachedMapInfo";
    }

    @Override
    public String convertResponse(AbstractPortalIotCommandResponse response, ProtocolVersion version, Gson gson)
            throws DataParsingException {
        if (response instanceof PortalIotCommandJsonResponse jsonResponse) {
            CachedMapInfoReport resp = jsonResponse.getResponsePayloadAs(gson, CachedMapInfoReport.class);
            if (resp.mapInfos == null) {
                throw new DataParsingException("Map infos missing in response " + response);
            }
            Optional<String> mapIdOpt = resp.mapInfos.stream() //
                    .filter(i -> i != null && i.used != 0) //
                    .findFirst() //
                    .flatMap(i -> Optional.ofNullable(i.mapId)); // map ID might be null as well

            return mapIdOpt.orElseThrow(() -> new DataParsingException("No active map ID in response " + response));
        } else {
            String payload = ((PortalIotCommandXmlResponse) response).getResponsePayloadXml();
            @Nullable
            String id = XPathUtils.getFirstXPathMatch(payload, "//@i").getNodeValue();
            if (id == null) {
                throw new DataParsingException("No ID in response " + payload);
            }
            return id;
        }
    }
}
