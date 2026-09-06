package com.github.beng420.kung.skyblock;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.util.KungDebugRecorder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public final class SkyBlockMayorTracker {
    public static final SkyBlockMayorTracker INSTANCE = new SkyBlockMayorTracker();

    private static final URI ELECTION_URI =
        URI.create("https://api.hypixel.net/v2/resources/skyblock/election");
    private static final long SUCCESS_REFRESH_INTERVAL_MILLIS = 30L * 60L * 1000L;
    private static final long FAILED_REFRESH_INTERVAL_MILLIS = 2L * 60L * 1000L;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private volatile boolean requestInFlight;
    private volatile boolean paulScoreBonusActive;
    private volatile double catacombsXpMultiplier = 1.0;
    private volatile String catacombsXpBoostLabel = "None";
    private volatile String mayorName = "";
    private volatile String ministerName = "";
    private volatile String status = "unknown";
    private volatile long nextRefreshAfterMillis;
    private String lastLoggedState = "";

    private SkyBlockMayorTracker() {
    }

    public static void initializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> INSTANCE.tick(client));
    }

    public boolean paulScoreBonusActive() {
        return paulScoreBonusActive;
    }

    public double catacombsXpMultiplier() {
        return catacombsXpMultiplier;
    }

    public String catacombsXpBoostLabel() {
        return catacombsXpBoostLabel;
    }

    public String mayorName() {
        return mayorName;
    }

    public String ministerName() {
        return ministerName;
    }

    public String status() {
        return status;
    }

    private void tick(Minecraft client) {
        if (client == null || client.player == null || requestInFlight) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextRefreshAfterMillis) {
            return;
        }
        refreshAsync(now);
    }

    private void refreshAsync(long nowMillis) {
        requestInFlight = true;
        nextRefreshAfterMillis = nowMillis + FAILED_REFRESH_INTERVAL_MILLIS;
        HttpRequest request = HttpRequest.newBuilder(ELECTION_URI)
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("User-Agent", "Kung-MayorTracker")
            .GET()
            .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete((response, throwable) -> {
                try {
                    if (throwable != null) {
                        markError(throwable);
                        return;
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        markError(new IllegalStateException("HTTP " + response.statusCode()));
                        return;
                    }
                    applyResponse(response.body());
                    nextRefreshAfterMillis = System.currentTimeMillis() + SUCCESS_REFRESH_INTERVAL_MILLIS;
                } catch (RuntimeException exception) {
                    markError(exception);
                } finally {
                    requestInFlight = false;
                }
            });
    }

    private void applyResponse(String body) {
        JsonElement rootElement = JsonParser.parseString(body);
        JsonObject root = rootElement != null && rootElement.isJsonObject() ? rootElement.getAsJsonObject() : null;
        if (root == null || !booleanMember(root, "success")) {
            throw new IllegalStateException("invalid response");
        }

        JsonObject mayor = objectMember(root, "mayor");
        String parsedMayorName = stringMember(mayor, "name");
        JsonObject minister = objectMember(mayor, "minister");
        String parsedMinisterName = stringMember(minister, "name");
        boolean active = isPaul(parsedMayorName)
            || isPaul(parsedMinisterName)
            || hasPaulScorePerk(mayor);
        MayorXpBoost xpBoost = mayorXpBoost(parsedMayorName, parsedMinisterName);

        mayorName = parsedMayorName;
        ministerName = parsedMinisterName;
        paulScoreBonusActive = active;
        catacombsXpMultiplier = xpBoost.multiplier();
        catacombsXpBoostLabel = xpBoost.label();
        status = "ok";
        logState();
    }

    private void markError(Throwable throwable) {
        status = "error:" + shortError(throwable);
        nextRefreshAfterMillis = System.currentTimeMillis() + FAILED_REFRESH_INTERVAL_MILLIS;
        KungMod.LOGGER.warn("Failed to fetch SkyBlock mayor data.", throwable);
        logState();
    }

    private void logState() {
        String state = "status=" + status
            + " mayor=" + blank(mayorName)
            + " minister=" + blank(ministerName)
            + " paulScore=" + paulScoreBonusActive
            + " cataXp=" + catacombsXpBoostLabel;
        if (!state.equals(lastLoggedState)) {
            lastLoggedState = state;
            KungDebugRecorder.event("mayor", state);
        }
    }

    private static MayorXpBoost mayorXpBoost(String mayor, String minister) {
        if (isAura(mayor) || isAura(minister)) {
            return new MayorXpBoost(1.59, "Aura (+59%)");
        }
        if (isDerpy(mayor) || isDerpy(minister)) {
            return new MayorXpBoost(1.5, "Derpy (+50%)");
        }
        return new MayorXpBoost(1.0, "None");
    }

    private static boolean hasPaulScorePerk(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                JsonElement value = entry.getValue();
                if ((key.equals("name") || key.equals("description")) && value.isJsonPrimitive()) {
                    String text = value.getAsString().toLowerCase(Locale.ROOT);
                    if (text.equals("ezpz")
                        || (text.contains("10") && text.contains("bonus") && text.contains("score"))
                        || (text.contains("dungeon") && text.contains("score"))) {
                        return true;
                    }
                }
                if (hasPaulScorePerk(value)) {
                    return true;
                }
            }
            return false;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                if (hasPaulScorePerk(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static JsonObject objectMember(JsonObject object, String name) {
        if (object == null || !object.has(name)) {
            return null;
        }
        JsonElement value = object.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String stringMember(JsonObject object, String name) {
        if (object == null || !object.has(name)) {
            return "";
        }
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static boolean booleanMember(JsonObject object, String name) {
        if (object == null || !object.has(name)) {
            return false;
        }
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    private static boolean isPaul(String name) {
        return "paul".equals(name.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isDerpy(String name) {
        return "derpy".equals(name.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isAura(String name) {
        return "aura".equals(name.trim().toLowerCase(Locale.ROOT));
    }

    private static String shortError(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        String name = cause.getClass().getSimpleName();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? name : name + "(" + message + ")";
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private record MayorXpBoost(double multiplier, String label) {
    }
}
