package com.mighil.tinyurl.service;

public enum Tier {
    FREE(10),
    PRO(100);

    private final int creationsPerMinute;

    Tier(int creationsPerMinute) {
        this.creationsPerMinute = creationsPerMinute;
    }

    public int creationsPerMinute() {
        return creationsPerMinute;
    }
}
