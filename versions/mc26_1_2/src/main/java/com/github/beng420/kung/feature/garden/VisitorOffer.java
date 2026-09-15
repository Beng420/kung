package com.github.beng420.kung.feature.garden;

import com.github.beng420.kung.skyblock.HypixelLocation;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class VisitorOffer {
    private static final Pattern OFFERS_ACCEPTED = Pattern.compile("Offers Accepted: [0-9][0-9,]*");

    static boolean isDecision(String title, String button, List<String> visitorInfoLore, Set<String> visitors) {
        String action = HypixelLocation.clean(button);
        if (!action.equals("Accept Offer") && !action.equals("Refuse Offer")) return false;
        if (!visitors.contains(HypixelLocation.clean(title))) return false;
        // The visitor's info item distinguishes this menu from unrelated offers/trades.
        return visitorInfoLore.stream().limit(40).map(HypixelLocation::clean)
            .anyMatch(line -> OFFERS_ACCEPTED.matcher(line).matches());
    }
}
