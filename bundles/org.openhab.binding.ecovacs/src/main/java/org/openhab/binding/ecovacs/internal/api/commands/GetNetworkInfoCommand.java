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

import org.openhab.binding.ecovacs.internal.api.impl.dto.response.deviceapi.NetworkInfoReport;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.AbstractPortalIotCommandResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandJsonResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandXmlResponse;
import org.openhab.binding.ecovacs.internal.api.model.NetworkInfo;
import org.w3c.dom.Node;

import com.google.gson.Gson;

/**
 * @author Danny Baumann - Initial contribution
 */
public class GetNetworkInfoCommand extends IotDeviceCommand<NetworkInfo> {
    public GetNetworkInfoCommand() {
        super("GetNetInfo", "getNetInfo");
    }

    @Override
    public NetworkInfo convertResponse(AbstractPortalIotCommandResponse response, Gson gson) throws Exception {
        if (response instanceof PortalIotCommandJsonResponse) {
            NetworkInfoReport resp = ((PortalIotCommandJsonResponse) response).getResponsePayloadAs(gson,
                    NetworkInfoReport.class);
            return new NetworkInfo(resp.ip, resp.mac, resp.ssid, Integer.valueOf(resp.rssi));
        } else {
            String payload = ((PortalIotCommandXmlResponse) response).getResponsePayloadXml();
            Node ipAttr = getFirstXPathMatch(payload, "//@wi"); // TODO: verify this
            Node ssidAttr = getFirstXPathMatch(payload, "//@s");
            return new NetworkInfo(ipAttr.getNodeValue(), "", ssidAttr.getNodeValue(), 0);
        }
    }
}
