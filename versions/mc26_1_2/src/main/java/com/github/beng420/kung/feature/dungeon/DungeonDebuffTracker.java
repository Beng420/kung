package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.config.category.DungeonConfig.DragonDebuffScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Applied server ticks, never client frames or nominal milliseconds, own all offsets. */
final class DungeonDebuffTracker {
    static final int SPRAY_TICKS = 100;
    static final int MAX_MARKERS = 256;
    static final int DEBUFF_TICKS = 40;
    private final Map<UUID, Long> sprayedUntil = new LinkedHashMap<>();
    private final Map<UUID, Dragon> dragons = new LinkedHashMap<>();
    private final List<Dragon> spawnWave = new ArrayList<>();
    private final List<Long> pendingHits = new ArrayList<>();
    private long waveTick;
    private Vec3 wavePlayerPosition;
    private Dragon selectedDragon;
    private long tick;

    long tick() { return tick; }

    boolean advance() {
        tick++;
        sprayedUntil.values().removeIf(expiry -> expiry <= tick);
        // ponytail: group adjacent spawns for two ticks; replace with a wave ID if one becomes available.
        if (spawnWave.isEmpty() || tick - waveTick <= 2) return false;
        selectNearestStatue();
        return true;
    }

    void reset() {
        tick = 0;
        sprayedUntil.clear();
        clearDragons();
    }

    void clearDragons() {
        dragons.clear();
        spawnWave.clear();
        pendingHits.clear();
        wavePlayerPosition = null;
        selectedDragon = null;
    }
    Set<UUID> highlighted() { return Set.copyOf(sprayedUntil.keySet()); }

    boolean spray(UUID target, long observedTick) {
        if (observedTick + SPRAY_TICKS > tick) {
            if (sprayedUntil.size() >= MAX_MARKERS && !sprayedUntil.containsKey(target)) return false;
            sprayedUntil.merge(target, observedTick + SPRAY_TICKS, Math::max);
        }
        Dragon dragon = dragons.get(target);
        if (dragon == null || dragon.ended || dragon.sprayTick >= 0) return false;
        dragon.sprayTick = Math.max(0, observedTick - dragon.spawnTick);
        return true;
    }

    Dragon spawn(UUID uuid, String name) {
        return spawn(uuid, name, null);
    }

    Dragon spawn(UUID uuid, String name, Vec3 playerPosition) {
        Dragon previous = dragons.get(uuid);
        if (previous != null) return previous; // A client-range reload is not another server spawn.
        if (dragons.size() >= 32) {
            var retired = dragons.entrySet().stream().filter(entry -> entry.getValue().ended).findFirst();
            if (retired.isEmpty()) return null;
            dragons.remove(retired.get().getKey());
        }
        Dragon dragon = new Dragon(name, tick);
        dragons.put(uuid, dragon);
        if (spawnWave.isEmpty()) {
            waveTick = tick;
            wavePlayerPosition = playerPosition;
            selectedDragon = null;
        }
        spawnWave.add(dragon);
        return dragon;
    }

    List<Dragon> observations() { return List.copyOf(dragons.values()); }

    List<Dragon> displayedDragons(DragonDebuffScope scope) {
        if (scope == DragonDebuffScope.ALL_DRAGONS) return latestDragons();
        return selectedDragon == null ? List.of() : List.of(selectedDragon);
    }

    private void selectNearestStatue() {
        double closest = Double.POSITIVE_INFINITY;
        for (Dragon dragon : spawnWave) {
            dragon.selectionPending = false;
            if (wavePlayerPosition == null) continue;
            double distance = statueDistanceSquared(dragon.name, wavePlayerPosition);
            if (distance < closest || distance == closest && selectedDragon != null
                && dragon.name.compareTo(selectedDragon.name) < 0) {
                closest = distance;
                selectedDragon = dragon;
            }
        }
        if (selectedDragon != null) {
            selectedDragon.nearestInWave = true;
            for (long hitTick : pendingHits) recordHit(selectedDragon, hitTick);
        }
        pendingHits.clear();
        spawnWave.clear();
    }

    private static double statueDistanceSquared(String name, Vec3 position) {
        Vec3 statue = switch (name) {
            case "Red" -> new Vec3(32, 0, 59);
            case "Orange" -> new Vec3(80, 0, 56);
            case "Green" -> new Vec3(32, 0, 94);
            case "Blue" -> new Vec3(79, 0, 94);
            case "Purple" -> new Vec3(56, 0, 120);
            default -> null;
        };
        // Statues have different heights; horizontal distance reflects the player's assignment.
        if (statue == null) return Double.POSITIVE_INFINITY;
        double dx = position.x - statue.x, dz = position.z - statue.z;
        return dx * dx + dz * dz;
    }

    List<Dragon> latestDragons() {
        Map<String, Dragon> latest = new LinkedHashMap<>();
        for (Dragon dragon : dragons.values()) latest.put(dragon.name, dragon);
        return List.copyOf(latest.values());
    }

    boolean arrowHit() {
        if (!spawnWave.isEmpty()) {
            if (pendingHits.size() >= 128) return false;
            pendingHits.add(tick);
            return true;
        }
        return selectedDragon != null && !selectedDragon.ended && recordHit(selectedDragon, tick);
    }

    private static boolean recordHit(Dragon dragon, long hitTick) {
        long offset = hitTick - dragon.spawnTick;
        if (offset < 0 || offset > DEBUFF_TICKS || dragon.ended && hitTick > dragon.endTick) return false;
        // ponytail: the shooter ping has no target/weapon ID; assign it once to the spawn-selected statue.
        dragon.arrows.add(offset);
        return true;
    }

    static boolean localArrowFeedback(String sound, Vec3 origin, Vec3 player) {
        // ponytail: nearby feedback is a filter, not proof of ownership; the packet has no shooter ID.
        return "minecraft:entity.arrow.hit_player".equals(sound) && origin.distanceToSqr(player) <= 16;
    }

    Dragon end(UUID uuid, boolean killed) {
        Dragon dragon = dragons.get(uuid);
        if (dragon == null || dragon.ended) return null;
        dragon.ended = true;
        dragon.endTick = tick;
        dragon.killed = killed;
        sprayedUntil.remove(uuid);
        return dragon;
    }

    /** Reject overlapping candidates instead of making one ice marker freeze a whole crowd. */
    static UUID uniqueTarget(Vec3 marker, List<Target> targets) {
        // ponytail: proximity is inferred; a future linked-entity signal should replace this match.
        UUID result = null;
        double nearest = Double.POSITIVE_INFINITY;
        double second = Double.POSITIVE_INFINITY;
        for (Target target : targets) {
            double distance = target.dragon ? target.position.distanceTo(marker)
                : Math.sqrt(target.bounds.distanceToSqr(marker));
            if (distance > (target.dragon ? 8 : 1.5)) continue;
            if (distance < nearest) {
                second = nearest;
                nearest = distance;
                result = target.uuid;
            } else second = Math.min(second, distance);
        }
        return second - nearest < 0.5 ? null : result;
    }

    static String dragonName(Vec3 position) {
        if (position.y < 0 || position.y > 40) return null;
        String[] names = {"Red", "Orange", "Green", "Blue", "Purple"};
        double[][] bounds = {{14.5, 45.5, 39.5, 70.5}, {72, 47, 102, 77}, {7, 80, 37, 110},
            {71.5, 82.5, 96.5, 107.5}, {45.5, 113.5, 68.5, 136.5}};
        for (int i = 0; i < bounds.length; i++) {
            double[] b = bounds[i];
            if (position.x >= b[0] && position.z >= b[1] && position.x <= b[2] && position.z <= b[3]) return names[i];
        }
        return null;
    }

    record Target(UUID uuid, Vec3 position, AABB bounds, boolean dragon) { }

    static final class Dragon {
        final String name;
        final long spawnTick;
        final Hits arrows = new Hits();
        long sprayTick = -1;
        long endTick;
        boolean ended;
        boolean killed;
        boolean selectionPending = true;
        boolean nearestInWave;
        boolean resultAnnounced;

        Dragon(String name, long spawnTick) {
            this.name = name;
            this.spawnTick = spawnTick;
        }

        boolean shownIn(DragonDebuffScope scope) {
            return scope == DragonDebuffScope.ALL_DRAGONS || !selectionPending && nearestInWave;
        }

        String summary(long now) {
            return name + ": Time: " + String.format(Locale.ROOT, "%.2fs", ((ended ? endTick : now) - spawnTick) / 20.0)
                + " | Arrows: " + (nearestInWave ? arrows.count : "--")
                + " | Sprayed: " + (sprayTick < 0 ? "no" : sprayTick + "t");
        }

        String details() {
            return name + " dragon\n"
                + "Arrows (first " + DEBUFF_TICKS + " ticks): " + (nearestInWave ? arrows.describe() : "-- (another statue selected)") + "\n"
                + "Ice Spray: " + (sprayTick < 0 ? "no matching ice marker received" : sprayTick + "t after spawn") + "\n"
                + "End: " + (killed ? "death confirmed" : "entity removed; death not confirmed") + "\n"
                + "Time uses observed server ticks; 20t = 1 nominal second.\n"
                + "Arrow feedback is assigned to your nearest spawning statue; it carries no target or bow ID.\n"
                + "Ice Spray requires a matching server ice marker. Missing markers can miss a spray.";
        }
    }

    static final class Hits {
        int count;
        long first = -1;
        long fifth = -1;
        long last = -1;
        private final List<Long> offsets = new ArrayList<>();

        void add(long offset) {
            count++;
            if (first < 0) first = offset;
            if (count == 5) fifth = offset;
            last = offset;
            if (offsets.size() < 64) offsets.add(offset);
        }

        String describe() {
            if (count == 0) return "0";
            return count + " | first " + first + "t | fifth " + (fifth < 0 ? "--" : fifth + "t")
                + " | last " + last + "t | span " + (last - first) + "t | " + rate() + "/20t\n"
                + "Hit ticks: " + offsets + (count > offsets.size() ? " (first 64)" : "")
                + "\nGaps (ticks): " + gaps();
        }

        private List<Long> gaps() {
            List<Long> gaps = new ArrayList<>();
            for (int i = 1; i < offsets.size(); i++) gaps.add(offsets.get(i) - offsets.get(i - 1));
            return gaps;
        }

        String rate() {
            return count < 2 || last == first ? "--" : String.format(Locale.ROOT, "%.1f", (count - 1) * 20.0 / (last - first));
        }
    }
}
