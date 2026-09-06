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
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;

public final class KungDebugRecorder {
    private static final int MAX_EVENTS = 50000;
    private static final int MAX_EVENT_LENGTH = 1400;
    private static final DateTimeFormatter CLOCK_FORMAT =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FILE_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneId.systemDefault());
    private static final ArrayDeque<String> EVENTS = new ArrayDeque<>(MAX_EVENTS);

    private static long sequence;

    private KungDebugRecorder() {
    }

    public static void event(String area, String message) {
        String cleanArea = sanitize(area);
        String cleanMessage = sanitize(message);
        if (cleanMessage.length() > MAX_EVENT_LENGTH) {
            cleanMessage = cleanMessage.substring(0, MAX_EVENT_LENGTH) + "...";
        }

        synchronized (EVENTS) {
            sequence++;
            EVENTS.addLast(String.format(
                Locale.ROOT,
                "%06d %s [%s] %s",
                sequence,
                CLOCK_FORMAT.format(Instant.now()),
                cleanArea.isBlank() ? "general" : cleanArea,
                cleanMessage
            ));
            while (EVENTS.size() > MAX_EVENTS) {
                EVENTS.removeFirst();
            }
        }
    }

    public static void clear() {
        synchronized (EVENTS) {
            EVENTS.clear();
            sequence = 0L;
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
}
