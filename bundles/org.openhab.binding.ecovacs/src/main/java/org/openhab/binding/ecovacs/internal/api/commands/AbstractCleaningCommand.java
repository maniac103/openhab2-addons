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

import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.ecovacs.internal.api.impl.ProtocolVersion;
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
    private final Optional<String> mode;

    protected AbstractCleaningCommand(String xmlAction, String jsonAction, @Nullable String mode) {
        super();
        this.xmlAction = xmlAction;
        this.jsonAction = jsonAction;
        this.mode = Optional.ofNullable(mode);
    }

    @Override
    public String getName(ProtocolVersion version) {
        return version == ProtocolVersion.XML ? "Clean" : "clean";
    }

    @Override
    protected void applyXmlPayload(Document doc, Element ctl) {
        Element clean = doc.createElement("clean");
        mode.ifPresent(m -> clean.setAttribute("type", m));
        clean.setAttribute("speed", "standard");
        clean.setAttribute("act", xmlAction);
        ctl.appendChild(clean);
    }

    @Override
    protected @Nullable JsonElement getJsonPayloadArgs(ProtocolVersion version) {
        JsonObject args = new JsonObject();
        args.addProperty("act", jsonAction);
        mode.ifPresent(m -> args.addProperty("type", m));
        return args;
    }
}
