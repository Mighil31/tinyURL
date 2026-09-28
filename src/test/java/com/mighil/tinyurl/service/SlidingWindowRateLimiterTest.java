package com.mighil.tinyurl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.mighil.tinyurl.service.RateLimiter.Decision;

class SlidingWindowRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final RateLimiter limiter = new SlidingWindowRateLimiter(clock, WINDOW);

    @Test
    void allowsUpToLimitThenDenies() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("k", 10).allowed()).as("request %d", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire("k", 10).allowed()).isFalse();
    }

    @Test
    void allowedDecisionHasZeroRetryAfter() {
        assertThat(limiter.tryAcquire("k", 10)).isEqualTo(Decision.allow());
    }

    @Test
    void retryAfterIsTimeUntilOldestRequestLeavesWindow() {
        limiter.tryAcquire("k", 2);                 // t=0
        clock.advance(Duration.ofSeconds(10));
        limiter.tryAcquire("k", 2);                 // t=10s
        clock.advance(Duration.ofSeconds(20));      // t=30s

        Decision denied = limiter.tryAcquire("k", 2);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isEqualTo(Duration.ofSeconds(30)); // oldest (t=0) expires at t=60s
    }

    @Test
    void requestExpiresExactlyAtWindowBoundary() {
        limiter.tryAcquire("k", 1);                 // t=0

        clock.advance(WINDOW.minusMillis(1));       // t=59.999s: still inside
        Decision justBefore = limiter.tryAcquire("k", 1);
        assertThat(justBefore.allowed()).isFalse();
        assertThat(justBefore.retryAfter()).isEqualTo(Duration.ofMillis(1));

        clock.advance(Duration.ofMillis(1));        // t=60s: expired
        assertThat(limiter.tryAcquire("k", 1).allowed()).isTrue();
    }

    @Test
    void waitingExactlyRetryAfterIsEnough() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("k", 3);
            clock.advance(Duration.ofMillis(1_234));
        }
        Decision denied = limiter.tryAcquire("k", 3);
        assertThat(denied.allowed()).isFalse();

        clock.advance(denied.retryAfter());
        assertThat(limiter.tryAcquire("k", 3).allowed()).isTrue();
    }

    @Test
    void windowSlidesRatherThanResettingAllAtOnce() {
        for (int i = 0; i < 5; i++) limiter.tryAcquire("k", 10);   // 5 at t=0
        clock.advance(Duration.ofSeconds(30));
        for (int i = 0; i < 5; i++) limiter.tryAcquire("k", 10);   // 5 at t=30s
        clock.advance(Duration.ofSeconds(30));                     // t=60s: the t=0 batch expired

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("k", 10).allowed()).as("freed slot %d", i + 1).isTrue();
        }
        Decision denied = limiter.tryAcquire("k", 10);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isEqualTo(Duration.ofSeconds(30)); // t=30s batch expires at t=90s
    }

    @Test
    void deniedRequestsAreNotRecorded() {
        limiter.tryAcquire("k", 1);                 // t=0
        clock.advance(Duration.ofSeconds(1));
        for (int i = 0; i < 1_000; i++) {
            assertThat(limiter.tryAcquire("k", 1).allowed()).isFalse();
        }
        clock.advance(Duration.ofSeconds(59));      // t=60s: only the t=0 request ever counted
        assertThat(limiter.tryAcquire("k", 1).allowed()).isTrue();
    }

    @Test
    void keysAreIndependent() {
        assertThat(limiter.tryAcquire("a", 1).allowed()).isTrue();
        assertThat(limiter.tryAcquire("a", 1).allowed()).isFalse();
        assertThat(limiter.tryAcquire("b", 1).allowed()).isTrue();
    }

    @Test
    void limitIsPerCallSoTiersCanDiffer() {
        for (int i = 0; i < 10; i++) limiter.tryAcquire("free", 10);
        for (int i = 0; i < 10; i++) limiter.tryAcquire("pro", 100);

        assertThat(limiter.tryAcquire("free", 10).allowed()).isFalse();
        assertThat(limiter.tryAcquire("pro", 100).allowed()).isTrue();
    }

    @Test
    void loweredLimitWaitsUntilEnoughEntriesExpire() {
        for (int i = 0; i < 3; i++) {               // 3 at t=0s,10s,20s under limit 3
            limiter.tryAcquire("k", 3);
            clock.advance(Duration.ofSeconds(10));
        }                                           // t=30s; limit drops to 1

        Decision denied = limiter.tryAcquire("k", 1);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isEqualTo(Duration.ofSeconds(50)); // t=10s entry expires at t=80s
        clock.advance(denied.retryAfter());
        assertThat(limiter.tryAcquire("k", 1).allowed()).isTrue();
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThatThrownBy(() -> limiter.tryAcquire("k", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.tryAcquire("k", -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentRequestsOnSameKeyNeverExceedLimit() throws Exception {
        int threads = 32, perThread = 50, limit = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            results.add(pool.submit(() -> {
                start.await();
                int allowed = 0;
                for (int i = 0; i < perThread; i++) {
                    if (limiter.tryAcquire("hot", limit).allowed()) allowed++;
                }
                return allowed;
            }));
        }
        start.countDown();
        int total = 0;
        for (Future<Integer> f : results) total += f.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(total).isEqualTo(limit);
    }
}
