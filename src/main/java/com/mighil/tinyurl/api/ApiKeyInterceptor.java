package com.mighil.tinyurl.api;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.mighil.tinyurl.service.ApiKeys;
import com.mighil.tinyurl.service.RateLimiter;
import com.mighil.tinyurl.service.RateLimiter.Decision;
import com.mighil.tinyurl.service.Tier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Guards POST /links: authenticate, then rate-limit, before the body is parsed.
 * Order of failures is therefore always 401, then 429, then 400.
 */
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    static final String HEADER = "X-API-Key";
    static final String OWNER_ATTRIBUTE = "ownerKeyFingerprint";

    private final ApiKeys apiKeys;
    private final RateLimiter limiter;

    public ApiKeyInterceptor(ApiKeys apiKeys, RateLimiter limiter) {
        this.apiKeys = apiKeys;
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String key = request.getHeader(HEADER);
        Tier tier = apiKeys.tierOf(key).orElseThrow(UnauthorizedException::new);
        Decision decision = limiter.tryAcquire(key, tier.creationsPerMinute());
        if (!decision.allowed()) {
            throw new RateLimitedException(decision.retryAfter());
        }
        request.setAttribute(OWNER_ATTRIBUTE, ApiKeys.fingerprint(key));
        return true;
    }
}
