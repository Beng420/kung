package com.github.beng420.kung.runtime;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class KungPaths {
    private KungPaths() {
    }

    public static Path gameDirectory() {
        try {
            return FabricLoader.getInstance().getGameDir();
        } catch (IllegalStateException exception) {
            return Path.of("").toAbsolutePath().normalize();
        }
    }

    public static Path dungeonDataDirectory() {
        return gameDirectory().resolve("kung-dungeon-scans");
    }
}
