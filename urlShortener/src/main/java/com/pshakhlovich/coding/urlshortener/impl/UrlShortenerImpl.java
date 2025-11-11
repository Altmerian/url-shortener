package com.pshakhlovich.coding.urlshortener.impl;

import com.pshakhlovich.coding.urlshortener.UrlShortener;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class UrlShortenerImpl implements UrlShortener {

    private final Map<String, String> shortenToOriginalUrls = new ConcurrentHashMap<>();

    static final String URL_TEMPLATE = "%s%s/%s";
    static final String PREFIX = "https://";
    static final String OUTPUT_DOMAIN = "accounts";
    static final int MAX_SHORTENED_URL_LENGTH = 20;
    static final Pattern PATTERN = Pattern.compile("[0-9a-zA-Z]");

    @Override
    public String shortUrl(String originalUrl) {
        var randomPartLength = MAX_SHORTENED_URL_LENGTH - (OUTPUT_DOMAIN.length() + PREFIX.length() + 1);

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < randomPartLength; i++) {
            char character = (char) new Random().nextInt(255);
            stringBuilder.append(character);
        }

        if (!PATTERN.matcher(stringBuilder.toString()).matches()) {
            throw new IllegalArgumentException("Shorten URL contains invalid characters.");
        }

        var result = URL_TEMPLATE.formatted(PREFIX, OUTPUT_DOMAIN, stringBuilder.toString());

        shortenToOriginalUrls.put(result, originalUrl);

        System.out.println(result);

        return result;
    }

    @Override
    public String getOriginalUrl(String shortenUrl) {
        return shortenToOriginalUrls.get(shortenUrl);
    }
}
