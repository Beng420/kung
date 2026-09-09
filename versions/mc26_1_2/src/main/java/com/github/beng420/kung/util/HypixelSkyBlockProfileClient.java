package com.github.beng420.kung.util;

import com.github.beng420.kung.config.KungConfig;

import com.github.beng420.kung.util.CatacombsAverageCalculator.DungeonClass;
import com.github.beng420.kung.util.CatacombsAverageCalculator.PlayerData;
import com.github.beng420.kung.util.CatacombsAverageCalculator.ProfileData;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class HypixelSkyBlockProfileClient {
    public static final HypixelSkyBlockProfileClient INSTANCE = new HypixelSkyBlockProfileClient();

    private static final URI MOJANG_PROFILE_API =
        URI.create("https://api.minecraftservices.com/minecraft/profile/lookup/name/");
    private static final URI HYPIXEL_PLAYER_API = URI.create("https://api.hypixel.net/v2/player");
    private static final URI HYPIXEL_PROFILES_API = URI.create("https://api.hypixel.net/v2/skyblock/profiles");
    private static final int API_ATTEMPTS = 3;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private HypixelSkyBlockProfileClient() {
    }

    public CompletableFuture<ProfileResult> loadPlayer(String username) {
        String key = KungConfig.get().misc.hypixelApiKey();
        String normalized = username == null ? "" : username.trim();
        if (!KungConfig.get().misc.hypixelApiEnabled()) {
            return CompletableFuture.completedFuture(ProfileResult.error("Hypixel API is disabled"));
        }
        if (key.isBlank()) {
            return CompletableFuture.completedFuture(ProfileResult.error("Hypixel API key is not set"));
        }
        if (!validUsername(normalized)) {
            return CompletableFuture.completedFuture(ProfileResult.error("invalid username"));
        }
        return resolveUsername(normalized)
            .thenCompose(profile -> loadHypixel(profile, key));
    }

    public CompletableFuture<SecretResult> loadTotalSecrets(String username) {
        String key = KungConfig.get().misc.hypixelApiKey();
        String normalized = username == null ? "" : username.trim();
        if (!KungConfig.get().misc.hypixelApiEnabled()) {
            return CompletableFuture.completedFuture(SecretResult.error("Hypixel API is disabled"));
        }
        if (key.isBlank()) {
            return CompletableFuture.completedFuture(SecretResult.error("Hypixel API key is not set"));
        }
        if (!validUsername(normalized)) {
            return CompletableFuture.completedFuture(SecretResult.error("invalid username"));
        }
        return resolveUsername(normalized)
            .thenCompose(profile -> loadHypixelObject(HYPIXEL_PLAYER_API, "uuid", profile.uuid(), key)
                .thenApply(playerRoot -> SecretResult.ok(profile.name(), totalSecrets(playerRoot)))
                .exceptionally(throwable -> SecretResult.error(shortError(throwable))));
    }

    private CompletableFuture<MinecraftProfile> resolveUsername(String username) {
        URI uri = URI.create(MOJANG_PROFILE_API + URLEncoder.encode(username, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")
            .header("User-Agent", "Kung-HypixelProfile")
            .GET()
            .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("Mojang returned HTTP " + response.statusCode());
                }
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                String id = string(root, "id", "");
                String name = string(root, "name", username);
                if (id.isBlank()) {
                    throw new IllegalStateException("Mojang returned no UUID");
                }
                return new MinecraftProfile(id, name);
            });
    }

    private CompletableFuture<ProfileResult> loadHypixel(MinecraftProfile profile, String apiKey) {
        CompletableFuture<JsonObject> playerFuture = loadHypixelObject(HYPIXEL_PLAYER_API, "uuid", profile.uuid(), apiKey);
        CompletableFuture<JsonObject> profilesFuture = loadHypixelObject(HYPIXEL_PROFILES_API, "uuid", profile.uuid(), apiKey);
        return playerFuture.thenCombine(profilesFuture, (playerRoot, profilesRoot) -> parse(profile, playerRoot, profilesRoot))
            .exceptionally(throwable -> ProfileResult.error(shortError(throwable)));
    }

    private CompletableFuture<JsonObject> loadHypixelObject(URI baseUri, String parameter, String value, String apiKey) {
        return loadHypixelObject(baseUri, parameter, value, apiKey, 1);
    }

    private CompletableFuture<JsonObject> loadHypixelObject(
        URI baseUri,
        String parameter,
        String value,
        String apiKey,
        int attempt
    ) {
        URI uri = URI.create(baseUri + "?" + parameter + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)
            + "&_kungFresh=" + System.currentTimeMillis());
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(12))
            .header("Accept", "application/json")
            .header("API-Key", apiKey)
            .header("Cache-Control", "no-cache, no-store, max-age=0")
            .header("Pragma", "no-cache")
            .header("User-Agent", "Kung-HypixelProfile")
            .GET()
            .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .handle((response, throwable) -> {
                if (throwable != null) {
                    if (attempt < API_ATTEMPTS) {
                        return retryHypixelObject(baseUri, parameter, value, apiKey, attempt);
                    }
                    return failedFuture(throwable);
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    IllegalStateException error =
                        new IllegalStateException("Hypixel returned HTTP " + response.statusCode());
                    if (shouldRetry(response.statusCode()) && attempt < API_ATTEMPTS) {
                        return retryHypixelObject(baseUri, parameter, value, apiKey, attempt);
                    }
                    return failedFuture(error);
                }
                try {
                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (!bool(root, "success")) {
                        throw new IllegalStateException("Hypixel returned success=false");
                    }
                    return CompletableFuture.completedFuture(root);
                } catch (RuntimeException exception) {
                    return failedFuture(exception);
                }
            })
            .thenCompose(future -> future);
    }

    private CompletableFuture<JsonObject> retryHypixelObject(
        URI baseUri,
        String parameter,
        String value,
        String apiKey,
        int previousAttempt
    ) {
        long delayMillis = 300L * previousAttempt;
        return CompletableFuture.supplyAsync(
                () -> null,
                CompletableFuture.delayedExecutor(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            )
            .thenCompose(ignored -> loadHypixelObject(baseUri, parameter, value, apiKey, previousAttempt + 1));
    }

    private static CompletableFuture<JsonObject> failedFuture(Throwable throwable) {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    private static boolean shouldRetry(int statusCode) {
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504 || statusCode >= 500;
    }

    private ProfileResult parse(MinecraftProfile minecraftProfile, JsonObject playerRoot, JsonObject profilesRoot) {
        int secrets = totalSecrets(playerRoot);
        JsonElement profilesElement = profilesRoot.get("profiles");
        if (profilesElement == null || !profilesElement.isJsonArray()) {
            return ProfileResult.error("Hypixel returned no SkyBlock profiles");
        }

        java.util.ArrayList<ProfileData> profiles = new java.util.ArrayList<>();
        int selectedIndex = 0;
        long bestLastSave = Long.MIN_VALUE;
        String memberKey = minecraftProfile.uuid().replace("-", "").toLowerCase(Locale.ROOT);
        for (JsonElement element : profilesElement.getAsJsonArray()) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject profileObject = element.getAsJsonObject();
            JsonObject member = objectMember(objectMember(profileObject, "members"), memberKey);
            if (member == null) {
                continue;
            }
            ProfileData profile = parseProfile(profileObject, member);
            int index = profiles.size();
            profiles.add(profile);
            long lastSave = (long) number(member, "last_save", 0.0);
            if (profile.selected()) {
                selectedIndex = index;
            } else if (profiles.size() == 1 || lastSave > bestLastSave) {
                bestLastSave = lastSave;
                selectedIndex = index;
            }
        }
        if (profiles.isEmpty()) {
            return ProfileResult.error("no SkyBlock profile found");
        }
        return ProfileResult.ok(new PlayerData(minecraftProfile.name(), profiles, selectedIndex), secrets);
    }

    private static int totalSecrets(JsonObject playerRoot) {
        return (int) number(objectMember(objectMember(playerRoot, "player"), "achievements"),
            "skyblock_treasure_hunter", -1.0);
    }

    private ProfileData parseProfile(JsonObject profileObject, JsonObject member) {
        JsonObject dungeons = objectMember(member, "dungeons");
        JsonObject dungeonTypes = objectMember(dungeons, "dungeon_types");
        JsonObject catacombs = objectMember(dungeonTypes, "catacombs");
        JsonObject playerClasses = objectMember(dungeons, "player_classes");
        EnumMap<DungeonClass, Double> classXp = new EnumMap<>(DungeonClass.class);
        EnumMap<DungeonClass, Integer> classPerks = new EnumMap<>(DungeonClass.class);
        for (DungeonClass dungeonClass : DungeonClass.values()) {
            JsonObject classObject = objectMember(playerClasses, classId(dungeonClass));
            classXp.put(dungeonClass, number(classObject, "experience", 0.0));
            classPerks.put(dungeonClass, 5);
        }
        return new ProfileData(
            string(profileObject, "profile_id", ""),
            string(profileObject, "cute_name", "Profile"),
            bool(profileObject, "selected"),
            number(catacombs, "experience", 0.0),
            classXp,
            classPerks
        );
    }

    private static String classId(DungeonClass dungeonClass) {
        return dungeonClass == DungeonClass.BERSERK ? "berserk" : dungeonClass.id();
    }

    public String statusMessage() {
        KungConfig config = KungConfig.get();
        if (!config.misc.hypixelApiEnabled()) {
            return config.misc.hypixelApiKey().isBlank()
                ? "Direct Hypixel: off, no key"
                : "Direct Hypixel: off, key set";
        }
        return config.misc.hypixelApiKey().isBlank()
            ? "Direct Hypixel: no key"
            : "Direct Hypixel: key set";
    }

    private static JsonObject objectMember(JsonObject object, String name) {
        if (object == null || !object.has(name) || !object.get(name).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(name);
    }

    private static String string(JsonObject object, String name, String fallback) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return fallback;
        }
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static double number(JsonObject object, String name, double fallback) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return fallback;
        }
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsDouble() : fallback;
    }

    private static boolean bool(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return false;
        }
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    private static boolean validUsername(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{3,16}");
    }

    private static String shortError(Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    public record ProfileResult(boolean success, PlayerData player, int secretsFound, String error) {
        static ProfileResult ok(PlayerData player, int secretsFound) {
            return new ProfileResult(true, player, secretsFound, "");
        }

        static ProfileResult error(String error) {
            return new ProfileResult(false, null, -1, error);
        }
    }

    public record SecretResult(boolean success, String name, int secretsFound, String error) {
        static SecretResult ok(String name, int secretsFound) {
            return new SecretResult(true, name, secretsFound, "");
        }

        static SecretResult error(String error) {
            return new SecretResult(false, "", -1, error);
        }
    }

    private record MinecraftProfile(String uuid, String name) {
    }
}
