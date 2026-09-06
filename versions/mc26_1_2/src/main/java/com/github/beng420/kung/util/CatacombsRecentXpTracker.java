package com.github.beng420.kung.util;

import com.github.beng420.kung.util.CatacombsAverageCalculator.DungeonClass;
import com.github.beng420.kung.util.CatacombsAverageCalculator.PlayerData;
import com.github.beng420.kung.util.CatacombsAverageCalculator.ProfileData;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;

public final class CatacombsRecentXpTracker {
    public static final CatacombsRecentXpTracker INSTANCE = new CatacombsRecentXpTracker();

    private static final Pattern CATACOMBS_XP_PATTERN =
        Pattern.compile("^\\+(?<xp>[\\d,]+) Catacombs Experience$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_XP_PATTERN =
        Pattern.compile("^\\+(?<xp>[\\d,]+) (?<class>Archer|Berserk|Healer|Mage|Tank) Experience(?<team> \\(Team Bonus\\))?$",
            Pattern.CASE_INSENSITIVE);
    private static final long DUPLICATE_LINE_WINDOW_MILLIS = 750L;

    private final EnumMap<DungeonClass, Double> observedClassXp = new EnumMap<>(DungeonClass.class);
    private final EnumMap<DungeonClass, Double> latestClassXpPerRun = new EnumMap<>(DungeonClass.class);
    private final Map<String, ApiAnchor> apiAnchors = new HashMap<>();
    private double observedCatacombsXp;
    private String lastRewardLine = "";
    private long lastRewardLineMillis;
    private boolean initialized;

    private CatacombsRecentXpTracker() {
        for (DungeonClass dungeonClass : DungeonClass.values()) {
            observedClassXp.put(dungeonClass, 0.0);
        }
    }

    public static void initializeClient() {
        INSTANCE.registerClientHooks();
    }

    public synchronized AdjustedPlayer applyToLocalPlayer(String requestedName, PlayerData player) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || player == null || player.profiles().isEmpty()) {
            return new AdjustedPlayer(player, "localXp=unavailable", Map.of());
        }

        String localName = normalize(client.player.getName().getString());
        String requested = normalize(requestedName);
        String resolved = normalize(player.name());
        if (!requested.equals(localName) && !resolved.equals(localName)) {
            return new AdjustedPlayer(player, "localXp=not-local", Map.of());
        }

        ProfileData profile = player.selectedProfile();
        XpTotals apiTotals = XpTotals.from(profile);
        XpTotals observedTotals = observedTotals();
        String key = localName + "|" + (profile.id().isBlank() ? profile.cuteName().toLowerCase(Locale.ROOT) : profile.id());
        ApiAnchor anchor = apiAnchors.get(key);
        if (anchor == null) {
            apiAnchors.put(key, new ApiAnchor(apiTotals, observedTotals));
            return new AdjustedPlayer(player, "localXp=anchored observed=" + observedTotals.summary()
                + " observedRunXp=" + classXpPerRunSummary(), currentClassXpPerRun());
        }

        XpTotals observedDelta = observedTotals.minus(anchor.observedTotals());
        XpTotals apiDelta = apiTotals.minus(anchor.apiTotals());
        XpTotals localDelta = observedDelta.minusPositive(apiDelta);
        if (!localDelta.hasPositiveValues()) {
            apiAnchors.put(key, new ApiAnchor(apiTotals, observedTotals));
            return new AdjustedPlayer(player, "localXp=caught-up observedDelta=" + observedDelta.summary()
                + " apiDelta=" + apiDelta.summary()
                + " observedRunXp=" + classXpPerRunSummary(), currentClassXpPerRun());
        }

        PlayerData adjustedPlayer = withAddedXp(player, profile, localDelta);
        KungDebugRecorder.event("ca50-local-xp", "applied player=" + player.name()
            + " profile=" + profile.cuteName()
            + " localDelta=" + localDelta.summary()
            + " observedDelta=" + observedDelta.summary()
            + " apiDelta=" + apiDelta.summary());
        return new AdjustedPlayer(adjustedPlayer, "localXp=applied localDelta=" + localDelta.summary()
            + " observedDelta=" + observedDelta.summary()
            + " apiDelta=" + apiDelta.summary()
            + " observedRunXp=" + classXpPerRunSummary(), currentClassXpPerRun());
    }

    private void registerClientHooks() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> observeMessage(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
            observeMessage(message.getString()));
    }

    private synchronized void observeMessage(String rawMessage) {
        String line = cleanLine(rawMessage);
        if (line.isBlank() || isDuplicate(line)) {
            return;
        }

        Matcher cataMatcher = CATACOMBS_XP_PATTERN.matcher(line);
        if (cataMatcher.matches()) {
            double xp = parseXp(cataMatcher.group("xp"));
            observedCatacombsXp += xp;
            KungDebugRecorder.event("ca50-local-xp", "observed catacombs=" + Math.round(xp)
                + " total=" + Math.round(observedCatacombsXp));
            return;
        }

        Matcher classMatcher = CLASS_XP_PATTERN.matcher(line);
        if (!classMatcher.matches()) {
            return;
        }
        DungeonClass dungeonClass = classFromLabel(classMatcher.group("class"));
        if (dungeonClass == null) {
            return;
        }
        double xp = parseXp(classMatcher.group("xp"));
        observedClassXp.put(dungeonClass, observedClassXp.getOrDefault(dungeonClass, 0.0) + xp);
        double xpPerSelectedRun = classMatcher.group("team") == null ? xp : xp * 4.0;
        latestClassXpPerRun.put(dungeonClass, xpPerSelectedRun);
        KungDebugRecorder.event("ca50-local-xp", "observed " + dungeonClass.id() + "=" + Math.round(xp)
            + " total=" + Math.round(observedClassXp.getOrDefault(dungeonClass, 0.0))
            + " selectedRunXp=" + Math.round(xpPerSelectedRun));
    }

    private boolean isDuplicate(String line) {
        long now = System.currentTimeMillis();
        if (line.equals(lastRewardLine) && now - lastRewardLineMillis <= DUPLICATE_LINE_WINDOW_MILLIS) {
            return true;
        }
        lastRewardLine = line;
        lastRewardLineMillis = now;
        return false;
    }

    private XpTotals observedTotals() {
        return new XpTotals(observedCatacombsXp, new EnumMap<>(observedClassXp));
    }

    private Map<DungeonClass, Double> currentClassXpPerRun() {
        EnumMap<DungeonClass, Double> values = new EnumMap<>(DungeonClass.class);
        for (DungeonClass dungeonClass : DungeonClass.values()) {
            double value = latestClassXpPerRun.getOrDefault(dungeonClass, 0.0);
            if (value > 0.0) {
                values.put(dungeonClass, value);
            }
        }
        return Map.copyOf(values);
    }

    private String classXpPerRunSummary() {
        StringBuilder builder = new StringBuilder();
        for (DungeonClass dungeonClass : DungeonClass.values()) {
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(dungeonClass.id())
                .append(':')
                .append(Math.round(latestClassXpPerRun.getOrDefault(dungeonClass, 0.0)));
        }
        return builder.toString();
    }

    private static PlayerData withAddedXp(PlayerData player, ProfileData selectedProfile, XpTotals delta) {
        java.util.ArrayList<ProfileData> profiles = new java.util.ArrayList<>(player.profiles());
        int selectedIndex = Math.clamp(player.selectedIndex(), 0, profiles.size() - 1);
        ProfileData original = profiles.get(selectedIndex);
        if (original != selectedProfile) {
            selectedProfile = original;
        }

        EnumMap<DungeonClass, Double> adjustedClassXp = new EnumMap<>(DungeonClass.class);
        for (DungeonClass dungeonClass : DungeonClass.values()) {
            adjustedClassXp.put(dungeonClass,
                selectedProfile.classXp().getOrDefault(dungeonClass, 0.0)
                    + delta.classXp().getOrDefault(dungeonClass, 0.0));
        }
        profiles.set(selectedIndex, new ProfileData(
            selectedProfile.id(),
            selectedProfile.cuteName(),
            selectedProfile.selected(),
            selectedProfile.cataXp() + delta.catacombsXp(),
            adjustedClassXp,
            selectedProfile.classPerks()
        ));
        return new PlayerData(player.name(), profiles, player.selectedIndex());
    }

    private static double parseXp(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(text.replace(",", ""));
    }

    private static DungeonClass classFromLabel(String label) {
        for (DungeonClass dungeonClass : DungeonClass.values()) {
            if (dungeonClass.label().equalsIgnoreCase(label)) {
                return dungeonClass;
            }
        }
        return null;
    }

    private static String cleanLine(String rawLine) {
        return rawLine == null ? "" : rawLine.replaceAll("\u00a7.", "").replaceAll("\\s+", " ").trim();
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public record AdjustedPlayer(PlayerData player, String debugDetails, Map<DungeonClass, Double> classXpPerRun) {
        public AdjustedPlayer {
            classXpPerRun = Map.copyOf(classXpPerRun);
        }
    }

    private record ApiAnchor(XpTotals apiTotals, XpTotals observedTotals) {
    }

    private record XpTotals(double catacombsXp, Map<DungeonClass, Double> classXp) {
        private XpTotals {
            classXp = Map.copyOf(classXp);
        }

        static XpTotals from(ProfileData profile) {
            return new XpTotals(profile.cataXp(), profile.classXp());
        }

        XpTotals minus(XpTotals other) {
            EnumMap<DungeonClass, Double> values = new EnumMap<>(DungeonClass.class);
            for (DungeonClass dungeonClass : DungeonClass.values()) {
                values.put(dungeonClass, classXp.getOrDefault(dungeonClass, 0.0)
                    - other.classXp().getOrDefault(dungeonClass, 0.0));
            }
            return new XpTotals(catacombsXp - other.catacombsXp(), values);
        }

        XpTotals minusPositive(XpTotals other) {
            EnumMap<DungeonClass, Double> values = new EnumMap<>(DungeonClass.class);
            for (DungeonClass dungeonClass : DungeonClass.values()) {
                values.put(dungeonClass, Math.max(0.0, classXp.getOrDefault(dungeonClass, 0.0)
                    - Math.max(0.0, other.classXp().getOrDefault(dungeonClass, 0.0))));
            }
            return new XpTotals(Math.max(0.0, catacombsXp - Math.max(0.0, other.catacombsXp())), values);
        }

        boolean hasPositiveValues() {
            if (catacombsXp > 0.0) {
                return true;
            }
            for (double value : classXp.values()) {
                if (value > 0.0) {
                    return true;
                }
            }
            return false;
        }

        String summary() {
            StringBuilder builder = new StringBuilder("cata:")
                .append(Math.round(catacombsXp));
            for (DungeonClass dungeonClass : DungeonClass.values()) {
                builder.append(',')
                    .append(dungeonClass.id())
                    .append(':')
                    .append(Math.round(classXp.getOrDefault(dungeonClass, 0.0)));
            }
            return builder.toString();
        }
    }
}
