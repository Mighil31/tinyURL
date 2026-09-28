package com.mighil.tinyurl.service;

import java.time.Duration;

public interface RateLimiter {

    /**
     * Atomically checks and records one request for {@code key}.
     *
     * <p>Allowed if fewer than {@code limit} requests were recorded for this key within the window
     * ending now. Denied requests are NOT recorded.
     *
     * @param key   caller identity (the API key)
     * @param limit max requests per window for this key; must be >= 1
     * @return allowed, or denied with the exact time until the next request would be allowed
     * @throws IllegalArgumentException if limit < 1
     */
    Decision tryAcquire(String key, int limit);

    record Decision(boolean allowed, Duration retryAfter) {

        public static Decision allow() {
            return new Decision(true, Duration.ZERO);
        }

        public static Decision deny(Duration retryAfter) {
            return new Decision(false, retryAfter);
        }
    }
}
