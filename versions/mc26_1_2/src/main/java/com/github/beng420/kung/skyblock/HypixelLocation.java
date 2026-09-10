package com.github.beng420.kung.skyblock;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses actual location fields, never location mentions in social or combat text. */
public record HypixelLocation(Kind kind, String name) {
    public enum Kind { UNKNOWN, CATACOMBS, DUNGEON_HUB, KUUDRA, SKYBLOCK }
    public static final HypixelLocation UNKNOWN = new HypixelLocation(Kind.UNKNOWN, "");
    private static final Pattern FIELD = Pattern.compile("^(?:Area|Location|Dungeon)\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CATACOMBS = Pattern.compile("^(?:The )?Catacombs(?:\\s*\\((?:E|[FM]?[0-7])\\))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVER = Pattern.compile("\\b(?:mini\\d{1,5}|mega\\d{1,5}|m\\d{2,5})[a-z]{0,4}\\b", Pattern.CASE_INSENSITIVE);

    public static HypixelLocation parse(List<String> lines) {
        HypixelLocation result = UNKNOWN;
        for (String raw : lines) {
            String line = clean(raw);
            Matcher field = FIELD.matcher(line);
            boolean locationField = field.matches() || line.startsWith("⏣") || line.startsWith("ф");
            String name = field.matches() ? field.group(1).trim()
                : locationField ? line.substring(1).trim() : line;
            HypixelLocation candidate = named(name, locationField);
            // Explicit non-dungeon areas win over stale floor lines while the board updates.
            if (candidate.kind == Kind.KUUDRA || candidate.kind == Kind.DUNGEON_HUB) return candidate;
            if (candidate.kind == Kind.SKYBLOCK && locationField) return candidate;
            if (candidate.kind != Kind.UNKNOWN) result = candidate;
        }
        return result;
    }

    private static HypixelLocation named(String name, boolean locationField) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (CATACOMBS.matcher(name).matches() || HypixelDungeonFloor.fromLine(name).known()) {
            return new HypixelLocation(Kind.CATACOMBS, name);
        }
        if (lower.equals("dungeon hub")) return new HypixelLocation(Kind.DUNGEON_HUB, "Dungeon Hub");
        if (lower.equals("kuudra") || lower.matches("kuudra's hollow(?:\\s*\\(t[1-5]\\))?")) return new HypixelLocation(Kind.KUUDRA, "Kuudra's Hollow");
        if (locationField && !name.isBlank() && !lower.equals("unknown")) return new HypixelLocation(Kind.SKYBLOCK, name);
        return UNKNOWN;
    }

    public static String serverId(List<String> lines) {
        for (String raw : lines) {
            String line = clean(raw);
            if (!line.matches("(?i)^(?:\\d{1,2}/\\d{1,2}/\\d{2,4}.*|(?:Server|Server ID):.*|(?:mini|mega|m)\\d+[a-z]*)$")) continue;
            Matcher matcher = SERVER.matcher(line);
            if (matcher.find()) return matcher.group().toLowerCase(Locale.ROOT);
        }
        return "";
    }

    public static String clean(String text) {
        return text == null ? "" : text.replaceAll("§.", "").replaceAll("\\s+", " ").trim();
    }
}
