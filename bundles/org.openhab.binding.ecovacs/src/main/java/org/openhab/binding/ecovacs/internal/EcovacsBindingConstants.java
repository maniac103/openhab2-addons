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
package org.openhab.binding.ecovacs.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link EcovacsBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class EcovacsBindingConstants {
    private static final String BINDING_ID = "ecovacs";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_API = new ThingTypeUID(BINDING_ID, "ecovacsapi");
    public static final ThingTypeUID THING_TYPE_VACUUM = new ThingTypeUID(BINDING_ID, "vacuum");

    // List of all channel UIDs
    public static final String CHANNEL_ID_STATE = "state";
    public static final String CHANNEL_ID_BATTERY_LEVEL = "battery";
}
