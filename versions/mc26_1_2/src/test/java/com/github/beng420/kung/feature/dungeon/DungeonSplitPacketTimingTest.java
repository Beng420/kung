package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.util.ServerTickPacketContext;
import com.github.beng420.kung.util.ServerTickSequence;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.junit.Test;

public final class DungeonSplitPacketTimingTest {
    @Test
    public void standaloneTicksAroundAChatBoundaryInOneDeliveryBatchStayOnTheirAppliedSide() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        ServerTickSequence sequence = new ServerTickSequence();
        tracker.startRun(0L, 7, true);
        clock.set(1_000L);
        apply(new ClientboundPingPacket(-1), sequence, tracker, clock);
        apply(new ClientboundSystemChatPacket(Component.literal("The BLOOD DOOR has been opened!"), false),
            sequence, tracker, clock);
        var open = tracker.completedSplits().getLast();
        apply(new ClientboundPingPacket(-2), sequence, tracker, clock);
        assertEquals(50L, open.serverSplitDurationMillis());
        assertEquals(50L, tracker.currentSplitServerDurationMillis());
        assertEquals(100L, tracker.currentTotalServerDurationMillis());
        assertEquals(950L, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
        clock.set(2_000L);
        tracker.observeMessage("[BOSS] The Watcher: You have proven yourself. You may pass.", 0L);
        assertEquals(open, tracker.completedSplits().getFirst());
        assertEquals(50L, tracker.completedSplits().getLast().serverSplitDurationMillis());
        assertEquals(1_900L, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
    }

    @Test
    public void sixtySecondsAtTenOrTwentyTpsIgnoresExtraBundlePingsEvenWithDelayedDelivery() {
        for (int tps : new int[] {10, 20}) {
            for (int batchSize : new int[] {1, 10, 600}) {
                AtomicLong clock = new AtomicLong();
                DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
                ServerTickSequence sequence = new ServerTickSequence();
                tracker.startRun(0L, 7, true);
                for (int endTick = batchSize; endTick <= tps * 60; endTick += batchSize) {
                    clock.set(endTick * 1_000L / tps);
                    for (int tick = endTick - batchSize + 1; tick <= endTick; tick++) {
                        apply(new ClientboundPingPacket(-tick), sequence, tracker, clock);
                        apply(new ClientboundBundlePacket(List.of(new ClientboundPingPacket(10_000 + tick))),
                            sequence, tracker, clock);
                    }
                    assertEquals(-1L, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
                }
                tracker.mark("Blood Clear", 0L);
                long ideal = tps * 60 * 50L;
                assertEquals(60_000L, tracker.completedSplits().getFirst().splitDurationMillis());
                assertEquals(ideal, tracker.completedSplits().getFirst().serverSplitDurationMillis());
                assertEquals(60_000L - ideal, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
            }
        }
    }

    @Test
    public void suppliedTracePhaseCountsMatchNoammWhenBundlePingsAreExcluded() throws Exception {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        ServerTickSequence sequence = new ServerTickSequence();
        tracker.startRun(0L, 7, true);
        int id = 0;
        long expectedTotalIdeal = 0L;
        long expectedLoss = 0L;
        try (var reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("/dungeon/splits-164109.csv"), StandardCharsets.UTF_8))) {
            for (String row : reader.lines().filter(line -> !line.startsWith("#")).toList()) {
                String[] columns = row.split(",");
                String phase = columns[0];
                long wall = Long.parseLong(columns[1]);
                int oldCount = Integer.parseInt(columns[2]);
                int extras = Integer.parseInt(columns[3]);
                long ideal = Long.parseLong(columns[4]);
                int ticks = oldCount - extras;
                assertEquals(ideal, ticks * 50L);
                tracker.mark(phase, 0L);
                long start = clock.get();
                int bundled = 0;
                // Only aggregate counts/times were logged; spacing and IDs here
                // are synthetic. Distinct IDs reproduce duplicatePings=0 in the trace.
                for (int tick = 1; tick <= ticks; tick++) {
                    clock.set(start + tick * wall / ticks);
                    apply(new ClientboundPingPacket(--id), sequence, tracker, clock);
                    while (bundled < tick * extras / ticks) {
                        apply(new ClientboundBundlePacket(List.of(new ClientboundPingPacket(--id))),
                            sequence, tracker, clock);
                        bundled++;
                    }
                }
                tracker.mark("Boundary", 0L);
                var split = tracker.completedSplits().getLast();
                expectedTotalIdeal += ideal;
                expectedLoss += wall - ideal;
                assertEquals(phase, split.name());
                assertEquals(ideal, split.serverSplitDurationMillis());
                assertEquals(expectedTotalIdeal, split.serverTotalDurationMillis());
                assertEquals(expectedLoss, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
            }
        }
        assertEquals(8_044L, expectedLoss);
    }

    @Test
    public void bundledTrafficDuringAThreeSecondStallAndAChatBoundaryCannotHideTheLoss() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        ServerTickSequence sequence = new ServerTickSequence();
        tracker.startRun(0L, 7, true);
        for (int tick = 1; tick <= 20; tick++) {
            clock.set(tick * 50L);
            apply(new ClientboundPingPacket(-tick), sequence, tracker, clock);
        }
        for (int extra = 1; extra <= 60; extra++) {
            clock.set(1_000L + extra * 50L);
            apply(new ClientboundBundlePacket(List.of(new ClientboundPingPacket(-1000 - extra))),
                sequence, tracker, clock);
            assertEquals(1_000L, tracker.currentTotalServerDurationMillis());
            assertEquals(-1L, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
        }
        apply(new ClientboundBundlePacket(List.of(
            new ClientboundPingPacket(-2000),
            new ClientboundSystemChatPacket(Component.literal("The BLOOD DOOR has been opened!"), false),
            new ClientboundPingPacket(-2001)
        )), sequence, tracker, clock);
        var settled = tracker.completedSplits().getLast();
        assertEquals(3_000L, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
        clock.set(5_000L);
        // Twenty standalone pings in one delivery batch are still twenty ticks.
        for (int tick = 21; tick <= 40; tick++) apply(new ClientboundPingPacket(-tick), sequence, tracker, clock);
        assertEquals(2_000L, tracker.currentTotalServerDurationMillis());
        assertEquals(settled, tracker.completedSplits().getLast());
        assertEquals(3_000L, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
    }

    private static void apply(Packet<?> packet, ServerTickSequence sequence,
                              DungeonSplitTracker tracker, AtomicLong clock) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            ServerTickPacketContext.applyBundle(() -> {
                for (Packet<?> child : bundle.subPackets()) apply(child, sequence, tracker, clock);
            });
        } else if (packet instanceof ClientboundPingPacket ping) {
            if (sequence.accept(ping.getId(), ServerTickPacketContext.isApplyingBundle())) tracker.serverTick(clock.get());
        } else if (packet instanceof ClientboundSystemChatPacket chat) {
            tracker.observeMessage(chat.content().getString(), 0L);
        }
    }
}
