package com.github.beng420.kung.feature.dungeon;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DungeonServerTickEvents {
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private DungeonServerTickEvents() {
    }

    public static void register(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void post() {
        for (Runnable listener : LISTENERS) {
            listener.run();
        }
    }
}
