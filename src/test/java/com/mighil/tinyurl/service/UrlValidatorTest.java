package com.mighil.tinyurl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path?q=1#frag",
            "HTTPS://Example.com",
            "http://localhost:8080/x",
            "https://sub.example.co.uk/a%20b"
    })
    void acceptsHttpAndHttpsUrlsWithHost(String url) {
        assertThat(validator.validate(url).getHost()).isNotBlank();
    }

    @Test
    void stripsSurroundingWhitespace() {
        assertThat(validator.validate("  https://example.com  ").toString()).isEqualTo("https://example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "ftp://example.com",
            "javascript:alert(1)",
            "mailto:a@example.com",
            "example.com",
            "/relative/path",
            "http://",
            "http:example.com",
            "https://exa mple.com",
            "http:///path-only"
    })
    void rejectsInvalidUrls(String url) {
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsUrlsLongerThanMax() {
        String url = "https://example.com/" + "a".repeat(UrlValidator.MAX_LENGTH);
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void acceptsUrlOfExactlyMaxLength() {
        String prefix = "https://example.com/";
        String url = prefix + "a".repeat(UrlValidator.MAX_LENGTH - prefix.length());
        assertThat(validator.validate(url).toString()).hasSize(UrlValidator.MAX_LENGTH);
    }
}
