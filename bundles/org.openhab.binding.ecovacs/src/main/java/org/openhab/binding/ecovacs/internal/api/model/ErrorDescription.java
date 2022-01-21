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
package org.openhab.binding.ecovacs.internal.api.model;

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Danny Baumann - Initial contribution
 */
public class ErrorDescription {
    public final int errorCode;
    public final String description;

    public ErrorDescription(int errorCode) {
        this.errorCode = errorCode;
        this.description = ERROR_DESCS.get(errorCode);
    }

    private static final Map<Integer, String> ERROR_DESCS = Stream
            .of(new AbstractMap.SimpleImmutableEntry<>(0, "No error"),
                    new AbstractMap.SimpleImmutableEntry<>(3, "Authentication error"),
                    new AbstractMap.SimpleImmutableEntry<>(7, "Log data was not found"),
                    new AbstractMap.SimpleImmutableEntry<>(100, "No error"),
                    new AbstractMap.SimpleImmutableEntry<>(101, "Low battery"),
                    new AbstractMap.SimpleImmutableEntry<>(102, "Robot is off the floor"),
                    new AbstractMap.SimpleImmutableEntry<>(103, "Driving wheel malfunction"),
                    new AbstractMap.SimpleImmutableEntry<>(104, "Excess dust on the anti-drop sensors"),
                    new AbstractMap.SimpleImmutableEntry<>(105, "Robot is stuck"),
                    new AbstractMap.SimpleImmutableEntry<>(106, "Side brushes have expired"),
                    new AbstractMap.SimpleImmutableEntry<>(107, "Dust case filter expired"),
                    new AbstractMap.SimpleImmutableEntry<>(108, "Side Brushes are tangled"),
                    new AbstractMap.SimpleImmutableEntry<>(109, "Main Brush is tangled"),
                    new AbstractMap.SimpleImmutableEntry<>(110, "Dust bin not installed"),
                    new AbstractMap.SimpleImmutableEntry<>(111, "Bump sensor stuck"),
                    new AbstractMap.SimpleImmutableEntry<>(112, "Laser distance sensor malfunction"),
                    new AbstractMap.SimpleImmutableEntry<>(113, "Main brush has expired"),
                    new AbstractMap.SimpleImmutableEntry<>(114, "Dust bin full"),
                    new AbstractMap.SimpleImmutableEntry<>(115, "Battery error"),
                    new AbstractMap.SimpleImmutableEntry<>(116, "Forward looking error"),
                    new AbstractMap.SimpleImmutableEntry<>(117, "Gyroscope error"),
                    new AbstractMap.SimpleImmutableEntry<>(118, "Strainer blocked"),
                    new AbstractMap.SimpleImmutableEntry<>(119, "Fan error"),
                    new AbstractMap.SimpleImmutableEntry<>(120, "Water box error"),
                    new AbstractMap.SimpleImmutableEntry<>(201, "Air filter removed"),
                    new AbstractMap.SimpleImmutableEntry<>(202, "Ultrasonic component error"),
                    new AbstractMap.SimpleImmutableEntry<>(203, "Small wheel error"),
                    new AbstractMap.SimpleImmutableEntry<>(204, "Wheel is blocked"),
                    new AbstractMap.SimpleImmutableEntry<>(205, "Ion sterilization exhausted"),
                    new AbstractMap.SimpleImmutableEntry<>(206, "Ion sterilization error"),
                    new AbstractMap.SimpleImmutableEntry<>(207, "Ion sterilization fault"),
                    new AbstractMap.SimpleImmutableEntry<>(404, "Recipient unavailable"),
                    new AbstractMap.SimpleImmutableEntry<>(500, "Request timeout"),
                    new AbstractMap.SimpleImmutableEntry<>(601, "AIVI side error"),
                    new AbstractMap.SimpleImmutableEntry<>(602, "AIVI roll error"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
}
