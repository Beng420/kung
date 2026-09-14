package com.github.beng420.kung.feature.garden;

import com.github.beng420.kung.skyblock.HypixelLocation;
import java.util.List;
import java.util.regex.Pattern;

/** Kernel currency is independent of Feast donation tiers and survives event changes. */
final class FeastKernels {
    private static final Pattern BALANCE = Pattern.compile("^Your Kernels: ([0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)$");
    private static final Pattern SIDEBAR_BALANCE = Pattern.compile("^Kernels: ([0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)$");
    private static final Pattern SIDEBAR_SUFFIX_BALANCE = Pattern.compile(
        "^([0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?: \\([+-][0-9,]+\\))? Kernels$");
    private static final Pattern GAIN = Pattern.compile(
        "^(?:\\[\\d{2}:\\d{2}:\\d{2}\\] )?\\[NPC\\] Feast Chef Ted: "
            + "Thanks for the donation! I've added a Kernel to your purse\\.$");

    private Long balance;
    private Object menu;
    private Long lastMenuBalance;
    private Long lastSidebarBalance;

    Long balance() { return balance; }

    void restore(Long saved) {
        if (balance == null && saved != null && saved >= 0) balance = saved;
    }

    static boolean supportsMenu(String title) {
        String clean = HypixelLocation.clean(title);
        return clean.equals("Grand Feast") || clean.equals("Grand Bakery");
    }

    static Long readMenu(String title, List<FeastProgress.MenuItem> items) {
        if (!supportsMenu(title) || items.size() > 54) return null;
        Long found = null;
        for (var item : items) {
            for (String raw : item.lore().stream().limit(40).toList()) {
                var matcher = BALANCE.matcher(HypixelLocation.clean(raw.replaceAll("[\\p{Zs}\\t]+", " ")));
                if (!matcher.matches()) continue;
                try {
                    long amount = Long.parseLong(matcher.group(1).replace(",", ""));
                    if (found != null && found != amount) return null;
                    found = amount;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return found;
    }

    boolean observeMenu(Object currentMenu, Long amount) {
        if (menu != currentMenu) {
            menu = currentMenu;
            lastMenuBalance = null;
        }
        if (amount == null || amount < 0 || amount.equals(lastMenuBalance)) return false;
        lastMenuBalance = amount;
        boolean changed = !amount.equals(balance);
        balance = amount;
        return changed;
    }

    boolean observeMessage(String text) {
        if (balance == null || balance == Long.MAX_VALUE || !GAIN.matcher(HypixelLocation.clean(text)).matches()) return false;
        balance++;
        return true;
    }

    boolean observeSidebar(List<String> lines) {
        Long found = null;
        for (String raw : lines) {
            String clean = HypixelLocation.clean(raw);
            var matcher = SIDEBAR_BALANCE.matcher(clean);
            if (!matcher.matches()) matcher = SIDEBAR_SUFFIX_BALANCE.matcher(clean);
            if (!matcher.matches()) continue;
            try {
                long amount = Long.parseLong(matcher.group(1).replace(",", ""));
                if (found != null && found != amount) return false;
                found = amount;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        if (found == null || found.equals(lastSidebarBalance)) return false;
        lastSidebarBalance = found;
        boolean changed = !found.equals(balance);
        balance = found;
        return changed;
    }

    void worldChanged() {
        lastSidebarBalance = null;
    }

    void closeMenu() {
        menu = null;
        lastMenuBalance = null;
    }

    void invalidate() {
        balance = null;
        // Unchanged lore from a menu left open across a profile change must not restore its balance.
    }

    void reset() {
        invalidate();
        closeMenu();
        worldChanged();
    }
}
