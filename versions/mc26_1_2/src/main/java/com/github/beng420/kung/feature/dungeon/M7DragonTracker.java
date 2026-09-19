package com.github.beng420.kung.feature.dungeon;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server evidence confirms statue destruction; the displayed range is only an estimate. */
final class M7DragonTracker {
    static final int CONFIRMATION_TICKS = 40;
    static final int MAX_ATTEMPTS = 32;
    private final Map<UUID, Attempt> attempts = new LinkedHashMap<>();
    private final Map<UUID, Long> unknownDeaths = new LinkedHashMap<>();
    private final EnumSet<Statue> brokenStatues = EnumSet.noneOf(Statue.class);
    private long tick;

    long tick() { return tick; }

    void reset() {
        attempts.clear();
        unknownDeaths.clear();
        brokenStatues.clear();
        tick = 0;
    }

    List<Result> advance() {
        tick++;
        unknownDeaths.values().removeIf(deathTick -> tick - deathTick > CONFIRMATION_TICKS);
        List<Result> results = new ArrayList<>();
        for (Attempt attempt : attempts.values()) {
            if (attempt.dead && attempt.outcome == null && tick - attempt.deathTick > CONFIRMATION_TICKS) {
                attempt.outcome = Outcome.UNKNOWN;
                results.add(new Result(attempt.statue, Outcome.UNKNOWN, "No statue confirmation received", attempt.position));
            }
        }
        return List.copyOf(results);
    }

    List<Result> spawn(UUID uuid, Statue statue, Vec3 position) {
        Attempt existing = attempts.get(uuid);
        if (existing != null) {
            existing.loaded = true;
            if (!existing.dead) existing.position = position;
            return List.of();
        }
        Attempt previous = latest(statue);
        if (attempts.size() >= MAX_ATTEMPTS) {
            var retired = attempts.entrySet().stream()
                .filter(entry -> entry.getValue().dead || !entry.getValue().loaded
                    || entry.getValue().outcome != null).findFirst();
            if (retired.isEmpty()) return List.of();
            attempts.remove(retired.get().getKey());
        }
        attempts.put(uuid, new Attempt(uuid, statue, position));
        if (previous != null && previous.dead && previous.outcome == null) {
            previous.outcome = Outcome.UNKNOWN;
            return List.of(new Result(statue, Outcome.UNKNOWN, "Another dragon spawned without statue confirmation", previous.position));
        }
        return List.of();
    }

    void move(UUID uuid, Vec3 position) {
        Attempt attempt = attempts.get(uuid);
        if (attempt != null && !attempt.dead) attempt.position = position;
    }

    void death(UUID uuid) {
        Attempt attempt = attempts.get(uuid);
        if (attempt == null || attempt.dead) return;
        attempt.dead = true;
        attempt.deathTick = tick;
    }

    boolean unknownDeath(UUID uuid) {
        if (unknownDeaths.containsKey(uuid)) return false;
        if (unknownDeaths.size() >= MAX_ATTEMPTS) {
            // Newer deaths preserve ambiguity for at least as long as the evicted oldest record.
            unknownDeaths.remove(unknownDeaths.keySet().iterator().next());
        }
        unknownDeaths.put(uuid, tick);
        return true;
    }

    void unload(UUID uuid) {
        Attempt attempt = attempts.get(uuid);
        if (attempt != null) attempt.loaded = false;
    }

    Attempt latest(Statue statue) {
        Attempt latest = null;
        for (Attempt attempt : attempts.values()) if (attempt.statue == statue) latest = attempt;
        return latest;
    }

    Attempt attempt(UUID uuid) { return attempts.get(uuid); }

    boolean statueCounted(Statue statue) { return brokenStatues.contains(statue); }

    List<Result> statueBroken(Statue statue) {
        if (!brokenStatues.add(statue)) return List.of();
        Attempt attempt = latest(statue);
        if (attempt != null) attempt.outcome = Outcome.COUNTS;
        return List.of(new Result(statue, Outcome.COUNTS, "Statue block destroyed", attempt == null ? null : attempt.position));
    }

    List<Result> confirmMessage(String message) {
        if (!"[BOSS] Wither King: Oh, this one hurts!".equals(message)
            && !"[BOSS] Wither King: I have more of those.".equals(message)
            && !"[BOSS] Wither King: My soul is disposable.".equals(message)) return List.of();
        if (!unknownDeaths.isEmpty()) return List.of(new Result(null, Outcome.COUNTS, message, null));
        Attempt candidate = null;
        for (Attempt attempt : attempts.values()) {
            if (!attempt.dead || tick - attempt.deathTick > CONFIRMATION_TICKS) continue;
            // Include resolved deaths: excluding one could assign its delayed message to another dragon.
            if (candidate != null) return List.of(new Result(null, Outcome.COUNTS, message, null));
            candidate = attempt;
        }
        if (candidate == null) return List.of(new Result(null, Outcome.COUNTS, message, null));
        if (candidate.outcome == Outcome.COUNTS) return List.of();
        candidate.outcome = Outcome.COUNTS;
        brokenStatues.add(candidate.statue);
        return List.of(new Result(candidate.statue, Outcome.COUNTS, message, candidate.position));
    }

    enum Outcome { COUNTS, UNKNOWN }

    /** position is where the dragon died, or null when no attempt could be tied to the result. */
    record Result(Statue statue, Outcome outcome, String evidence, Vec3 position) { }

    static final class Attempt {
        private final UUID uuid;
        private final Statue statue;
        private Vec3 position;
        private boolean dead;
        private boolean loaded = true;
        private Outcome outcome;
        private long deathTick;

        private Attempt(UUID uuid, Statue statue, Vec3 position) {
            this.uuid = uuid;
            this.statue = statue;
            this.position = position;
        }

        UUID uuid() { return uuid; }
        Statue statue() { return statue; }
        Vec3 position() { return position; }
        boolean dead() { return dead; }
        boolean loaded() { return loaded; }
        Outcome outcome() { return outcome; }
    }

    enum Statue {
        RED("Red", 0xFFFF5555, new Vec3(27, 14, 59), new BlockPos(32, 22, 59)),
        ORANGE("Orange", 0xFFFFAA00, new Vec3(85, 14, 56), new BlockPos(80, 23, 56)),
        GREEN("Green", 0xFF55FF55, new Vec3(27, 14, 94), new BlockPos(32, 23, 94)),
        BLUE("Blue", 0xFF5555FF, new Vec3(84, 14, 94), new BlockPos(79, 23, 94)),
        PURPLE("Purple", 0xFFAA00AA, new Vec3(56, 14, 125), new BlockPos(56, 22, 120));

        private final String label;
        private final int color;
        private final Vec3 spawn;
        private final BlockPos statueBlock;
        private final AABB range;

        Statue(String label, int color, Vec3 spawn, BlockPos statueBlock) {
            this.label = label;
            this.color = color;
            this.spawn = spawn;
            this.statueBlock = statueBlock;
            range = new AABB(spawn.x - 13.5, 6, spawn.z - 13.5, spawn.x + 13.5, 29.5, spawn.z + 13.5);
        }

        String label() { return label; }
        int color() { return color; }
        Vec3 spawn() { return spawn; }
        BlockPos statueBlock() { return statueBlock; }
        AABB range() { return range; }

        boolean contains(Vec3 origin) {
            return origin != null && origin.x >= range.minX && origin.x <= range.maxX
                && origin.y >= range.minY && origin.y <= range.maxY
                && origin.z >= range.minZ && origin.z <= range.maxZ;
        }

        static Statue atSpawn(Vec3 position) {
            if (position == null) return null;
            Statue match = null;
            for (Statue statue : values()) {
                if (statue.spawn.distanceToSqr(position) > 16) continue;
                if (match != null) return null;
                match = statue;
            }
            return match;
        }
    }
}
