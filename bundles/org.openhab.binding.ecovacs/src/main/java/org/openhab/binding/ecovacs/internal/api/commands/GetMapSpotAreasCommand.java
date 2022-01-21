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

import java.util.List;

/**
 * @author Danny Baumann - Initial contribution
 */
public class GetMapSpotAreasCommand implements MultiCommand<List<String>> {
    private boolean requestingMapInfo;
    private List<String> spotAreas;

    @Override
    public IotDeviceCommand<?> getFirstCommand(boolean useXml) {
        requestingMapInfo = true;
        return new GetActiveMapIdCommand();
    }

    @Override
    public IotDeviceCommand<?> processResultAndGetNextCommand(Object lastCommandResult) {
        if (requestingMapInfo) {
            requestingMapInfo = false;
            return new GetMapSpotAreasWithMapIdCommand((String) lastCommandResult);
        } else {
            spotAreas = (List<String>) lastCommandResult;
            return null;
        }
    }

    @Override
    public List<String> getResult() {
        return spotAreas;
    }
}
