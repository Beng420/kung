package com.github.beng420.kung.util;

import com.github.beng420.kung.config.KungConfig;

import com.github.beng420.kung.skyblock.SkyBlockMayorTracker;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class CatacombsAverageCalculator {
    private static final URI PIXELSTATS_DUNGEON_API =
        URI.create("https://www.pixelstats.net/api/calc/dungeon");
    private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private static final DungeonClass[] CLASSES = DungeonClass.values();
    private static final int API_ATTEMPTS = 3;
    private static final double[] HECATOMB_BONUSES = {
        0.0, 0.0056, 0.0072, 0.0088, 0.0104, 0.012, 0.0136, 0.0152, 0.0168, 0.0184, 0.02
    };
    private static final long[] CATACOMBS_XP = {
        0L, 50L, 125L, 235L, 395L, 625L, 955L, 1_425L, 2_095L, 3_045L, 4_385L, 6_275L,
        8_940L, 12_700L, 17_960L, 25_340L, 35_640L, 50_040L, 70_040L, 97_640L, 135_640L,
        188_140L, 259_640L, 356_640L, 488_640L, 668_640L, 911_640L, 1_239_640L, 1_684_640L,
        2_284_640L, 3_084_640L, 4_149_640L, 5_559_640L, 7_459_640L, 9_959_640L, 13_259_640L,
        17_559_640L, 23_159_640L, 30_359_640L, 39_559_640L, 51_559_640L, 66_559_640L,
        85_559_640L, 109_559_640L, 139_559_640L, 177_559_640L, 225_559_640L, 285_559_640L,
        360_559_640L, 453_559_640L, 569_809_640L
    };

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    public CompletableFuture<Result> calculateAsync(String username) {
        return calculateAsync(username, Goal.CLASS_AVERAGE_50);
    }

    public CompletableFuture<Result> calculateAsync(String username, Goal goal) {
        return calculateAsync(username, goal, true);
    }

    public CompletableFuture<Result> calculateAsync(String username, Goal goal, boolean useLocalObservedXp) {
        String normalized = username == null ? "" : username.trim();
        if (!validUsername(normalized)) {
            return CompletableFuture.completedFuture(Result.error("invalid username"));
        }

        if (KungConfig.get().misc.directHypixelApiEnabled()) {
            return HypixelSkyBlockProfileClient.INSTANCE.loadPlayer(normalized)
                .thenCompose(result -> {
                    if (result.success()) {
                        return CompletableFuture.completedFuture(calculate(
                            result.player(),
                            "source=hypixel secrets=" + result.secretsFound(),
                            normalized,
                            goal,
                            useLocalObservedXp
                        ));
                    }
                    return loadPixelStats(normalized, goal, useLocalObservedXp, "hypixelError=" + result.error());
                });
        }

        return loadPixelStats(normalized, goal, useLocalObservedXp, "hypixel=not-configured");
    }

    private CompletableFuture<Result> loadPixelStats(
        String normalized,
        Goal goal,
        boolean useLocalObservedXp,
        String fallbackDebug
    ) {
        return loadPixelStats(normalized, goal, useLocalObservedXp, fallbackDebug, 1);
    }

    private CompletableFuture<Result> loadPixelStats(
        String normalized,
        Goal goal,
        boolean useLocalObservedXp,
        String fallbackDebug,
        int attempt
    ) {
        long started = System.currentTimeMillis();
        URI requestUri = URI.create(PIXELSTATS_DUNGEON_API
            + "?username="
            + URLEncoder.encode(normalized, StandardCharsets.UTF_8)
            + "&_kungFresh="
            + started);
        HttpRequest request = HttpRequest.newBuilder(requestUri)
            .timeout(Duration.ofSeconds(12))
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache, no-store, max-age=0")
            .header("Pragma", "no-cache")
            .header("User-Agent", "Kung-CA50-ChatCommand")
            .GET()
            .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .handle((response, throwable) -> {
                long elapsed = System.currentTimeMillis() - started;
                if (throwable != null) {
                    if (attempt < API_ATTEMPTS) {
                        return retryPixelStats(normalized, goal, useLocalObservedXp, fallbackDebug, attempt);
                    }
                    return CompletableFuture.completedFuture(Result.error(shortError(throwable)));
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String debug = httpDebug(response, elapsed, normalized) + " attempt=" + attempt;
                    if (shouldRetry(response.statusCode()) && attempt < API_ATTEMPTS) {
                        return retryPixelStats(normalized, goal, useLocalObservedXp, fallbackDebug + " " + debug, attempt);
                    }
                    return CompletableFuture.completedFuture(Result.error("api returned HTTP " + response.statusCode(), debug));
                }
                return CompletableFuture.completedFuture(calculate(parsePlayer(response.body()),
                    fallbackDebug + " " + httpDebug(response, elapsed, normalized),
                    normalized,
                    goal,
                    useLocalObservedXp));
            })
            .thenCompose(future -> future);
    }

    private CompletableFuture<Result> retryPixelStats(
        String normalized,
        Goal goal,
        boolean useLocalObservedXp,
        String fallbackDebug,
        int previousAttempt
    ) {
        long delayMillis = 300L * previousAttempt;
        return CompletableFuture.supplyAsync(
                () -> null,
                CompletableFuture.delayedExecutor(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            )
            .thenCompose(ignored -> loadPixelStats(normalized, goal, useLocalObservedXp, fallbackDebug, previousAttempt + 1));
    }

    private static boolean shouldRetry(int statusCode) {
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504 || statusCode >= 500;
    }

    public Result calculate(PlayerData player) {
        return calculate(player, "", "", Goal.CLASS_AVERAGE_50, true);
    }

    private Result calculate(PlayerData player, String requestDebug, String requestedName, Goal goal, boolean useLocalObservedXp) {
        if (player == null || player.profiles().isEmpty()) {
            return Result.error("no SkyBlock profile found");
        }
        CatacombsRecentXpTracker.AdjustedPlayer adjusted = useLocalObservedXp
            ? CatacombsRecentXpTracker.INSTANCE.applyToLocalPlayer(requestedName, player)
            : new CatacombsRecentXpTracker.AdjustedPlayer(player, "localXp=disabled", Map.of());
        player = adjusted.player();
        ProfileData profile = player.selectedProfile();
        EnumMap<DungeonClass, Double> classXpPerRun = classXpPerRun(profile.classPerks());
        for (Map.Entry<DungeonClass, Double> entry : adjusted.classXpPerRun().entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0.0) {
                classXpPerRun.put(entry.getKey(), entry.getValue());
            }
        }
        Breakdown breakdown = calculateBreakdown(profile.classXp(), classXpPerRun);
        long cataXpPerRun = catacombsXpPerRun();
        long cataRuns = runsToCatacombs(profile, cataXpPerRun);
        String message = goal == Goal.CATACOMBS_50
            ? catacombsSummaryLine(player.name(), cataRuns)
            : classAverageSummaryLine(player.name(), breakdown);
        return Result.ok(message,
            resultDebug(player, profile, breakdown, cataRuns, cataXpPerRun, classXpPerRun, requestDebug, adjusted.debugDetails(), goal));
    }

    public PlayerData parsePlayer(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        String name = string(root, "name", "");
        java.util.ArrayList<ProfileData> profiles = new java.util.ArrayList<>();
        JsonElement profilesElement = root.get("profiles");
        if (profilesElement != null && profilesElement.isJsonArray()) {
            for (JsonElement element : profilesElement.getAsJsonArray()) {
                if (element != null && element.isJsonObject()) {
                    profiles.add(parseProfile(element.getAsJsonObject()));
                }
            }
        }
        if (profiles.isEmpty()) {
            throw new IllegalArgumentException("no profiles");
        }
        int selectedIndex = bestProfileIndex(profiles);
        for (int index = 0; index < profiles.size(); index++) {
            if (profiles.get(index).selected()) {
                selectedIndex = index;
                break;
            }
        }
        return new PlayerData(name, profiles, selectedIndex);
    }

    private ProfileData parseProfile(JsonObject object) {
        JsonObject data = objectMember(object, "data");
        EnumMap<DungeonClass, Double> classXp = new EnumMap<>(DungeonClass.class);
        EnumMap<DungeonClass, Integer> perks = new EnumMap<>(DungeonClass.class);
        JsonObject classXpObject = objectMember(data, "classXp");
        JsonObject perksObject = objectMember(data, "classPerks");
        for (DungeonClass dungeonClass : CLASSES) {
            classXp.put(dungeonClass, number(classXpObject, dungeonClass.id(), 0.0));
            perks.put(dungeonClass, (int) Math.round(number(perksObject, dungeonClass.id(), 0.0)));
        }
        return new ProfileData(
            string(object, "id", ""),
            string(object, "cuteName", "Profile"),
            bool(object, "selected"),
            number(data, "cataXp", 0.0),
            classXp,
            perks
        );
    }

    private EnumMap<DungeonClass, Double> classXpPerRun(Map<DungeonClass, Integer> classPerks) {
        double baseXp = 300_000.0;
        double hecatomb = HECATOMB_BONUSES[10];
        double scarfBonus = 0.06;
        double graduateBonus = 0.20;
        double mayorMultiplier = SkyBlockMayorTracker.INSTANCE.catacombsXpMultiplier();
        EnumMap<DungeonClass, Double> classXpPerRun = new EnumMap<>(DungeonClass.class);
        for (DungeonClass dungeonClass : CLASSES) {
            double perk = Math.clamp(classPerks.getOrDefault(dungeonClass, 0), 0, 5) * 0.02;
            classXpPerRun.put(dungeonClass, baseXp * (1.0 + 2.0 * hecatomb + perk + scarfBonus + graduateBonus)
                * mayorMultiplier);
        }
        return classXpPerRun;
    }

    private long catacombsXpPerRun() {
        double baseXp = 300_000.0;
        int maxRuns = 26;
        double hecatomb = HECATOMB_BONUSES[10];
        double mayorMultiplier = SkyBlockMayorTracker.INSTANCE.catacombsXpMultiplier();
        double cataMultiplier;
        if (mayorMultiplier > 1.0) {
            cataMultiplier = 0.95 + (mayorMultiplier - 1.0 + (maxRuns - 1) / 100.0)
                + 0.1 + hecatomb + (maxRuns - 1) * (0.024 + hecatomb / 50.0);
        } else {
            cataMultiplier = 0.95 + 0.1 + hecatomb + (maxRuns - 1) * (0.024 + hecatomb / 50.0);
        }
        return (long) Math.ceil(baseXp * cataMultiplier);
    }

    private long runsToCatacombs(ProfileData profile, long cataXpPerRun) {
        double remaining = xpForLevel(50) - profile.cataXp();
        return remaining <= 0.0 ? 0L : (long) Math.ceil(remaining / Math.max(1L, cataXpPerRun));
    }

    private Breakdown calculateBreakdown(
        Map<DungeonClass, Double> currentClassXp,
        Map<DungeonClass, Double> classXpPerRun
    ) {
        EnumMap<DungeonClass, Double> remaining = new EnumMap<>(DungeonClass.class);
        EnumMap<DungeonClass, Long> perClass = new EnumMap<>(DungeonClass.class);
        double totalRemaining = 0.0;
        for (DungeonClass dungeonClass : CLASSES) {
            double xp = Math.max(0.0, xpForLevel(50) - currentClassXp.getOrDefault(dungeonClass, 0.0));
            remaining.put(dungeonClass, xp);
            perClass.put(dungeonClass, 0L);
            totalRemaining += xp;
        }
        if (totalRemaining <= 0.0) {
            return new Breakdown(0L, perClass);
        }

        for (DungeonClass dungeonClass : CLASSES) {
            if (remaining.get(dungeonClass) > 0.0 && classXpPerRun.getOrDefault(dungeonClass, 0.0) <= 0.0) {
                for (DungeonClass classKey : CLASSES) {
                    perClass.put(classKey, remaining.get(classKey) > 0.0 ? Long.MAX_VALUE : 0L);
                }
                return new Breakdown(Long.MAX_VALUE, perClass);
            }
        }

        long low = 0L;
        long high = 0L;
        for (DungeonClass dungeonClass : CLASSES) {
            double xpPerRun = classXpPerRun.getOrDefault(dungeonClass, 0.0);
            if (xpPerRun > 0.0) {
                high += (long) Math.ceil(remaining.get(dungeonClass) / xpPerRun);
            }
        }
        high = Math.max(1L, high);
        while (requiredRunCount(remaining, classXpPerRun, high) > high && high < Long.MAX_VALUE / 2L) {
            high *= 2L;
        }

        while (low < high) {
            long mid = low + (high - low) / 2L;
            if (requiredRunCount(remaining, classXpPerRun, mid) <= mid) {
                high = mid;
            } else {
                low = mid + 1L;
            }
        }

        perClass = requiredRunsForTotal(remaining, classXpPerRun, low);
        long assigned = 0L;
        for (long runs : perClass.values()) {
            assigned += runs;
        }
        while (assigned < low) {
            DungeonClass target = extraRunTarget(remaining, classXpPerRun, perClass);
            perClass.put(target, perClass.get(target) + 1L);
            assigned++;
        }
        return new Breakdown(low, perClass);
    }

    private long requiredRunCount(
        Map<DungeonClass, Double> remaining,
        Map<DungeonClass, Double> classXpPerRun,
        long totalRuns
    ) {
        long total = 0L;
        for (long runs : requiredRunsForTotal(remaining, classXpPerRun, totalRuns).values()) {
            total += runs;
        }
        return total;
    }

    private EnumMap<DungeonClass, Long> requiredRunsForTotal(
        Map<DungeonClass, Double> remaining,
        Map<DungeonClass, Double> classXpPerRun,
        long totalRuns
    ) {
        EnumMap<DungeonClass, Long> runs = new EnumMap<>(DungeonClass.class);
        for (DungeonClass dungeonClass : CLASSES) {
            double xpPerRun = classXpPerRun.getOrDefault(dungeonClass, 0.0);
            double xpLeftAfterTeamBonus = remaining.getOrDefault(dungeonClass, 0.0) - xpPerRun * totalRuns / 4.0;
            long selectedRuns = xpLeftAfterTeamBonus <= 0.0001 || xpPerRun <= 0.0
                ? 0L
                : (long) Math.ceil(xpLeftAfterTeamBonus / (xpPerRun * 0.75) - 0.0000001);
            runs.put(dungeonClass, Math.max(0L, selectedRuns));
        }
        return runs;
    }

    private DungeonClass extraRunTarget(
        Map<DungeonClass, Double> remaining,
        Map<DungeonClass, Double> classXpPerRun,
        Map<DungeonClass, Long> perClass
    ) {
        boolean hasRequiredClass = false;
        for (long runs : perClass.values()) {
            if (runs > 0L) {
                hasRequiredClass = true;
                break;
            }
        }

        DungeonClass best = CLASSES[0];
        double bestScore = Double.NEGATIVE_INFINITY;
        for (DungeonClass dungeonClass : CLASSES) {
            if (hasRequiredClass && perClass.getOrDefault(dungeonClass, 0L) <= 0L) {
                continue;
            }
            double xpPerRun = classXpPerRun.getOrDefault(dungeonClass, 1.0);
            double score = remaining.getOrDefault(dungeonClass, 0.0) / Math.max(1.0, xpPerRun);
            if (score > bestScore) {
                bestScore = score;
                best = dungeonClass;
            }
        }
        return best;
    }

    private String classAverageSummaryLine(String playerName, Breakdown breakdown) {
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        for (DungeonClass dungeonClass : CLASSES) {
            long runs = breakdown.perClass(dungeonClass);
            if (runs > 0L) {
                parts.add(format(runs) + " " + dungeonClass.label());
            }
        }
        String classPart = parts.isEmpty() ? "0 class-specific runs" : String.join(", ", parts);
        return "It will take " + format(breakdown.total()) + " M7 runs for " + playerName
            + " to reach Class Average 50 (" + classPart + ")";
    }

    private String catacombsSummaryLine(String playerName, long runs) {
        return "It will take " + format(runs) + " M7 runs for " + playerName + " to reach Catacombs 50";
    }

    private static int bestProfileIndex(List<ProfileData> profiles) {
        int bestIndex = 0;
        double bestXp = -1.0;
        for (int index = 0; index < profiles.size(); index++) {
            double total = profiles.get(index).totalDungeonXp();
            if (total > bestXp) {
                bestXp = total;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static String httpDebug(HttpResponse<?> response, long elapsedMillis, String requestedName) {
        return "requested="
            + requestedName
            + " status="
            + response.statusCode()
            + " ms="
            + elapsedMillis
            + " age="
            + header(response, "age")
            + " cache="
            + header(response, "cache-control")
            + " cf="
            + header(response, "cf-cache-status");
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse("-");
    }

    private static String resultDebug(
        PlayerData player,
        ProfileData profile,
        Breakdown breakdown,
        long cataRuns,
        long cataXpPerRun,
        Map<DungeonClass, Double> classXpPerRun,
        String requestDebug,
        String adjustmentDebug,
        Goal goal
    ) {
        StringBuilder builder = new StringBuilder();
        if (requestDebug != null && !requestDebug.isBlank()) {
            builder.append(requestDebug).append(' ');
        }
        if (adjustmentDebug != null && !adjustmentDebug.isBlank()) {
            builder.append(adjustmentDebug).append(' ');
        }
        builder.append("goal=").append(goal.id())
            .append(" at=").append(Instant.now())
            .append(" player=").append(player.name())
            .append(" profile=").append(profile.cuteName())
            .append(" selected=").append(profile.selected())
            .append(" profileId=").append(profile.id().isBlank() ? "-" : profile.id())
            .append(" cataXp=").append(Math.round(profile.cataXp()))
            .append(" classXp=");
        for (DungeonClass dungeonClass : CLASSES) {
            builder.append(dungeonClass.id()).append(':').append(Math.round(profile.classXp().getOrDefault(dungeonClass, 0.0))).append(',');
        }
        builder.append(" perks=");
        for (DungeonClass dungeonClass : CLASSES) {
            builder.append(dungeonClass.id()).append(':').append(profile.classPerks().getOrDefault(dungeonClass, 0)).append(',');
        }
        double averageClassXpPerRun = 0.0;
        for (DungeonClass dungeonClass : CLASSES) {
            averageClassXpPerRun += classXpPerRun.getOrDefault(dungeonClass, 0.0);
        }
        averageClassXpPerRun /= CLASSES.length;
        builder.append(" mayor=").append(SkyBlockMayorTracker.INSTANCE.catacombsXpBoostLabel())
            .append(" mayorMultiplier=").append(String.format(Locale.ROOT, "%.3f", SkyBlockMayorTracker.INSTANCE.catacombsXpMultiplier()))
            .append(" cataXpPerRun=").append(cataXpPerRun)
            .append(" cataRuns=").append(cataRuns)
            .append(" avgClassXpPerRun=").append(Math.round(averageClassXpPerRun))
            .append(" totalRuns=").append(breakdown.total());
        return builder.toString();
    }

    private static long xpForLevel(int level) {
        return CATACOMBS_XP[Math.clamp(level, 0, CATACOMBS_XP.length - 1)];
    }

    private static String format(long value) {
        return INTEGER_FORMAT.format(value);
    }

    private static JsonObject objectMember(JsonObject object, String name) {
        if (object == null || !object.has(name)) {
            return null;
        }
        JsonElement value = object.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
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

    public enum DungeonClass {
        ARCHER("archer", "Archer"),
        BERSERK("berserk", "Berserk"),
        HEALER("healer", "Healer"),
        MAGE("mage", "Mage"),
        TANK("tank", "Tank");

        private final String id;
        private final String label;

        DungeonClass(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }
    }

    public enum Goal {
        CLASS_AVERAGE_50("ca50"),
        CATACOMBS_50("c50");

        private final String id;

        Goal(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Result(boolean success, String message, String debugDetails) {
        static Result ok(String message) {
            return ok(message, "");
        }

        static Result ok(String message, String debugDetails) {
            return new Result(true, message, debugDetails);
        }

        static Result error(String message) {
            return error(message, "");
        }

        static Result error(String message, String debugDetails) {
            return new Result(false, message, debugDetails);
        }
    }

    public record PlayerData(String name, List<ProfileData> profiles, int selectedIndex) {
        public PlayerData {
            profiles = List.copyOf(profiles);
        }

        ProfileData selectedProfile() {
            return profiles.get(Math.clamp(selectedIndex, 0, profiles.size() - 1));
        }
    }

    public record ProfileData(
        String id,
        String cuteName,
        boolean selected,
        double cataXp,
        Map<DungeonClass, Double> classXp,
        Map<DungeonClass, Integer> classPerks
    ) {
        public ProfileData {
            classXp = Map.copyOf(classXp);
            classPerks = Map.copyOf(classPerks);
        }

        double totalDungeonXp() {
            double total = cataXp;
            for (double xp : classXp.values()) {
                total += xp;
            }
            return total;
        }
    }

    private record Breakdown(long total, Map<DungeonClass, Long> perClass) {
        long perClass(DungeonClass dungeonClass) {
            return perClass.getOrDefault(dungeonClass, 0L);
        }
    }
}
