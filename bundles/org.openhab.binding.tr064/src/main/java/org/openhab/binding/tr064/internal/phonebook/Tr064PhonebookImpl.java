/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.tr064.internal.phonebook;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.tr064.internal.dto.additions.PhonebooksType;
import org.openhab.core.i18n.LocaleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * The {@link Tr064PhonebookImpl} class implements a phonebook
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
public class Tr064PhonebookImpl implements Phonebook {
    private final Logger logger = LoggerFactory.getLogger(Tr064PhonebookImpl.class);

    private Map<String, String> phonebook = new HashMap<>();

    private final LocaleProvider localeProvider;
    private final HttpClient httpClient;
    private final String phonebookUrl;

    private String phonebookName = "";

    public Tr064PhonebookImpl(HttpClient httpClient, LocaleProvider localeProvider, String phonebookUrl) {
        this.httpClient = httpClient;
        this.localeProvider = localeProvider;
        this.phonebookUrl = phonebookUrl;
        getPhonebook();
    }

    private void getPhonebook() {
        try {
            ContentResponse contentResponse = httpClient.newRequest(phonebookUrl).method(HttpMethod.GET)
                    .timeout(2, TimeUnit.SECONDS).send();
            InputStream xml = new ByteArrayInputStream(contentResponse.getContent());

            JAXBContext context = JAXBContext.newInstance(PhonebooksType.class);
            Unmarshaller um = context.createUnmarshaller();
            PhonebooksType phonebooksType = um.unmarshal(new StreamSource(xml), PhonebooksType.class).getValue();

            phonebookName = phonebooksType.getPhonebook().getName();

            phonebook = phonebooksType.getPhonebook().getContact().stream().map(contact -> {
                String contactName = contact.getPerson().getRealName();
                return contact.getTelephony().getNumber().stream()
                        .collect(Collectors.toMap(number -> normalizeNumber(number.getValue()), number -> contactName));
            }).collect(HashMap::new, HashMap::putAll, HashMap::putAll);
            logger.debug("Downloaded phonebook {}: {}", phonebookName, phonebook);
        } catch (JAXBException | InterruptedException | ExecutionException | TimeoutException e) {
            logger.warn("Failed to get phonebook with URL {}:", phonebookUrl, e);
        }
    }

    @Override
    public String getName() {
        return phonebookName;
    }

    @Override
    public Optional<String> lookupNumber(String number) {
        String normalized = normalizeNumber(number);
        logger.trace("Normalized '{}' to '{}'", number, normalized);
        return normalized.isBlank() ? Optional.empty()
                : phonebook.keySet().stream().filter(n -> n.equals(normalized)).findFirst().map(phonebook::get);
    }

    @Override
    public String toString() {
        return "Phonebook{" + "phonebookName='" + phonebookName + "', phonebook=" + phonebook + '}';
    }

    private String normalizeNumber(String input) {
        final PhoneNumberUtil pnu = PhoneNumberUtil.getInstance();
        final Locale locale = localeProvider != null ? localeProvider.getLocale() : null;
        try {
            final PhoneNumber number = pnu.parse(input, locale != null ? locale.getCountry() : null);
            if (pnu.isValidNumber(number)) {
                return pnu.format(number, PhoneNumberFormat.E164);
            }
        } catch (NumberParseException e) {
            logger.debug("Failed to parse '{}' as phone number: {}", input, e.getMessage());
        }
        return input;
    }
}
