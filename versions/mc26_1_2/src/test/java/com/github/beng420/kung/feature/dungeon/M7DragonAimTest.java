package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.feature.dungeon.M7DragonTracker.Statue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.Test;

public final class M7DragonAimTest {
    @Test
    public void arrowFlightFollowsVanillaDragAndDrop() {
        assertEquals(0.0, M7DragonAim.arrowTicks(0, 3.0), 1e-9);
        // 3 blocks/tick slowed by 1% a tick: 30 blocks take ln(0.9) / ln(0.99) ticks.
        assertEquals(10.48, M7DragonAim.arrowTicks(30, 3.0), 0.01);
        // A half-drawn arrow needs far longer, and one that is too slow never arrives.
        assertTrue(M7DragonAim.arrowTicks(30, 1.5) > 2 * M7DragonAim.arrowTicks(30, 3.0));
        assertEquals(-1.0, M7DragonAim.arrowTicks(301, 3.0), 1e-9);
        assertEquals(0.0, M7DragonAim.drop(1), 1e-9);
        assertEquals(0.05, M7DragonAim.drop(2), 1e-9);
        assertTrue(M7DragonAim.drop(14) > 4);
    }

    @Test
    public void aimLeadsAMovingBodyAndRaisesForDrop() {
        Vec3 eye = Vec3.ZERO;
        // Standing still 30 blocks out: aim straight at it, raised by the drop.
        var still = M7DragonAim.aim(eye, tick -> new Vec3(0, 0, 30), 0, 3.0);
        double ticks = M7DragonAim.arrowTicks(30, 3.0);
        assertEquals(new Vec3(0, M7DragonAim.drop(ticks), 30), still.point());
        assertEquals(ticks, still.arrivalTick(), 1e-9);
        // Flying sideways at 0.35 blocks/tick after spawn: the aim sits ahead by speed x flight time.
        var moving = M7DragonAim.aim(eye, tick -> new Vec3(0.35 * Math.max(0, tick), 0, 30), 0, 3.0);
        assertEquals(0.35 * moving.arrivalTick(), moving.point().x, 1e-6);
        assertTrue(moving.point().x > 3);
        // Ten ticks before spawn the arrow lands before the dragon exists.
        assertTrue(M7DragonAim.aim(eye, tick -> new Vec3(0, 0, 10), -10, 3.0).arrivalTick() < 0);
    }

    @Test
    public void timelinesRoundTripAndInterpolateTheBody() {
        List<double[]> rows = new ArrayList<>();
        for (int tick = 0; tick < 3; tick++) {
            double[] row = new double[1 + 27];
            row[0] = tick;
            // BODY is the fourth part: BOX, HEAD, NECK, BODY.
            row[1 + 9] = tick;
            row[1 + 10] = 2;
            row[1 + 11] = -tick;
            rows.add(row);
        }
        var timeline = M7DragonAim.parse(M7DragonAim.line(new M7DragonAim.Timeline(Statue.RED, 101, rows)));
        assertNotNull(timeline);
        assertEquals(101, timeline.hintTicks());
        Vec3 anchor = Statue.RED.spawn();
        assertEquals(anchor.add(M7DragonAim.SPAWN_BODY), timeline.body(-5));
        assertEquals(anchor.add(1.5, 2, -1.5), timeline.body(1.5));
        assertEquals(anchor.add(2, 2, -2), timeline.body(40));
        assertNull(M7DragonAim.parse("{\"statue\":\"RED\",\"hint\":1,\"rows\":[[0,1,2]]}"));
        assertNull(M7DragonAim.parse("not json"));
    }
}
