package com.github.beng420.kung.feature.garden;

import java.util.Locale;

/** Repeated join announcements retain state; accounts, profiles and Feast identities stay separate. */
final class FeastPersistence {
    private final FeastStateStore store;
    private final FeastSession session;
    private final FeastKernels kernels;
    private String profile = "";
    private String profileId = "";

    FeastPersistence(FeastStateStore store, FeastSession session, FeastKernels kernels) {
        this.store = store;
        this.session = session;
        this.kernels = kernels;
    }

    boolean select(String account, String name) {
        if (name == null || name.isBlank()) return false;
        String next = account + ":" + name.toLowerCase(Locale.ROOT);
        if (next.equals(profile)) return false;
        if (!profile.isEmpty()) {
            session.invalidate();
            kernels.invalidate();
        }
        profile = next;
        var saved = store.get(profile);
        profileId = saved == null ? "" : saved.profileId();
        if (saved != null) kernels.restore(saved.kernels(), saved.pendingKernelGains());
        restoreProgress();
        save(); // A late first profile row may follow an already observed menu or donation.
        return true;
    }

    void identify(String id) {
        if (profile.isEmpty() || id == null || profileId.equals(id)) return;
        if (!profileId.isEmpty()) {
            // A deleted/recreated profile can reuse its fruit name, but not its server UUID.
            session.invalidate();
            kernels.invalidate();
            store.put(profile, new FeastStateStore.Saved(id, "", null, null, 0));
        }
        profileId = id;
        save();
    }

    void restoreProgress() {
        if (profile.isEmpty() || session.snapshot() != null || session.event() == null) return;
        var saved = store.get(profile);
        if (saved != null) session.restore(saved.eventKey(), saved.progress());
    }

    void save() {
        if (profile.isEmpty()) return;
        var previous = store.get(profile);
        var progress = session.snapshot();
        String event = progress == null ? "" : session.baselineEvent();
        if (progress == null && previous != null) {
            progress = previous.progress();
            event = previous.eventKey();
        }
        Long balance = kernels.balance();
        long pendingGains = kernels.pendingGains();
        if (balance == null && previous != null) {
            balance = previous.kernels();
            pendingGains = previous.pendingKernelGains();
        }
        store.put(profile, new FeastStateStore.Saved(profileId, event, progress, balance, pendingGains));
    }

    void reset() {
        profile = "";
        profileId = "";
    }
}
