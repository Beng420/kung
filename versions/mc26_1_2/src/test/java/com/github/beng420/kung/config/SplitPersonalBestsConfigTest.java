package com.github.beng420.kung.config;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.category.SplitsConfig;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class SplitPersonalBestsConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void realConfigSaveAndReloadKeepFloorModeAndPhaseSeparate() throws Exception {
        var path = temporary.getRoot().toPath().resolve("kung.json");
        KungConfig config = new KungConfig(path);
        config.load();
        config.splits.setX(321);
        config.splits.setScale(150);
        config.splits.setTimePrediction(false);
        config.splits.setPredictionMode(SplitsConfig.PredictionMode.LIVE);
        config.splits.recordPersonalBests(6, false, Map.of("Blood Open", 40_000L, "Blood Clear", 20_000L));
        config.splits.recordPersonalBests(7, false, Map.of("Blood Open", 35_000L));
        config.splits.recordPersonalBests(7, true, Map.of("Blood Open", 25_000L));
        config.splits.recordPersonalBests(6, true, Map.of("Blood Open", 30_000L));
        config.splits.recordPersonalBests(0, false, Map.of("Blood Open", 50_000L));
        KungConfig restored = new KungConfig(path);
        restored.load();
        assertEquals(40_000L, restored.splits.personalBestMillis(6, false, "Blood Open"));
        assertEquals(20_000L, restored.splits.personalBestMillis(6, false, "Blood Clear"));
        assertEquals(35_000L, restored.splits.personalBestMillis(7, false, "Blood Open"));
        assertEquals(25_000L, restored.splits.personalBestMillis(7, true, "Blood Open"));
        assertEquals(30_000L, restored.splits.personalBestMillis(6, true, "Blood Open"));
        assertEquals(50_000L, restored.splits.personalBestMillis(0, false, "Blood Open"));
        assertEquals(-1L, restored.splits.personalBestMillis(1, false, "Blood Open"));
        assertEquals(-1L, restored.splits.personalBestMillis(7, true, "Blood Clear"));
        assertEquals(321, restored.splits.x());
        assertEquals(150, restored.splits.scale());
        assertFalse(restored.splits.timePrediction());
        assertEquals(SplitsConfig.PredictionMode.LIVE, restored.splits.predictionMode());
        assertFalse(restored.splits.enabled());
        assertTrue(Files.readString(path).contains("\"M7\""));
    }

    @Test public void onlyStrictImprovementsSaveOncePerBatch() {
        SplitsConfig config = new SplitsConfig();
        AtomicInteger writes = new AtomicInteger();
        config.onChange(writes::incrementAndGet);
        config.recordPersonalBests(6, false, Map.of("Blood Open", 30_000L, "Blood Clear", 20_000L));
        config.recordPersonalBests(6, false, Map.of("Blood Open", 31_000L, "Blood Clear", 20_000L));
        assertEquals(1, writes.get());
        config.recordPersonalBests(6, false, Map.of("Blood Open", 25_000L, "Blood Clear", 22_000L));
        assertEquals(2, writes.get());
        assertEquals(25_000L, config.personalBestMillis(6, false, "Blood Open"));
        assertEquals(20_000L, config.personalBestMillis(6, false, "Blood Clear"));
    }

    @Test public void missingOrUnknownPredictionSettingsPreservePhaseEndDefaults() {
        for (String json : new String[] {"{}", "{\"splitsOverlay\":{\"predictionMode\":null}}",
            "{\"splitsOverlay\":{\"predictionMode\":\"unknown\"}}"}) {
            var config = KungConfig.read(new StringReader(json)).splits;
            assertTrue(config.timePrediction());
            assertEquals(SplitsConfig.PredictionMode.PHASE_END, config.predictionMode());
            assertFalse(config.enabled());
        }
    }

    @Test public void oldNullAndInvalidMeasurementsDoNotBecomeRecords() {
        for (String json : new String[] {"{}", "{\"splitsOverlay\":{\"personalBests\":null}}",
            "{\"splitsOverlay\":{\"personalBests\":{\"F6\":null}}}",
            "{\"splitsOverlay\":{\"personalBests\":{\"F6\":{\"Blood Open\":null,\"Blood Clear\":0}}}}"}) {
            var config = KungConfig.read(new StringReader(json)).splits;
            assertEquals(-1L, config.personalBestMillis(6, false, "Blood Open"));
            assertEquals(-1L, config.personalBestMillis(6, false, "Blood Clear"));
            config.recordPersonalBests(-1, false, Map.of("Blood Open", 1L));
            config.recordPersonalBests(8, true, Map.of("Blood Open", 1L));
            config.recordPersonalBests(6, false, Map.of("Blood Open", -1L, "Blood Clear", 0L));
            assertEquals(-1L, config.personalBestMillis(6, false, "Blood Open"));
            assertEquals(-1L, config.personalBestMillis(6, false, "Blood Clear"));
            config.recordPersonalBests(6, false, Map.of("Blood Open", 40_000L));
            assertEquals(40_000L, config.personalBestMillis(6, false, "Blood Open"));
        }
    }
}
