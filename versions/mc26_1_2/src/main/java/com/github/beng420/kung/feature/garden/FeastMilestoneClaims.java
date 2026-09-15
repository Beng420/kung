package com.github.beng420.kung.feature.garden;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Click intentions are confirmed only by server items, never local inventory prediction. */
final class FeastMilestoneClaims {
    private static final long CONFIRMATION_TIMEOUT = 15_000;
    private static final Pattern REWARD = Pattern.compile("^- Kernels x([0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)$");
    private final Map<Integer, Pending> pending = new HashMap<>();
    private String event = "";
    private int creditedTiers;
    private Long expectedBalance;

    record Milestone(int tier, int kernels, boolean claimable) {}
    record Credit(int tier, int kernels, Long minimumBalance) {}
    private record Pending(Milestone milestone, long expiresAt) {}

    static Milestone read(String title, FeastProgress.MenuItem item, boolean glint) {
        if (FeastProgress.Kind.fromTitle(title) != FeastProgress.Kind.GRAND) return null;
        int tier = FeastProgress.milestoneTier(item.name());
        if (tier < 1 || tier > 9 || !FeastProgress.completedMilestone(item)) return null;
        boolean rewards = false;
        boolean claimable = glint;
        int kernels = 0;
        for (String raw : item.lore().stream().limit(40).toList()) {
            String line = FeastProgress.cleanMenuText(raw);
            if (line.equals("Rewards:")) rewards = true;
            if (line.equals("Click to claim!")) claimable = true;
            var matcher = REWARD.matcher(line);
            if (!rewards || !matcher.matches()) continue;
            if (kernels != 0) return null;
            try {
                kernels = Integer.parseInt(matcher.group(1).replace(",", ""));
                if (kernels <= 0 || kernels > 1_000_000) return null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return kernels == 0 ? null : new Milestone(tier, kernels, claimable);
    }

    boolean begin(Milestone milestone, Long balance, long now) {
        expire(now);
        if (milestone == null || !milestone.claimable() || pending.containsKey(milestone.tier())
            || (creditedTiers & (1 << milestone.tier())) != 0) return false;
        // All overlapping clicks share a baseline, even if the sidebar already includes one reward.
        if (pending.isEmpty()) expectedBalance = balance;
        pending.put(milestone.tier(), new Pending(milestone, now + CONFIRMATION_TIMEOUT));
        return true;
    }

    Credit observeServer(Milestone milestone, long now) {
        expire(now);
        if (milestone == null || milestone.claimable()) return null;
        var claim = pending.get(milestone.tier());
        if (claim == null || claim.milestone().kernels() != milestone.kernels()) return null;
        earned(milestone.kernels());
        var credit = new Credit(milestone.tier(), milestone.kernels(), expectedBalance);
        creditedTiers |= 1 << milestone.tier();
        pending.remove(milestone.tier());
        if (pending.isEmpty()) expectedBalance = null;
        return credit;
    }

    void earned(long amount) {
        if (expectedBalance != null) expectedBalance += Math.min(amount, Long.MAX_VALUE - expectedBalance);
    }

    boolean pending() { return !pending.isEmpty(); }

    void selectEvent(String key) {
        if (key == null || key.isEmpty()) return;
        if (!event.isEmpty() && !event.equals(key)) reset();
        event = key;
    }

    void clearPending() {
        pending.clear();
        expectedBalance = null;
    }

    void reset() {
        clearPending();
        event = "";
        creditedTiers = 0;
    }

    private void expire(long now) {
        pending.values().removeIf(claim -> now >= claim.expiresAt());
        if (pending.isEmpty()) expectedBalance = null;
    }
}
