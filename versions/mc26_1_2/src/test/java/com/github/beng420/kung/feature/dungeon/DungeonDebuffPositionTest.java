package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.junit.BeforeClass;
import org.junit.Test;

public final class DungeonDebuffPositionTest {
    @BeforeClass public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test public void goldorMarkerCoordinatesMatchReceivedMovementBeforeTheMobFinishesInterpolating() {
        var goldor = wither();
        Vec3 rendered = new Vec3(45.080954954055635, 117.19359608171682, 40.03958822780525);
        goldor.setPos(rendered);
        var originalBounds = goldor.getBoundingBox();
        // Marker and rendered positions are from 22:02:04. The movement destination is simulated:
        // the old trace did not record it, so this checks the interpolation failure, not an exact replay.
        List<Vec3> markers = List.of(new Vec3(47.84375, 119.5, 40), new Vec3(47.71875, 119.5, 40.21875),
            new Vec3(47.9375, 119.46875, 40.09375), new Vec3(47.8125, 119.5, 40));
        for (Vec3 marker : markers) {
            assertNull(DungeonDebuffTracker.uniqueTarget(marker, List.of(DungeonDebuffFeature.matchingTarget(goldor))));
        }
        Vec3 received = new Vec3(47.875, rendered.y, rendered.z);
        goldor.moveOrInterpolateTo(received);
        assertEquals(rendered, goldor.position());
        assertEquals(originalBounds, goldor.getBoundingBox());
        var target = DungeonDebuffFeature.matchingTarget(goldor);
        assertEquals(received, target.position());
        for (Vec3 marker : markers) {
            assertEquals(goldor.getUUID(), DungeonDebuffTracker.uniqueTarget(marker, List.of(target)));
        }
        assertNull(DungeonDebuffTracker.uniqueTarget(received.add(3, 1, 0), List.of(target)));
        var overlapping = wither();
        overlapping.setPos(received);
        assertNull(DungeonDebuffTracker.uniqueTarget(markers.getFirst(),
            List.of(target, DungeonDebuffFeature.matchingTarget(overlapping))));
    }

    @Test public void movingMarkerUsesTheSameCoordinateSpaceAndStoppedInterpolationUsesCurrentPosition() {
        var marker = new ArmorStand(EntityType.ARMOR_STAND, null);
        marker.setPos(1, 2, 3);
        var bounds = marker.getBoundingBox();
        marker.moveOrInterpolateTo(new Vec3(4, 5, 6));
        assertEquals(new Vec3(4, 5, 6), DungeonDebuffFeature.matchingPosition(marker));
        assertEquals(bounds.move(3, 3, 3), DungeonDebuffFeature.matchingTarget(marker).bounds());
        assertEquals(new Vec3(1, 2, 3), marker.position());
        marker.getInterpolation().cancel();
        assertEquals(marker.position(), DungeonDebuffFeature.matchingPosition(marker));
        assertEquals(bounds, DungeonDebuffFeature.matchingTarget(marker).bounds());
    }

    private static WitherBoss wither() {
        return new WitherBoss(EntityType.WITHER, null) {
            // The constructor's boss-bar label otherwise requires a world's scoreboard.
            @Override public Component getDisplayName() { return Component.literal("Wither"); }
        };
    }
}
