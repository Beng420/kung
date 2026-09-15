package com.github.beng420.kung.feature.garden;

import com.github.beng420.kung.util.KungDebugRecorder;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/** Optional, read-only startup seed. Local tracking does not depend on another mod. */
final class VisitorTimerCompatibility {
    record Seed(long intervalMillis, long remainingMillis) {}

    private Object gardenApi;
    private Method storageGetter;
    private Method intervalGetter;
    private Method arrivalGetter;
    private boolean checked;

    Seed read(long wallClockMillis) {
        if (!checked) {
            checked = true;
            if (!FabricLoader.getInstance().isModLoaded("skyhanni")) return null;
            try {
                Class<?> type = Class.forName("at.hannibal2.skyhanni.features.garden.GardenApi");
                gardenApi = type.getField("INSTANCE").get(null);
                storageGetter = type.getMethod("getStorage");
                Class<?> storageType = storageGetter.getReturnType();
                intervalGetter = storageType.getMethod("getVisitorInterval");
                // Kotlin value-class return types have a compiler-generated method suffix.
                for (Method method : storageType.getMethods()) {
                    if (method.getName().startsWith("getNextSixthVisitorArrival-")
                        && method.getParameterCount() == 0 && method.getReturnType() == long.class) arrivalGetter = method;
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                storageGetter = null;
                KungDebugRecorder.event("visitor-alarm", "optional-timer-unavailable; using local timer");
            }
        }
        if (storageGetter == null || intervalGetter == null || arrivalGetter == null) return null;
        try {
            Object storage = storageGetter.invoke(gardenApi);
            if (storage == null) return null;
            long interval = (long) intervalGetter.invoke(storage);
            long arrival = (long) arrivalGetter.invoke(storage);
            return validate(interval, arrival, wallClockMillis);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            storageGetter = null;
            return null;
        }
    }

    static Seed validate(long interval, long arrival, long now) {
        // Reject uninitialized/far-past sentinels and stale or implausible persisted clocks.
        if (interval <= 0 || interval > 900_000 || arrival <= 0
            || arrival < now - 86_400_000 || arrival > now + 900_000) return null;
        return new Seed(interval, Math.max(0, arrival - now));
    }
}
