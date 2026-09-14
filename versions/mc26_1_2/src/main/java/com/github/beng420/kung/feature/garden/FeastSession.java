package com.github.beng420.kung.feature.garden;

/** Progress is bound to one Feast; persisted baselines require the same event identity. */
final class FeastSession {
    private final FeastProgress progress = new FeastProgress();
    private FeastContext.Event event;
    private String baselineEvent = "";
    private Object menu;
    private FeastProgress.Snapshot lastMenuSnapshot;

    FeastContext.Event event() { return event; }
    FeastProgress.Snapshot snapshot() { return progress.snapshot(); }
    String baselineEvent() { return baselineEvent; }

    boolean restore(String key, FeastProgress.Snapshot saved) {
        if (snapshot() != null || event == null || saved == null
            || !event.key().equals(key) || event.kind() != saved.kind()) return false;
        progress.synchronize(saved);
        baselineEvent = key;
        return true;
    }

    void select(FeastContext.Event next, boolean dateKnown) {
        event = next;
        if (next != null) {
            if (!baselineEvent.isEmpty() && !baselineEvent.equals(next.key())) invalidate();
            baselineEvent = next.key();
        } else if (dateKnown) {
            invalidate();
        }
    }

    boolean observeMenu(Object currentMenu, FeastProgress.Snapshot next) {
        if (currentMenu != menu) {
            menu = currentMenu;
            lastMenuSnapshot = null;
        }
        if (event == null || next == null || next.kind() != event.kind() || next.equals(lastMenuSnapshot)) return false;
        boolean reopened = lastMenuSnapshot == null;
        lastMenuSnapshot = next;
        var previous = progress.snapshot();
        // A delayed coherent menu update must not undo donations already received in chat.
        // Reopening explicitly resynchronizes, including corrections to a lower server total.
        if (!reopened && previous != null && previous.goals().equals(next.goals())
            && previous.donations() > next.donations()) return false;
        progress.synchronize(next);
        baselineEvent = event.key();
        return true;
    }

    boolean donate(String message) { return event != null && progress.donate(message); }

    void closeMenu() {
        menu = null;
        lastMenuSnapshot = null;
    }

    void invalidate() {
        progress.reset();
        baselineEvent = "";
        // Retain the last menu observation: unchanged old lore must not restore a reset total.
    }

    void reset() {
        invalidate();
        event = null;
        closeMenu();
    }
}
