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
    /** Body centre in the spawn pose, relative to the anchor (SPAWN_PARTS body: 5x3x5 at -2.5, 0, -3). */
    static final Vec3 SPAWN_BODY = new Vec3(0, 1.5, -0.5);

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

    /** One recorded flight: per server tick since spawn, the tick and xyz of every DragonPart, relative to the anchor. */
    record Timeline(Statue statue, int hintTicks, List<double[]> rows) {
        /** Body centre at a fractional tick: the spawn pose before spawn, interpolated after, the last row beyond. */
        Vec3 body(double tick) {
            if (rows.isEmpty() || tick <= rows.getFirst()[0]) {
                return tick <= 0 || rows.isEmpty() ? statue.spawn().add(SPAWN_BODY) : part(rows.getFirst(), DragonPart.BODY);
            }
            for (int index = 1; index < rows.size(); index++) {
                double[] next = rows.get(index);
                if (next[0] < tick) continue;
                double[] previous = rows.get(index - 1);
                return part(previous, DragonPart.BODY).lerp(part(next, DragonPart.BODY),
                    (tick - previous[0]) / (next[0] - previous[0]));
            }
            return part(rows.getLast(), DragonPart.BODY);
        }

        private Vec3 part(double[] row, DragonPart part) {
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
