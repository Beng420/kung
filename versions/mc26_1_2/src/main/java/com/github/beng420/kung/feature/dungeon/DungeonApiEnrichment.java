package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.util.HypixelSkyBlockProfileClient;
import com.github.beng420.kung.util.KungDebugRecorder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

public final class DungeonApiEnrichment {
    private final Set<String> requestedTotals = new HashSet<>();
    private final Set<String> requestedBaselines = new HashSet<>();
    private final Set<String> requestedFinals = new HashSet<>();
    private final Map<String, Integer> baselines = new HashMap<>();
    private boolean finalFetchStarted;
    private long finalFetchStartTick = Long.MIN_VALUE;
    private int pendingFinalFetches;

    public void reset() {
        requestedTotals.clear();
        requestedBaselines.clear();
        requestedFinals.clear();
        baselines.clear();
        finalFetchStarted = false;
        finalFetchStartTick = Long.MIN_VALUE;
        pendingFinalFetches = 0;
    }

    public boolean finalFetchStarted() {
        return finalFetchStarted;
    }

    public void startFinalFetch(long nowTick) {
        finalFetchStarted = true;
        finalFetchStartTick = nowTick;
    }

    public boolean finalFetchCompleteOrTimedOut(long nowTick, long timeoutTicks) {
        return pendingFinalFetches <= 0 || nowTick - finalFetchStartTick >= timeoutTicks;
    }

    public boolean hasPendingFinalFetches() {
        return pendingFinalFetches > 0;
    }

    public void requestTotal(
        Minecraft client,
        String name,
        Consumer<HypixelSkyBlockProfileClient.SecretResult> onSuccess
    ) {
        String requestedName = key(name);
        if (!requestedTotals.add(requestedName)) {
            return;
        }
        request(client, name, "total-secrets", result -> onSuccess.accept(result), () -> { });
    }

    public void requestBaseline(
        Minecraft client,
        String name,
        Consumer<HypixelSkyBlockProfileClient.SecretResult> onSuccess
    ) {
        String requestedName = key(name);
        if (!requestedBaselines.add(requestedName)) {
            return;
        }
        request(client, name, "run-secret baseline", result -> {
            baselines.put(resultKey(result, name), result.secretsFound());
            onSuccess.accept(result);
        }, () -> { });
    }

    public void requestFinal(
        Minecraft client,
        String name,
        BiConsumer<HypixelSkyBlockProfileClient.SecretResult, Integer> onSuccess
    ) {
        String requestedName = key(name);
        if (!requestedFinals.add(requestedName)) {
            return;
        }
        pendingFinalFetches++;
        request(client, name, "run-secret final", result -> {
            Integer baseline = baselines.get(resultKey(result, name));
            if (baseline == null) {
                baseline = baselines.get(requestedName);
            }
            int delta = baseline != null && result.secretsFound() >= baseline
                ? result.secretsFound() - baseline
                : -1;
            onSuccess.accept(result, delta);
        }, () -> pendingFinalFetches = Math.max(0, pendingFinalFetches - 1));
    }

    private static void request(
        Minecraft client,
        String name,
        String operation,
        Consumer<HypixelSkyBlockProfileClient.SecretResult> onSuccess,
        Runnable onComplete
    ) {
        HypixelSkyBlockProfileClient.INSTANCE.loadTotalSecrets(name).whenComplete((result, throwable) ->
            client.execute(() -> {
                try {
                    if (throwable != null) {
                        KungDebugRecorder.event("player-stats", operation + " failed player=" + name
                            + " error=" + throwable.getClass().getSimpleName());
                        return;
                    }
                    if (!result.success()) {
                        KungDebugRecorder.event("player-stats", operation + " failed player=" + name
                            + " error=" + result.error());
                        return;
                    }
                    onSuccess.accept(result);
                } finally {
                    onComplete.run();
                }
            })
        );
    }

    private static String resultKey(HypixelSkyBlockProfileClient.SecretResult result, String fallbackName) {
        String resultName = isPlayerName(result.name()) ? result.name() : fallbackName;
        return key(resultName);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static boolean isPlayerName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }
}
