package com.mighil.tinyurl.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

/** Accepts absolute http/https URLs with a host, up to MAX_LENGTH characters. No DNS lookups. */
@Component
public class UrlValidator {

    static final int MAX_LENGTH = 2048;
    private static final Set<String> SCHEMES = Set.of("http", "https");

    public URI validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidUrlException("url is required");
        }
        String url = raw.strip();
        if (url.length() > MAX_LENGTH) {
            throw new InvalidUrlException("url must be at most " + MAX_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("url is malformed");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new InvalidUrlException("url must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("url must have a host");
        }
        return uri;
    }
}
