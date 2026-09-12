package com.github.beng420.kung.util;

/** Retains protocol-bundle provenance while vanilla dispatches its children. */
public final class ServerTickPacketContext {
    private static final ThreadLocal<Boolean> APPLYING_BUNDLE = new ThreadLocal<>();

    private ServerTickPacketContext() { }

    public static boolean isApplyingBundle() {
        return Boolean.TRUE.equals(APPLYING_BUNDLE.get());
    }

    public static void applyBundle(Runnable handler) {
        Boolean previous = APPLYING_BUNDLE.get();
        APPLYING_BUNDLE.set(true);
        try {
            handler.run();
        } finally {
            // Vanilla can throw to enqueue on its packet thread. That path and
            // failed/nested dispatch must not mark subsequent standalone pings.
            if (previous == null) APPLYING_BUNDLE.remove();
            else APPLYING_BUNDLE.set(previous);
        }
    }
}
