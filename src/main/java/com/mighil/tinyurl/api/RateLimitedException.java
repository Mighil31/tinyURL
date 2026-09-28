package com.mighil.tinyurl.api;

import java.time.Duration;

public class RateLimitedException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitedException(Duration retryAfter) {
        super("rate limit exceeded");
        this.retryAfter = retryAfter;
    }

    /** Whole seconds, rounded up so a client that waits this long is guaranteed a slot; at least 1. */
    public long retryAfterSeconds() {
        long seconds = retryAfter.getSeconds() + (retryAfter.getNano() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
