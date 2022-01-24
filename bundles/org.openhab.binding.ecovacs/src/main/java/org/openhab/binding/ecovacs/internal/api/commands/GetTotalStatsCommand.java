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
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.AbstractPortalIotCommandResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandJsonResponse;
import org.openhab.binding.ecovacs.internal.api.impl.dto.response.portal.PortalIotCommandXmlResponse;
import org.openhab.binding.ecovacs.internal.api.util.XPathParser;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class GetTotalStatsCommand extends IotDeviceCommand<GetTotalStatsCommand.TotalStats> {
    public class TotalStats {
        @SerializedName("area")
        public final int totalArea;
        @SerializedName("time")
        public final int totalRuntime;
        @SerializedName("count")
        public final int cleanRuns;

        private TotalStats(int area, int runtime, int runs) {
            this.totalArea = area;
            this.totalRuntime = runtime;
            this.cleanRuns = runs;
        }
    }

    public GetTotalStatsCommand() {
        super("GetCleanSum", "getTotalStats");
    }

    public TotalStats convertResponse(AbstractPortalIotCommandResponse response, Gson gson) throws Exception {
        if (response instanceof PortalIotCommandJsonResponse) {
            return ((PortalIotCommandJsonResponse) response).getResponsePayloadAs(gson, TotalStats.class);
        } else {
            String payload = ((PortalIotCommandXmlResponse) response).getResponsePayloadXml();
            XPathParser parser = new XPathParser(payload);
            String area = parser.getFirstXPathMatch("//@a").getNodeValue();
            String time = parser.getFirstXPathMatch("//@l").getNodeValue();
            String count = parser.getFirstXPathMatch("//@c").getNodeValue();
            return new TotalStats(Integer.valueOf(area), Integer.valueOf(time), Integer.valueOf(count));
        }
    }
}
