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
import org.openhab.binding.ecovacs.internal.api.model.CleanMode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
abstract class AbstractCleaningCommand extends AbstractNoResponseCommand {
    private final String xmlAction;
    private final String jsonAction;
    private final @Nullable CleanMode mode;

    protected AbstractCleaningCommand(String xmlAction, String jsonAction, @Nullable CleanMode mode) {
        this.xmlAction = xmlAction;
        this.jsonAction = jsonAction;
        this.mode = mode;
    }

    @Override
    public String getName(ProtocolVersion version) {
        switch (version) {
            case XML:
                return "Clean";
            case JSON:
                return "clean";
            case JSON_V2:
                return "clean_V2";
        }
        throw new AssertionError();
    }

    @Override
    protected void applyXmlPayload(Document doc, Element ctl) {
        Element clean = doc.createElement("clean");
        getCleanModeProperty(ProtocolVersion.XML).ifPresent(m -> clean.setAttribute("type", m));
        clean.setAttribute("speed", "standard");
        clean.setAttribute("act", xmlAction);
        ctl.appendChild(clean);
    }

    @Override
    protected @Nullable JsonElement getJsonPayloadArgs(ProtocolVersion version) {
        JsonObject args = new JsonObject();
        args.addProperty("act", jsonAction);
        getCleanModeProperty(version).ifPresent(m -> {
            JsonObject payload = args;
            if (version == ProtocolVersion.JSON_V2) {
                JsonObject content = new JsonObject();
                args.add("content", content);
                payload = content;
            }
            payload.addProperty("type", m);
        });
        return args;
    }

    private Optional<String> getCleanModeProperty(ProtocolVersion version) {
        final @Nullable String modeValue = switch (mode) {
            case null -> null;
            case AUTO -> "auto";
            case CUSTOM_AREA -> version == ProtocolVersion.XML ? "CustomArea" : "customArea";
            case EDGE -> "border";
            case SPOT -> "spot";
            case SPOT_AREA -> version == ProtocolVersion.XML ? "SpotArea" : "spotArea";
            case SINGLE_ROOM -> "singleRoom";
            case STOP -> "stop";
            default -> null;
        };
        return Optional.ofNullable(modeValue);
    }
}
