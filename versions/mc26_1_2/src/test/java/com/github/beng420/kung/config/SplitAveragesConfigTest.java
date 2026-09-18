package com.github.beng420.kung.config;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.category.SplitsConfig;
import com.github.beng420.kung.config.category.SplitsConfig.PredictionSource;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class SplitAveragesConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void lastTwentyWholeRunsPersistAndEvictEverySplitTogether() throws Exception {
        var path = temporary.getRoot().toPath().resolve("kung.json");
        var config = new KungConfig(path);
        config.load();
        config.splits.setPredictionSource(PredictionSource.AVG);
        for (int run = 1; run <= 23; run++) {
            Map<String, Long> samples = new HashMap<>();
            samples.put("Blood Open", run * 1_000L);
            if (run <= 3) samples.put("Blood Clear", 5_000L);
            config.splits.recordRun(7, true, samples, samples);
            samples.clear(); // The history owns its copy of each completed run.
        }
        config.splits.recordRun(7, false, Map.of(), Map.of("Blood Open", 50_000L));
        config.splits.recordRun(6, true, Map.of(), Map.of("Blood Open", 60_000L));
        config.splits.recordRun(0, false, Map.of(), Map.of("Blood Open", 70_000L));

        var restored = new KungConfig(path);
        restored.load();
        assertEquals(PredictionSource.AVG, restored.splits.predictionSource());
        assertEquals(20, restored.splits.recentRunCount(7, true));
        assertEquals(13_500L, restored.splits.averageMillis(7, true, "Blood Open"));
        assertEquals(-1L, restored.splits.averageMillis(7, true, "Blood Clear"));
        assertEquals(1_000L, restored.splits.personalBestMillis(7, true, "Blood Open"));
        assertEquals(50_000L, restored.splits.averageMillis(7, false, "Blood Open"));
        assertEquals(60_000L, restored.splits.averageMillis(6, true, "Blood Open"));
        assertEquals(70_000L, restored.splits.averageMillis(0, true, "Blood Open"));
        assertEquals(0, restored.splits.recentRunCount(6, false));

        restored.splits.clearRecentRuns(7, true);
        config.load();
        assertEquals(0, config.splits.recentRunCount(7, true));
        assertEquals(-1L, config.splits.averageMillis(7, true, "Blood Open"));
        assertEquals(1_000L, config.splits.personalBestMillis(7, true, "Blood Open"));
        assertEquals(50_000L, config.splits.averageMillis(7, false, "Blood Open"));
    }

    @Test public void missingSplitsAreExcludedAndSlowerRunsStillSaveOnce() {
        var config = new SplitsConfig();
        var writes = new AtomicInteger();
        config.onChange(writes::incrementAndGet);
        var first = Map.of("Blood Open", 10_000L, "Blood Clear", 20_000L);
        var second = Map.of("Blood Open", 30_001L, "Blood Clear", -1L, "Portal Entry", 0L);
        config.recordRun(6, false, first, first);
        config.recordRun(6, false, second, second);
        assertEquals(2, writes.get());
        assertEquals(2, config.recentRunCount(6, false));
        assertEquals(20_001L, config.averageMillis(6, false, "Blood Open"));
        assertEquals(20_000L, config.averageMillis(6, false, "Blood Clear"));
        assertEquals(-1L, config.averageMillis(6, false, "Portal Entry"));
        assertEquals(10_000L, config.personalBestMillis(6, false, "Blood Open"));
        config.clearRecentRuns(6, true);
        config.recordRun(-1, false, first, first);
        config.recordRun(8, false, first, first);
        config.recordRun(6, false, Map.of(), Map.of("Blood Open", 0L));
        assertEquals(2, writes.get());
        config.clearRecentRuns(6, false);
        config.clearRecentRuns(6, false);
        assertEquals(3, writes.get());
    }

    @Test public void oldOrNullConfigDefaultsToPbAndAnEmptyAverageHistory() {
        for (String json : new String[] {"{}",
            "{\"splitsOverlay\":{\"predictionSource\":null,\"recentRuns\":null}}",
            "{\"splitsOverlay\":{\"predictionSource\":\"unknown\",\"recentRuns\":{\"F6\":null}}}"}) {
            var config = KungConfig.read(new StringReader(json)).splits;
            assertEquals(PredictionSource.PB, config.predictionSource());
            assertEquals(0, config.recentRunCount(6, false));
            assertEquals(-1L, config.averageMillis(6, false, "Blood Open"));
            config.recordRun(6, false, Map.of(), Map.of("Blood Open", 10_000L));
            assertEquals(1, config.recentRunCount(6, false));
            assertEquals(10_000L, config.averageMillis(6, false, "Blood Open"));
        }
    }
}
