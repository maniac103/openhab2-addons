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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class SpotAreaCleaningCommand extends AbstractNoResponseCommand {
    private final String content;
    private final int cleanPasses;

    public SpotAreaCleaningCommand(String roomIds, int cleanPasses) {
        super("Clean", "clean");
        this.content = roomIds;
        this.cleanPasses = cleanPasses;
    }

    @Override
    protected void applyXmlPayload(Document doc, Element ctl) {
        Element clean = doc.createElement("clean");
        clean.setAttribute("act", "s");
        clean.setAttribute("type", "SpotArea");
        clean.setAttribute("speed", "standard");
        clean.setAttribute("mid", content);
        clean.setAttribute("deep", String.valueOf(cleanPasses));
        ctl.appendChild(clean);
    }

    @Override
    protected @Nullable Object getJsonPayloadArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put("act", "start");
        args.put("content", content);
        args.put("count", cleanPasses);
        args.put("type", "spotArea");
        return args;
    }
}
