package com.mighil.tinyurl.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RateLimitedExceptionTest {

    private static long seconds(Duration d) {
        return new RateLimitedException(d).retryAfterSeconds();
    }

    @Test
    void wholeSecondsAreUnchanged() {
        assertThat(seconds(Duration.ofSeconds(30))).isEqualTo(30);
    }

    @Test
    void fractionalSecondsRoundUp() {
        assertThat(seconds(Duration.ofMillis(29_001))).isEqualTo(30);
        assertThat(seconds(Duration.ofNanos(1))).isEqualTo(1);
    }

    @Test
    void neverAdvertisesZero() {
        assertThat(seconds(Duration.ZERO)).isEqualTo(1);
    }
}
