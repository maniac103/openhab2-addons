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
package org.openhab.binding.ecovacs.internal.api.impl.dto.response.deviceapi.xml;

import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.ecovacs.internal.api.model.ChargeMode;
import org.openhab.binding.ecovacs.internal.api.util.XPathParser;
import org.w3c.dom.Node;

import com.google.gson.Gson;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class DeviceInfo {
    private static final Set<String> ERROR_ATTR_NAMES = Set.of("code", "error", "errno", "errs");

    public static int parseBatteryInfo(String xml) throws Exception {
        Node batteryAttr = new XPathParser(xml).getFirstXPathMatch("//battery/@power");
        return Integer.valueOf(batteryAttr.getNodeValue());
    }

    public static ChargeMode parseChargeInfo(String xml, Gson gson) throws Exception {
        String modeString = new XPathParser(xml).getFirstXPathMatch("//charge/@type").getNodeValue();
        ChargeMode mode = gson.fromJson(modeString, ChargeMode.class);
        if (mode == null) {
            throw new IllegalArgumentException("Could not parse charge mode " + modeString);
        }
        return mode;
    }

    public static Optional<Integer> parseErrorInfo(String xml) throws Exception {
        XPathParser parser = new XPathParser(xml);
        for (String attr : ERROR_ATTR_NAMES) {
            Optional<Node> node = parser.getFirstXPathMatchOpt("//@" + attr);
            if (node.isPresent()) {
                return node.map(n -> Integer.valueOf(n.getNodeValue()));
            }
        }
        return Optional.empty();
    }

    public static int parseComponentLifespanInfo(String xml) throws Exception {
        XPathParser parser = new XPathParser(xml);
        Optional<Integer> value = nodeValueToInt(parser, "value");
        Optional<Integer> total = nodeValueToInt(parser, "total");
        Optional<Integer> left = nodeValueToInt(parser, "left");
        if (value.isPresent() && total.isPresent()) {
            return (int) Math.round(100.0 * value.get() / total.get());
        } else if (value.isPresent()) {
            return (int) Math.round(0.01 * value.get());
        } else if (left.isPresent() && total.isPresent()) {
            return (int) Math.round(100.0 * left.get() / total.get());
        } else if (left.isPresent()) {
            return (int) Math.round((double) left.get() / 60.0);
        }
        return 0;
    }

    private static Optional<Integer> nodeValueToInt(XPathParser parser, String attrName) throws Exception {
        return parser.getFirstXPathMatchOpt("//ctl/@" + attrName).map(n -> Integer.valueOf(n.getNodeValue()));
    }
}
