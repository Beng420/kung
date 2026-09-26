package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.config.category.DungeonConfig.DragonPart;
import com.github.beng420.kung.feature.dungeon.M7DragonTracker.Statue;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import net.minecraft.world.phys.Vec3;

/**
 * Where to shoot so an arrow fired now meets a dragon. Flights repeat to within a quarter block
 * run after run, so one recorded per-tick flight per statue predicts the next one.
 */
final class M7DragonAim {
    // ponytail: vanilla arrow physics (full power 3 blocks/tick, drag 0.99, gravity 0.05). Last
    // Breath and Terminator speeds are unmeasured; if aims land short or long, measure the player's own arrows.
    static final double ARROW_SPEED = 3.0;
    private static final double DRAG = 0.99;
    private static final double GRAVITY = 0.05;
    /** Median of 19 traced spawns: the anchor burst comes 5.05 s before the dragon exists. */
    static final int HINT_TO_SPAWN_TICKS = 101;
    /** Recorded and predicted window after spawn; the debuff window is the first 40 ticks. */
    static final int TIMELINE_TICKS = 60;
    /** 290 logged prefires hit when they came down 0-17 ticks after spawn; later ones found the dragon gone. */
    static final int HIT_WINDOW_TICKS = 17;
    /** Vanilla draw power: under 3 ticks it stays below 10% and no arrow leaves the bow. */
    static final int MIN_SHOT_TICKS = 3;
    static final int FULL_DRAW_TICKS = 20;
    /** The aim marker sits this far along the launch direction, so it rides with you. */
    static final double LOB_MARKER_DISTANCE = 10;
    private static final double LOB_STEP = Math.toRadians(1);
    private static final int MAX_FLIGHT_TICKS = 200;

    private M7DragonAim() { }

    /** Ticks an arrow at this launch speed needs to cover the distance, or -1 when drag stops it short. */
    static double arrowTicks(double distance, double speed) {
        double remaining = 1 - distance * (1 - DRAG) / speed;
        return remaining <= 0 ? -1 : Math.log(remaining) / Math.log(DRAG);
    }

    /** How far an arrow falls over these ticks, so the aim sits that much higher. */
    static double drop(double ticks) {
        double drop = 0;
        double fall = 0;
        for (int tick = 0; tick < ticks; tick++) {
            drop += fall * Math.min(1, ticks - tick);
            fall = fall * DRAG + GRAVITY;
        }
        return drop;
    }

    record Aim(Vec3 point, double arrivalTick) { }

    /**
     * The body's position when an arrow fired now arrives, raised by the arrow's drop. Arrival
     * time depends on distance and distance on arrival time, so a few rounds settle both.
     */
    static Aim aim(Vec3 eye, DoubleFunction<Vec3> bodyAt, double ticksSinceSpawn, double arrowSpeed) {
        Vec3 target = bodyAt.apply(ticksSinceSpawn);
        double ticks = 0;
        for (int round = 0; round < 4; round++) {
            ticks = arrowTicks(eye.distanceTo(target), arrowSpeed);
            if (ticks < 0) return null;
            target = bodyAt.apply(ticksSinceSpawn + ticks);
        }
        return new Aim(target.add(0, drop(ticks), 0), ticksSinceSpawn + ticks);
    }

    /** Launch speed after this many draw ticks; Hypixel's Last Breath follows vanilla (8 ticks just reach the dragon). */
    static double drawSpeed(int ticks) {
        return ARROW_SPEED * net.minecraft.world.item.BowItem.getPowerForTime(Math.clamp(ticks, 0, FULL_DRAW_TICKS));
    }

    record Shot(Vec3 direction, double ticks) { }

    /**
     * The high arc that comes down on the target, for arrows shot up that rain onto the dragon.
     * Scans down from straight up to the first pitch still at the target's height when it gets
     * there, then bisects towards the falling side. Null when no pitch reaches the target.
     */
    static Shot lobShot(Vec3 eye, Vec3 target, double speed) {
        double dx = target.x - eye.x;
        double dz = target.z - eye.z;
        double range = Math.sqrt(dx * dx + dz * dz);
        double rise = target.y - eye.y;
        double high = Math.PI / 2;
        double low = Double.NaN;
        for (double pitch = high - LOB_STEP; pitch > 0; pitch -= LOB_STEP) {
            if (flight(range, pitch, speed)[0] >= rise) {
                low = pitch;
                break;
            }
            high = pitch;
        }
        if (Double.isNaN(low)) return null;
        for (int round = 0; round < 12; round++) {
            double mid = (low + high) / 2;
            if (flight(range, mid, speed)[0] >= rise) low = mid;
            else high = mid;
        }
        double yaw = Math.atan2(dz, dx);
        return new Shot(new Vec3(Math.cos(low) * Math.cos(yaw), Math.sin(low), Math.cos(low) * Math.sin(yaw)),
            flight(range, low, speed)[1]);
    }

    /** Height and ticks when the arrow has covered this horizontal distance; -infinity if it never does. */
    private static double[] flight(double range, double pitch, double speed) {
        double x = 0;
        double y = 0;
        double vx = speed * Math.cos(pitch);
        double vy = speed * Math.sin(pitch);
        for (int tick = 0; tick < MAX_FLIGHT_TICKS; tick++) {
            if (x + vx >= range) {
                double part = vx <= 0 ? 0 : (range - x) / vx;
                return new double[] {y + vy * part, tick + part};
            }
            x += vx;
            y += vy;
            vx *= DRAG;
            vy = vy * DRAG - GRAVITY;
        }
        return new double[] {Double.NEGATIVE_INFINITY, -1};
    }

    /**
     * Where the dragon will be for the spam: the body's mean position over the hit window. Stand under
     * it; checked against 592 logged arrows, it lands on the best or second-best block for four statues.
     */
    static Vec3 standSpot(Timeline timeline) {
        Vec3 sum = Vec3.ZERO;
        for (int tick = 0; tick <= HIT_WINDOW_TICKS; tick++) sum = sum.add(timeline.at(tick, DragonPart.BODY));
        return sum.scale(1.0 / (HIT_WINDOW_TICKS + 1));
    }

    /** One recorded flight: per server tick since spawn, the tick and xyz of every DragonPart, relative to the anchor. */
    record Timeline(Statue statue, int hintTicks, List<double[]> rows) {
        /** A part's centre at a fractional tick: the first row before it, interpolated after, the last row beyond. */
        Vec3 at(double tick, DragonPart part) {
            if (tick <= rows.getFirst()[0]) return centre(rows.getFirst(), part);
            for (int index = 1; index < rows.size(); index++) {
                double[] next = rows.get(index);
                if (next[0] < tick) continue;
                double[] previous = rows.get(index - 1);
                return centre(previous, part).lerp(centre(next, part),
                    (tick - previous[0]) / (next[0] - previous[0]));
            }
            return centre(rows.getLast(), part);
        }

        private Vec3 centre(double[] row, DragonPart part) {
            int at = 1 + part.ordinal() * 3;
            return statue.spawn().add(row[at], row[at + 1], row[at + 2]);
        }
    }

    static String line(Timeline timeline) {
        StringBuilder line = new StringBuilder("{\"statue\":\"").append(timeline.statue().name())
            .append("\",\"hint\":").append(timeline.hintTicks()).append(",\"rows\":[");
        for (int index = 0; index < timeline.rows().size(); index++) {
            if (index > 0) line.append(',');
            line.append('[');
            double[] row = timeline.rows().get(index);
            for (int value = 0; value < row.length; value++) {
                if (value > 0) line.append(',');
                line.append(Math.round(row[value] * 100.0) / 100.0);
            }
            line.append(']');
        }
        return line.append("]}").toString();
    }

    /** Null for a malformed or foreign line, so one bad write cannot break the rest of the file. */
    static Timeline parse(String line) {
        try {
            var json = com.google.gson.JsonParser.parseString(line).getAsJsonObject();
            List<double[]> rows = new ArrayList<>();
            for (var row : json.getAsJsonArray("rows")) {
                var values = row.getAsJsonArray();
                if (values.size() != 1 + DragonPart.values().length * 3) return null;
                double[] parsed = new double[values.size()];
                for (int index = 0; index < parsed.length; index++) parsed[index] = values.get(index).getAsDouble();
                rows.add(parsed);
            }
            return rows.isEmpty() ? null : new Timeline(Statue.valueOf(json.get("statue").getAsString()),
                json.get("hint").getAsInt(), List.copyOf(rows));
        } catch (RuntimeException malformed) {
            return null;
        }
    }
}
