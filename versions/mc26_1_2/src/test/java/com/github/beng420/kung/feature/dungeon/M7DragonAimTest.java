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
    public void lobComesDownOnTheTarget() {
        Vec3 eye = Vec3.ZERO;
        Vec3 target = new Vec3(20, 10, 22);
        var shot = M7DragonAim.lobShot(eye, target, 3.0);
        assertNotNull(shot);
        assertTrue("a lob, not a direct shot", shot.direction().y > 0.7);
        // Fly it independently: vanilla arrow, move then drag and gravity.
        Vec3 at = eye;
        Vec3 velocity = shot.direction().scale(3.0);
        double closest = Double.MAX_VALUE;
        boolean fallingThere = false;
        for (int tick = 0; tick < 200; tick++) {
            Vec3 next = at.add(velocity);
            for (int step = 0; step <= 10; step++) {
                double distance = at.lerp(next, step / 10.0).distanceTo(target);
                if (distance < closest) {
                    closest = distance;
                    fallingThere = velocity.y < 0;
                }
            }
            at = next;
            velocity = velocity.scale(0.99).subtract(0, 0.05, 0);
        }
        assertEquals(0, closest, 0.05);
        assertTrue("it has to be coming down", fallingThere);
        assertTrue(shot.ticks() > 40);
        // A ten-tick Last Breath draw from just beside the spawn: almost straight up, and it still comes down.
        var spam = M7DragonAim.lobShot(eye, new Vec3(2, 8.5, 3), M7DragonAim.drawSpeed(10));
        assertNotNull(spam);
        assertTrue(spam.direction().y > 0.9);
        assertTrue(spam.ticks() > 20 && spam.ticks() < 45);
        // Out of reach: drag caps an arrow's horizontal travel at 100x its horizontal speed.
        assertNull(M7DragonAim.lobShot(eye, new Vec3(400, 0, 0), 3.0));
    }

    @Test
    public void eightTicksIsWhatItTakesToReachTheDragon() {
        // Vanilla power, as Last Breath fires: nothing at 0, and a dragon 6.4 above your eye needs 8 ticks.
        assertEquals(0.0, M7DragonAim.drawSpeed(0), 1e-9);
        Vec3 dragon = new Vec3(0, 6.4, 0.5);
        assertNull(M7DragonAim.lobShot(Vec3.ZERO, dragon, M7DragonAim.drawSpeed(7)));
        assertNotNull(M7DragonAim.lobShot(Vec3.ZERO, dragon, M7DragonAim.drawSpeed(8)));
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
        var body = com.github.beng420.kung.config.category.DungeonConfig.DragonPart.BODY;
        assertEquals(anchor.add(0, 2, 0), timeline.at(-5, body));
        assertEquals(anchor.add(1.5, 2, -1.5), timeline.at(1.5, body));
        assertEquals(anchor.add(2, 2, -2), timeline.at(40, body));
        // Stand spot: the body's mean over ticks 0-17 - here 0, 1, then 2 for the other sixteen.
        assertEquals(anchor.add(33 / 18.0, 2, -33 / 18.0).distanceTo(M7DragonAim.standSpot(timeline)), 0, 1e-9);
        assertNull(M7DragonAim.parse("{\"statue\":\"RED\",\"hint\":1,\"rows\":[[0,1,2]]}"));
        assertNull(M7DragonAim.parse("not json"));
    }
}
