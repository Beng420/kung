package com.github.beng420.kung.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;

public final class KungDebugRecorder {
    private static final int MAX_EVENTS = 2500;
    private static final int MAX_EVENT_LENGTH = 900;
    private static final long DEFAULT_DEDUPE_MILLIS = 15_000L;
    private static final DateTimeFormatter CLOCK_FORMAT =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FILE_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneId.systemDefault());
    private static final ArrayDeque<String> EVENTS = new ArrayDeque<>(MAX_EVENTS);
    private static final Map<String, AreaState> AREA_STATES = new HashMap<>();

    private static long sequence;
    private static long suppressedTotal;

    private KungDebugRecorder() {
    }

    public static void event(String area, String message) {
        String cleanArea = sanitize(area);
        if (cleanArea.isBlank()) {
            cleanArea = "general";
        }
        String cleanMessage = sanitize(message);
        if (cleanMessage.length() > MAX_EVENT_LENGTH) {
            cleanMessage = cleanMessage.substring(0, MAX_EVENT_LENGTH) + "...";
        }

        long nowMillis = System.currentTimeMillis();
        synchronized (EVENTS) {
            AreaPolicy policy = policyFor(cleanArea, cleanMessage);
            AreaState state = AREA_STATES.computeIfAbsent(cleanArea, ignored -> new AreaState());
            state.seen++;

            String fingerprint = fingerprint(cleanArea, cleanMessage, policy);
            if (!policy.important()
                && fingerprint.equals(state.lastFingerprint)
                && nowMillis - state.lastRecordedAtMillis <= policy.dedupeMillis()) {
                state.duplicatesSuppressed++;
                state.suppressed++;
                suppressedTotal++;
                return;
            }
            if (!policy.important() && policy.windowLimit() > 0 && rateLimited(state, policy, nowMillis)) {
                state.rateSuppressed++;
                state.suppressed++;
                suppressedTotal++;
                return;
            }

            sequence++;
            EVENTS.addLast(String.format(
                Locale.ROOT,
                "%06d %s [%s] %s",
                sequence,
                CLOCK_FORMAT.format(Instant.now()),
                cleanArea,
                cleanMessage
            ));
            state.recorded++;
            state.lastFingerprint = fingerprint;
            state.lastRecordedAtMillis = nowMillis;
            while (EVENTS.size() > MAX_EVENTS) {
                EVENTS.removeFirst();
            }
        }
    }

    public static void clear() {
        synchronized (EVENTS) {
            EVENTS.clear();
            AREA_STATES.clear();
            sequence = 0L;
            suppressedTotal = 0L;
        }
        event("log", "cleared");
    }

    public static String dump() {
        return dump(MAX_EVENTS);
    }

    public static String dump(int maxLines) {
        int limit = Math.max(1, maxLines);
        List<String> snapshot;
        synchronized (EVENTS) {
            snapshot = new ArrayList<>(EVENTS);
        }

        int from = Math.max(0, snapshot.size() - limit);
        StringBuilder builder = new StringBuilder(snapshot.size() * 96);
        builder.append("Kung Trace\n");
        builder.append("created=").append(Instant.now()).append('\n');
        builder.append("entries=").append(snapshot.size() - from).append('/').append(snapshot.size()).append('\n');
        builder.append("storedLimit=").append(MAX_EVENTS).append('\n');
        builder.append("suppressed=").append(suppressedTotal).append('\n');
        builder.append("focus=door-title,map-change,map-check,map-discovery,map-topology,mimic-esp,player-markers,player-slots,dungeon-state,context-state\n");
        appendAreaSummary(builder);
        builder.append('\n');
        for (int index = from; index < snapshot.size(); index++) {
            builder.append(snapshot.get(index)).append('\n');
        }
        return builder.toString();
    }

    public static Path saveToFile(Minecraft client) throws IOException {
        Path gameDirectory = client == null
            ? Path.of(".")
            : client.gameDirectory.toPath();
        Path directory = gameDirectory.resolve("kung-debug");
        Files.createDirectories(directory);
        Path path = directory.resolve("kung-trace-" + FILE_FORMAT.format(Instant.now()) + ".log");
        Files.writeString(path, dump(), StandardCharsets.UTF_8);
        return path;
    }

    public static String compact(String value) {
        return sanitize(value).replace(" | ", " / ");
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\u00a7.", "")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static void appendAreaSummary(StringBuilder builder) {
        synchronized (EVENTS) {
            if (AREA_STATES.isEmpty()) {
                return;
            }

            List<String> areas = new ArrayList<>(AREA_STATES.keySet());
            Collections.sort(areas);
            builder.append("areas=");
            boolean first = true;
            for (String area : areas) {
                AreaState state = AREA_STATES.get(area);
                if (state == null || state.seen <= 0L) {
                    continue;
                }
                if (!first) {
                    builder.append("; ");
                }
                first = false;
                builder.append(area)
                    .append(" seen=").append(state.seen)
                    .append(" kept=").append(state.recorded);
                if (state.suppressed > 0L) {
                    builder.append(" suppressed=").append(state.suppressed);
                    if (state.duplicatesSuppressed > 0L) {
                        builder.append(" duplicate=").append(state.duplicatesSuppressed);
                    }
                    if (state.rateSuppressed > 0L) {
                        builder.append(" rate=").append(state.rateSuppressed);
                    }
                }
            }
            builder.append('\n');
        }
    }

    private static boolean rateLimited(AreaState state, AreaPolicy policy, long nowMillis) {
        if (state.windowStartedAtMillis <= 0L
            || nowMillis - state.windowStartedAtMillis >= policy.windowMillis()) {
            state.windowStartedAtMillis = nowMillis;
            state.windowRecorded = 0;
        }
        if (state.windowRecorded >= policy.windowLimit()) {
            return true;
        }
        state.windowRecorded++;
        return false;
    }

    private static String fingerprint(String area, String message, AreaPolicy policy) {
        String text = policy.similarDedupe()
            ? message.replaceAll("-?\\d+(?:\\.\\d+)?", "#")
            : message;
        return area + '\n' + text;
    }

    private static AreaPolicy policyFor(String area, String message) {
        boolean important = important(area, message);
        return switch (area) {
            case "packet" -> new AreaPolicy(12, 60_000L, DEFAULT_DEDUPE_MILLIS, true, important);
            case "message" -> new AreaPolicy(20, 60_000L, DEFAULT_DEDUPE_MILLIS, false, important);
            case "screen" -> new AreaPolicy(12, 60_000L, DEFAULT_DEDUPE_MILLIS, true, important);
            case "chat-filter", "chat-command", "chat-name" ->
                new AreaPolicy(20, 60_000L, DEFAULT_DEDUPE_MILLIS, false, important);
            case "loadouts-auto-close" -> new AreaPolicy(40, 60_000L, DEFAULT_DEDUPE_MILLIS, true, important);
            case "map-change" -> new AreaPolicy(120, 60_000L, 3_000L, false, important);
            case "map-check" -> new AreaPolicy(30, 60_000L, DEFAULT_DEDUPE_MILLIS, false, important);
            case "mimic-esp" -> new AreaPolicy(40, 60_000L, DEFAULT_DEDUPE_MILLIS, true, important);
            case "map-discovery", "map-topology" ->
                new AreaPolicy(80, 60_000L, DEFAULT_DEDUPE_MILLIS, false, important);
            case "player-markers" -> new AreaPolicy(30, 60_000L, DEFAULT_DEDUPE_MILLIS, true, important);
            case "player-slots" -> new AreaPolicy(60, 60_000L, DEFAULT_DEDUPE_MILLIS, false, important);
            case "context-state", "dungeon-state" ->
                new AreaPolicy(60, 60_000L, DEFAULT_DEDUPE_MILLIS, false, important);
            case "door-title" -> new AreaPolicy(120, 60_000L, 2_000L, false, true);
            default -> new AreaPolicy(0, 60_000L, DEFAULT_DEDUPE_MILLIS, false, important);
        };
    }

    private static boolean important(String area, String message) {
        String haystack = (area + " " + message).toLowerCase(Locale.ROOT);
        return haystack.contains("error")
            || haystack.contains("exception")
            || haystack.contains("failed")
            || haystack.contains("crash")
            || haystack.contains("starting in 1 second")
            || haystack.contains("catacombs")
            || haystack.contains("blood")
            || haystack.contains("wither")
            || haystack.contains("secret")
            || haystack.contains("mimic")
            || haystack.contains("crypt")
            || haystack.contains("death")
            || haystack.contains(" died")
            || haystack.contains("score")
            || haystack.contains("run-start")
            || haystack.contains("run-finished")
            || haystack.contains("world-change")
            || haystack.contains("left dungeons")
            || haystack.contains("entered dungeons")
            || haystack.contains("entered the catacombs");
    }

    private record AreaPolicy(
        int windowLimit,
        long windowMillis,
        long dedupeMillis,
        boolean similarDedupe,
        boolean important
    ) {
    }

    private static final class AreaState {
        long seen;
        long recorded;
        long suppressed;
        long duplicatesSuppressed;
        long rateSuppressed;
        long windowStartedAtMillis;
        int windowRecorded;
        String lastFingerprint = "";
        long lastRecordedAtMillis = Long.MIN_VALUE;
    }
}
