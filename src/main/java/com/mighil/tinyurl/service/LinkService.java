package com.mighil.tinyurl.service;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.mighil.tinyurl.storage.LinkRepository;

@Service
public class LinkService {

    static final int MAX_ATTEMPTS = 5;

    private final LinkRepository links;
    private final CodeGenerator codes;
    private final UrlValidator validator;

    public LinkService(LinkRepository links, CodeGenerator codes, UrlValidator validator) {
        this.links = links;
        this.codes = codes;
        this.validator = validator;
    }

    public record Link(String code, String longUrl) {}

    public Link create(String rawUrl) {
        String longUrl = validator.validate(rawUrl).toString();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String code = codes.next();
            try {
                links.insert(code, longUrl);
                return new Link(code, longUrl);
            } catch (DuplicateKeyException collision) {
                // extremely rare at 62^7; try another code
            }
        }
        throw new IllegalStateException("could not allocate a unique code after " + MAX_ATTEMPTS + " attempts");
    }

    public Optional<String> resolve(String code) {
        return links.findLongUrl(code);
    }
}
