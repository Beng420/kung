package com.github.beng420.kung.update;

import static org.junit.Assert.*;

import java.util.Map;
import org.junit.Test;

public final class KungUpdateEnvironmentTest {
    @Test public void modrinthIpcProtectsManagedFilesEvenWithAnOverriddenLauncherBrand() {
        assertTrue(KungUpdateEnvironment.requiresLauncherInstall(Map.of(
            "minecraft.launcher.brand", "custom",
            "modrinth.internal.ipc.host", "127.0.0.1")));
        assertTrue(KungUpdateEnvironment.requiresLauncherInstall(Map.of(
            "minecraft.launcher.brand", "custom",
            "modrinth.internal.ipc.port", "42317")));
    }

    @Test public void launcherIdentityWorksWithoutIpcAndIgnoresCase() {
        for (String brand : new String[] {"theseus", "THESEUS", "Modrinth", " modrinth "}) {
            assertTrue(brand, KungUpdateEnvironment.requiresLauncherInstall(Map.of(
                "minecraft.launcher.brand", brand)));
        }
    }

    @Test public void presentButEmptyIpcPropertiesStillIdentifyLauncherOwnership() {
        assertTrue(KungUpdateEnvironment.requiresLauncherInstall(Map.of(
            "modrinth.internal.ipc.host", "")));
        assertTrue(KungUpdateEnvironment.requiresLauncherInstall(Map.of(
            "modrinth.internal.ipc.port", "")));
    }

    @Test public void unrelatedPropertiesAndOtherLaunchersDoNotDisableSelfInstallation() {
        assertFalse(KungUpdateEnvironment.requiresLauncherInstall(Map.of()));
        for (String brand : new String[] {"", "minecraft-launcher", "PrismLauncher", "notmodrinth", "theseus-custom"}) {
            assertFalse(brand, KungUpdateEnvironment.requiresLauncherInstall(Map.of(
                "minecraft.launcher.brand", brand,
                "user.dir", "/games/Modrinth experiments",
                "modrinth", "present")));
        }
    }
}
