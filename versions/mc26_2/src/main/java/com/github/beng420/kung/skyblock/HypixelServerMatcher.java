package com.github.beng420.kung.skyblock;

import java.util.Locale;

public final class HypixelServerMatcher {
    private HypixelServerMatcher() {
    }

    public static boolean isHypixelAddress(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }

        String normalized = address.toLowerCase(Locale.ROOT).trim();
        return normalized.equals("hypixel.net")
            || normalized.equals("mc.hypixel.net")
            || normalized.endsWith(".hypixel.net");
    }
}
