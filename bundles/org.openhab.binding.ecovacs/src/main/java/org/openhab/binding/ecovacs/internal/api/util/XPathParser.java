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
package org.openhab.binding.ecovacs.internal.api.util;

import java.io.StringReader;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class XPathParser {
    private final InputSource input;
    private final XPath xPath = XPathFactory.newInstance().newXPath();

    public XPathParser(String xml) {
        this.input = new InputSource(new StringReader(xml));
    }

    public Node getFirstXPathMatch(String xpathExpression) throws XPathExpressionException, NoSuchElementException {
        NodeList nodes = getXPathMatches(xpathExpression);
        if (nodes.getLength() == 0) {
            throw new NoSuchElementException();
        }
        return nodes.item(0);
    }

    public Optional<Node> getFirstXPathMatchOpt(String xpathExpression)
            throws XPathExpressionException, NoSuchElementException {
        NodeList nodes = getXPathMatches(xpathExpression);
        return nodes.getLength() == 0 ? Optional.empty() : Optional.of(nodes.item(0));
    }

    public NodeList getXPathMatches(String xpathExpression) throws XPathExpressionException, NoSuchElementException {
        return (NodeList) xPath.evaluate(xpathExpression, input, XPathConstants.NODESET);
    }
}
