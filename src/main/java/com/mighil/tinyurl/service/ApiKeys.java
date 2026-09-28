package com.mighil.tinyurl.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Seeded API keys, parsed once at startup from {@code key:tier,key:tier}. A bad spec fails startup. */
@Component
public class ApiKeys {

    private final Map<String, Tier> tiers;

    public ApiKeys(@Value("${app.api-keys}") String spec) {
        this.tiers = parse(spec);
    }

    public Optional<Tier> tierOf(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(tiers.get(key));
    }

    /** Stable, non-reversible identifier for a key, safe to store next to links. */
    public static String fingerprint(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Map<String, Tier> parse(String spec) {
        Map<String, Tier> result = new HashMap<>();
        for (String entry : spec.split(",")) {
            if (entry.isBlank()) {
                continue;
            }
            String[] parts = entry.strip().split(":");
            if (parts.length != 2 || parts[0].isBlank()) {
                throw new IllegalArgumentException("API_KEYS entries must look like key:tier");
            }
            Tier tier;
            try {
                tier = Tier.valueOf(parts[1].strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown tier '" + parts[1] + "' in API_KEYS");
            }
            if (result.put(parts[0].strip(), tier) != null) {
                throw new IllegalArgumentException("duplicate key in API_KEYS");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("API_KEYS must contain at least one key");
        }
        return Map.copyOf(result);
    }
}
