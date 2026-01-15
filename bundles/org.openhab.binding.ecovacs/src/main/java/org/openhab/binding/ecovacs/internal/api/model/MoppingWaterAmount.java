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
package org.openhab.binding.ecovacs.internal.api.model;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.ecovacs.internal.api.util.DataParsingException;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public enum MoppingWaterAmount {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH;

    public static MoppingWaterAmount fromApiValue(@Nullable Integer value) throws DataParsingException {
        if (value == null || value >= MoppingWaterAmount.values().length) {
            throw new DataParsingException("Unexpected water amount enum value " + value);
        }

        return MoppingWaterAmount.values()[value - 1];
    }

    public int toApiValue() {
        return ordinal() + 1;
    }
}
