package com.github.beng420.kung.feature.garden;

import com.github.beng420.kung.skyblock.HypixelLocation;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Fresh sidebar dates determine Autumn; elected perks determine year-round Grand Feasts. */
public final class FeastContext {
    private static final Pattern DATE = Pattern.compile(
        "^(?:(Early|Late) )?(Spring|Summer|Autumn|Winter) ([1-9]|[12][0-9]|3[01])(?:st|nd|rd|th)?(?:,? (?:Year )?(\\d{1,6}))?$");
    private static final Pattern PROFILE = Pattern.compile("^(?:Profile|Profile Name): (\\w+)(?: .*)?$");
    private static final Pattern PROFILE_MESSAGE = Pattern.compile(
        "^(?:\\[\\d{2}:\\d{2}:\\d{2}\\] )?(?:You are (?:now )?playing on profile:|Your profile (?:was changed|has been changed) to:|Profile switched to:) (\\w+)[.!]?$");
    private static final Pattern PROFILE_ID = Pattern.compile(
        "^(?:\\[\\d{2}:\\d{2}:\\d{2}\\] )?Profile ID: ([a-fA-F0-9-]{36})$");
    // SkyBlock year zero; each of its 372 days lasts 20 real minutes.
    static final long YEAR_ZERO = 1_559_829_300_000L;
    static final long YEAR_MILLIS = 446_400_000L;
    private final LongSupplier clock;
    private int month = -1;
    private int previousMonth = -1;
    private int day = -1;
    private long year = -1;
    private String profile = "";
    private boolean profileFromMessage;

    public FeastContext() { this(System::currentTimeMillis); }
    FeastContext(LongSupplier clock) { this.clock = clock; }

    public record Event(FeastProgress.Kind kind, String key) {}

    /** Returns true when profile evidence invalidates a baseline from the previous profile. */
    public boolean observe(List<String> sidebar, List<String> visible) {
        for (String raw : sidebar) {
            var date = DATE.matcher(HypixelLocation.clean(raw));
            if (!date.matches()) continue;
            int season = switch (date.group(2)) {
                case "Spring" -> 0;
                case "Summer" -> 1;
                case "Autumn" -> 2;
                default -> 3;
            };
            int nextMonth = season * 3 + ("Early".equals(date.group(1)) ? 0 : "Late".equals(date.group(1)) ? 2 : 1);
            int nextDay = Integer.parseInt(date.group(3));
            boolean wrapped = nextMonth < previousMonth || nextMonth == previousMonth && nextDay < day;
            long nextYear = date.group(4) == null
                ? Math.max(Math.floorDiv(clock.getAsLong() - YEAR_ZERO, YEAR_MILLIS), year + (wrapped ? 1 : 0))
                : Long.parseLong(date.group(4));
            month = nextMonth;
            previousMonth = nextMonth;
            day = nextDay;
            year = nextYear;
            break;
        }
        for (String raw : visible) {
            var found = PROFILE.matcher(HypixelLocation.clean(raw));
            if (!found.matches()) continue;
            String next = found.group(1);
            if (profileFromMessage && !profile.equals(next)) continue;
            boolean changed = !profile.isBlank() && !profile.equals(next);
            profile = next;
            return changed;
        }
        return false;
    }

    public Event event(boolean grandFeast, long electionYear) {
        if (grandFeast) return new Event(FeastProgress.Kind.GRAND, "grand:" + electionYear);
        if (month >= 6 && month <= 8) return new Event(FeastProgress.Kind.HARVEST, "harvest:" + year);
        return null;
    }

    /** Called for each new instance; an old world's Autumn row must never enable this HUD. */
    public void worldChanged() {
        month = -1;
        profileFromMessage = false;
    }

    public boolean hasDate() { return month >= 0; }

    public void reset() {
        worldChanged();
        previousMonth = -1;
        day = -1;
        year = -1;
        profile = "";
    }

    public static boolean garden(String instance, List<String> visible) {
        if (isGardenName(instance)) return true;
        // The sidebar can name a plot while the tab's Area still identifies the Garden.
        return visible.stream().map(HypixelLocation::clean)
            .anyMatch(line -> line.equals("Area: Garden") || line.equals("Area: The Garden"));
    }

    private static boolean isGardenName(String name) {
        String clean = HypixelLocation.clean(name);
        return clean.equals("Garden") || clean.equals("The Garden");
    }

    public static boolean profileMessage(String message) {
        return PROFILE_MESSAGE.matcher(HypixelLocation.clean(message)).matches();
    }

    public String profile() { return profile; }

    public void observeProfileMessage(String message) {
        var match = PROFILE_MESSAGE.matcher(HypixelLocation.clean(message));
        if (match.matches()) {
            profile = match.group(1);
            profileFromMessage = true;
        }
    }

    static String profileId(String message) {
        var match = PROFILE_ID.matcher(HypixelLocation.clean(message));
        if (!match.matches()) return null;
        try { return java.util.UUID.fromString(match.group(1)).toString(); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    static boolean hubFarm(String instance, List<String> visible) {
        boolean hub = HypixelLocation.clean(instance).equals("Hub")
            || visible.stream().map(HypixelLocation::clean).anyMatch(line -> line.equals("Area: Hub"));
        if (!hub) return false;
        return visible.stream().map(HypixelLocation::clean).anyMatch(line -> {
            String area = line.replaceFirst("^(?:Area|Location):\\s*", "")
                .replaceFirst("^[⏣ф\\p{Co}]+\\s*", "");
            return !area.equals(line) && (area.equals("Farm") || area.equals("Wheat Farm")
                || area.equals("Farmhouse") || area.equals("Communal Stew"));
        });
    }
}
