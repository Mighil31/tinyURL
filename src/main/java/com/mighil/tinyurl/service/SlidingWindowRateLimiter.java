package com.mighil.tinyurl.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding window log: per key, remember the timestamps of allowed requests within the last {@code window}.
 *
 * <p>Boundary rule: a request recorded at instant t counts while {@code now < t + window};
 * at exactly {@code t + window} it has expired.
 *
 * <p>Thread safety: each key's log is guarded by its own monitor, so different keys never contend.
 * The clock is read inside that monitor, which keeps each log sorted oldest-first even under concurrency.
 */
public class SlidingWindowRateLimiter implements RateLimiter {

    private final Clock clock;
    private final Duration window;
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> logs = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(Clock clock, Duration window) {
        this.clock = clock;
        this.window = window;
    }

    @Override
    public Decision tryAcquire(String key, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1, was " + limit);
        }
        ArrayDeque<Instant> log = logs.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (log) {
            Instant now = clock.instant();
            Instant cutoff = now.minus(window);
            // t has expired when t + window <= now, i.e. t <= now - window
            while (!log.isEmpty() && !log.peekFirst().isAfter(cutoff)) {
                log.pollFirst();
            }
            if (log.size() < limit) {
                log.addLast(now);
                return Decision.allow();
            }
            return Decision.deny(Duration.between(now, freesSlotAt(log, limit)));
        }
    }

    /**
     * When the log would next hold fewer than {@code limit} entries. Normally that's the oldest entry
     * expiring, but if the log holds more than {@code limit} (the key's limit was lowered), the first
     * {@code size - limit} entries must expire too.
     */
    private Instant freesSlotAt(ArrayDeque<Instant> log, int limit) {
        Iterator<Instant> it = log.iterator();
        for (int i = 0; i < log.size() - limit; i++) {
            it.next();
        }
        return it.next().plus(window);
    }
}
