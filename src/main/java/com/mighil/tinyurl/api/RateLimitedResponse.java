package com.mighil.tinyurl.api;

public record RateLimitedResponse(String error, long retryAfterSeconds) {}
