package com.github.beng420.kung.feature.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Bundled exclusions plus explicit session captures; never reads or writes profile files. */
final class DungeonStaticChestCatalog {
    static final String RESOURCE = "kung-dungeon-scans/static-trapped-chests.json";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_BYTES = 4_000_000;
    private static final int MAX_PATTERNS = 256;
    private List<DungeonStaticChestPattern> patterns = List.of();
    private int bundledCount;
    private boolean loaded;
    private long revision;

    DungeonStaticChestCatalog() { }

    DungeonStaticChestCatalog(List<DungeonStaticChestPattern> bundled) {
        patterns = List.copyOf(bundled);
        bundledCount = patterns.size();
        loaded = true;
    }

    void load() throws IOException {
        if (loaded) return;
        try (var stream = DungeonStaticChestCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IOException("Missing bundled static chest data");
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Static chest data exceeds 4 MB");
            patterns = read(new String(bytes, StandardCharsets.UTF_8));
        }
        bundledCount = patterns.size();
        loaded = true;
    }

    static List<DungeonStaticChestPattern> read(String text) throws IOException {
        try {
            if (text.length() > MAX_BYTES) throw new IllegalArgumentException("Static chest data exceeds 4 MB");
            var data = JSON.fromJson(text, Data.class);
            if (data == null || data.schema() != 1 || data.patterns() == null || data.patterns().size() > MAX_PATTERNS) {
                throw new IllegalArgumentException("Invalid static chest data");
            }
            var result = List.copyOf(data.patterns());
            if (new HashSet<>(result).size() != result.size()) throw new IllegalArgumentException("Duplicate static chest patterns");
            return result;
        } catch (RuntimeException exception) {
            throw new IOException("Cannot read static chest data", exception);
        }
    }

    List<DungeonStaticChestPattern> patterns() { return patterns; }
    int bundledCount() { return bundledCount; }
    int sessionCount() { return patterns.size() - bundledCount; }
    long revision() { return revision; }

    boolean add(DungeonStaticChestPattern pattern) throws IOException {
        load();
        if (patterns.contains(pattern)) return false;
        if (patterns.size() >= MAX_PATTERNS) throw new IOException("Static chest storage is full (256 entries)");
        var next = new ArrayList<>(patterns);
        next.add(pattern);
        patterns = List.copyOf(next);
        revision++;
        return true;
    }

    DungeonStaticChestPattern undo() throws IOException {
        load();
        // Undo belongs to authoring; shipped exclusions must survive it.
        if (sessionCount() == 0) return null;
        var removed = patterns.getLast();
        patterns = List.copyOf(patterns.subList(0, patterns.size() - 1));
        revision++;
        return removed;
    }

    String exportSession() throws IOException {
        load();
        return JSON.toJson(new Data(1, patterns.subList(bundledCount, patterns.size())));
    }

    private record Data(int schema, List<DungeonStaticChestPattern> patterns) { }
}
