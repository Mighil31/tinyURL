package com.mighil.tinyurl.api;

public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("missing or invalid API key");
    }
}
