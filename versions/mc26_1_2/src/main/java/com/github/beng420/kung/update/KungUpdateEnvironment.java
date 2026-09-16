package com.github.beng420.kung.update;

import java.util.HashMap;
import java.util.Map;

final class KungUpdateEnvironment {
    private static final String LAUNCHER_BRAND = "minecraft.launcher.brand";
    private static final String MODRINTH_IPC_HOST = "modrinth.internal.ipc.host";
    private static final String MODRINTH_IPC_PORT = "modrinth.internal.ipc.port";

    private KungUpdateEnvironment() {}

    static boolean requiresLauncherInstall() {
        Map<String, String> properties = new HashMap<>();
        for (String key : new String[] {LAUNCHER_BRAND, MODRINTH_IPC_HOST, MODRINTH_IPC_PORT}) {
            String value = System.getProperty(key);
            if (value != null) properties.put(key, value);
        }
        return requiresLauncherInstall(properties);
    }

    static boolean requiresLauncherInstall(Map<String, String> properties) {
        // Modrinth validates managed JAR paths and hashes before launch, including copied files.
        // Replacing a JAR outside its installer can block the entire instance on the next launch.
        if (properties.containsKey(MODRINTH_IPC_HOST) || properties.containsKey(MODRINTH_IPC_PORT)) {
            return true;
        }
        String brand = properties.get(LAUNCHER_BRAND);
        if (brand == null) return false;
        brand = brand.strip();
        return "theseus".equalsIgnoreCase(brand) || "modrinth".equalsIgnoreCase(brand);
    }
}
