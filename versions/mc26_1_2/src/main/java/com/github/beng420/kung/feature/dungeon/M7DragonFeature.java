package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.config.category.DungeonConfig;
import java.util.List;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.feature.dungeon.M7DragonTracker.Statue;
import com.github.beng420.kung.runtime.KungDeveloperAccess;
import com.github.beng420.kung.runtime.KungFileLayout;
import com.github.beng420.kung.skyblock.HypixelDungeonFloor;
import com.github.beng420.kung.skyblock.HypixelInstanceTracker;
import com.github.beng420.kung.util.KungDebugRecorder;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Local arena guidance; only server evidence can confirm a statue result. */
public final class M7DragonFeature extends ConfigurableFeature<DungeonConfig> {
    public static final M7DragonFeature INSTANCE = new M7DragonFeature();
    private final M7DragonTracker tracker = new M7DragonTracker();
    private final Map<UUID, EnderDragon> entities = new LinkedHashMap<>();
    private final Map<Statue, Boolean> statuePresent = new EnumMap<>(Statue.class);
    /** Resolved statues awaiting one shared title; EnumMap dedupes and keeps arena order. */
    private final Map<Statue, Boolean> pendingTitles = new EnumMap<>(Statue.class);
    /** Tick of the last pre-spawn particle burst seen at each anchor. */
    private final Map<Statue, Long> spawnHints = new EnumMap<>(Statue.class);
    /** Server tick each dragon was first seen, and its per-tick part positions while being recorded. */
    private final Map<UUID, Long> spawnTicks = new java.util.HashMap<>();
    private final Map<UUID, List<double[]>> timelineRows = new java.util.HashMap<>();
    /** Newest recorded flight per statue, and how many flights the file already holds per statue. */
    private final Map<Statue, M7DragonAim.Timeline> timelines = new EnumMap<>(Statue.class);
    private final Map<Statue, Integer> timelineCounts = new EnumMap<>(Statue.class);
    private boolean timelinesLoaded;
    /** Flights repeat to within a quarter block, so a few per statue are plenty. */
    private static final int MAX_TIMELINES = 5;
    private static final String TIMELINE_FILE = "m7-dragon-timelines.jsonl";
    /** Flight path per statue, from spawn to the point the dragon left its range. */
    private final Map<Statue, List<Vec3>> trails = new EnumMap<>(Statue.class);
    private final java.util.EnumSet<Statue> trailsClosed = java.util.EnumSet.noneOf(Statue.class);
    private final java.util.Set<UUID> hitboxesRecorded = new java.util.HashSet<>();
    /** Most recent recorded start paths per statue, newest last, capped at MAX_RECORDED_PATHS. */
    private final Map<Statue, java.util.ArrayDeque<List<Vec3>>> recordedPaths = new EnumMap<>(Statue.class);
    private DungeonConfig.DragonPart recordedPathsPart;
    private static final int MAX_RECORDED_PATHS = 5;
    private static final int MAX_TRAIL_POINTS = 256;
    private final List<String> pendingMessages = new ArrayList<>();
    private final ArrayDeque<String> developerSamples = new ArrayDeque<>();
    private final ArrayDeque<String> developerEvents = new ArrayDeque<>();
    private final Map<ParticleType<?>, Long> particleSamples = new LinkedHashMap<>();
    private String lastDeveloperReport = "";
    private long epoch = Long.MIN_VALUE;
    private long witherKingEpoch = Long.MIN_VALUE;
    private long lastSampleTick = Long.MIN_VALUE;
    private long lastWaypointTrace = Long.MIN_VALUE;

    private M7DragonFeature() { super(config -> config.dungeon); }

    @Override public boolean isEnabled() {
        return initialized() && KungDeveloperAccess.allowed()
            && (config().m7DragonHelperEnabled() || config().devDragonDiagnosticsEnabled());
    }

    @Override protected void onInitialize() {
        M7DragonRenderer.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onReset());
        DungeonServerTickEvents.register(() -> { if (ready()) publish(tracker.advance()); });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            observeMessage(message.getString(), overlay);
            return true;
        });
    }

    @Override protected void onReset() {
        clearObservations();
        epoch = Long.MIN_VALUE;
        witherKingEpoch = Long.MIN_VALUE;
    }

    @Override protected void onShutdown() { onReset(); }

    private void clearObservations() {
        spawnHints.clear();
        hitboxesRecorded.clear();
        trails.clear();
        trailsClosed.clear();
        if (!KungDeveloperAccess.allowed()) lastDeveloperReport = "";
        else if (!developerSamples.isEmpty()) lastDeveloperReport = currentDeveloperReport();
        tracker.reset();
        entities.clear();
        statuePresent.clear();
        pendingMessages.clear();
        developerSamples.clear();
        developerEvents.clear();
        lastSampleTick = Long.MIN_VALUE;
        particleSamples.clear();
        spawnTicks.clear();
        timelineRows.clear();
    }

    private void synchronizeEpoch() {
        long current = HypixelInstanceTracker.INSTANCE.instanceEpoch();
        if (epoch == current) return;
        clearObservations();
        epoch = current;
        witherKingEpoch = Long.MIN_VALUE;
    }

    static boolean contextAllowed(boolean catacombs, HypixelDungeonFloor floor, boolean witherKing) {
        return catacombs && (floor.known() ? floor.floor() == 7 && floor.masterMode() : witherKing);
    }

    private boolean ready() {
        synchronizeEpoch();
        var instance = HypixelInstanceTracker.INSTANCE;
        if (!isEnabled() || Minecraft.getInstance().level == null
            || !contextAllowed(instance.catacombs(), instance.dungeonFloor(), witherKingEpoch == epoch)) {
            clearObservations();
            return false;
        }
        if (!config().devDragonDiagnosticsEnabled()) {
            if (KungDeveloperAccess.allowed() && !developerSamples.isEmpty()) lastDeveloperReport = currentDeveloperReport();
            developerSamples.clear();
            developerEvents.clear();
            particleSamples.clear();
            if (!KungDeveloperAccess.allowed()) lastDeveloperReport = "";
        }
        return true;
    }

    private void observeMessage(String message, boolean overlay) {
        if (overlay) return;
        synchronizeEpoch();
        String clean = DungeonLifecycleSignals.clean(message);
        if (!HypixelInstanceTracker.INSTANCE.catacombs() || !clean.startsWith("[BOSS] Wither King:")) return;
        witherKingEpoch = epoch;
        if (!ready()) return;
        // Resolve after the packet batch so a chat packet preceding death metadata is not misassigned.
        if (pendingMessages.size() < 16) pendingMessages.add(clean);
        trace("boss-message " + clean);
    }

    public void observeSpawn(Entity entity) {
        if (!(entity instanceof EnderDragon dragon) || !ready()) return;
        var previous = tracker.attempt(entity.getUUID());
        Statue statue = previous == null ? Statue.atSpawn(entity.position()) : previous.statue();
        if (statue == null) {
            sample("unidentified-spawn id=" + entity.getId() + " position=" + entity.position());
            return;
        }
        publish(tracker.spawn(entity.getUUID(), statue, entity.position()));
        if (tracker.attempt(entity.getUUID()) == null) return;
        entities.put(entity.getUUID(), dragon);
        if (previous == null) spawnTicks.put(entity.getUUID(), tracker.tick());
        // A re-sent entity (chunk reload, re-track) is a position update, not a spawn: logging it
        // as "spawn" made mid-flight coordinates look like imprecise spawn anchors.
        trace((previous == null ? "spawn " : "reobserve ") + statue.label()
            + " id=" + entity.getId() + " uuid=" + entity.getUUID()
            + " position=" + entity.position());
    }

    public void observeData(Entity entity) {
        if (entity instanceof EnderDragon dragon && dragon.getHealth() <= 0) observeDeath(entity);
    }

    public void observeDeath(Entity entity) {
        if (!(entity instanceof EnderDragon) || !ready()) return;
        var attempt = tracker.attempt(entity.getUUID());
        if (attempt == null) {
            if (tracker.unknownDeath(entity.getUUID())) trace("unidentified-death uuid=" + entity.getUUID()
                + " position=" + DungeonDebuffFeature.matchingPosition(entity));
            return;
        }
        if (attempt.dead()) return;
        tracker.move(entity.getUUID(), DungeonDebuffFeature.matchingPosition(entity));
        tracker.death(entity.getUUID());
        trace("death " + attempt.statue().label() + " uuid=" + entity.getUUID()
            + " position=" + attempt.position() + " estimatedInside=" + attempt.statue().contains(attempt.position()));
    }

    private void tick(Minecraft client) {
        if (!ready()) return;
        var iterator = entities.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            EnderDragon dragon = entry.getValue();
            if (dragon.getHealth() <= 0) {
                observeDeath(dragon);
                // The death animation lifts the corpse straight up ~15 blocks in place. That is not
                // flight and it decided nothing, so the path ends at 0 HP, not at the range exit.
                closeTrail(entry.getKey());
                flushTimeline(entry.getKey());
            }
            if (dragon.isRemoved()) {
                // A dragon that dies inside its range never trips the exit condition, so its path
                // was silently dropped. Flush on removal too - three of five went missing this way.
                closeTrail(entry.getKey());
                flushTimeline(entry.getKey());
                tracker.unload(entry.getKey());
                iterator.remove();
                continue;
            }
            tracker.move(entry.getKey(), DungeonDebuffFeature.matchingPosition(dragon));
            recordTimeline(entry.getKey(), dragon);
            recordTrail(dragon);
            recordHitbox(dragon);
        }
        // Five fixed blocks, only while the arena is loaded; never request chunks or scan structures.
        if (inArena(client)) for (Statue statue : Statue.values()) {
            if (!client.level.hasChunkAt(statue.statueBlock())) continue;
            boolean present = !client.level.getBlockState(statue.statueBlock()).isAir();
            Boolean previous = statuePresent.put(statue, present);
            if (previous != null && previous && !present) {
                trace("statue-broken " + statue.label() + " block=" + statue.statueBlock());
                publish(tracker.statueBroken(statue));
            }
        }
        for (String message : pendingMessages) publish(tracker.confirmMessage(message));
        pendingMessages.clear();
        if (config().devDragonDiagnosticsEnabled()
            && DungeonScanSchedule.due(tracker.tick(), lastSampleTick, 5)) {
            lastSampleTick = tracker.tick();
            capturePositions();
        }
        flushTitles(client);
    }

    /** One title per tick for every statue that resolved in it, so a double kill shows both. */
    private void flushTitles(Minecraft client) {
        if (pendingTitles.isEmpty()) return;
        if (client.gui == null) {
            pendingTitles.clear();
            return;
        }
        MutableComponent line = Component.empty();
        boolean first = true;
        for (var entry : pendingTitles.entrySet()) {
            if (!first) line.append(Component.literal("   "));
            first = false;
            boolean counts = entry.getValue();
            line.append(Component.literal(entry.getKey().label()).withColor(entry.getKey().color() & 0xFFFFFF))
                .append(Component.literal(counts ? " counts" : " unsure")
                    .withColor(counts ? 0x55FF55 : 0xFFAA00));
        }
        pendingTitles.clear();
        // Subtitle slot rather than title: it draws at half the scale.
        client.gui.setTimes(0, 30, 5);
        client.gui.setTitle(Component.empty());
        client.gui.setSubtitle(line);
    }

    private static boolean inArena(Minecraft client) {
        if (client.player == null || !HypixelInstanceTracker.INSTANCE.positionKnown()) return false;
        Vec3 position = client.player.position();
        return position.x >= 0 && position.x <= 120 && position.y >= 0 && position.y < 45
            && position.z >= 25 && position.z <= 150;
    }

    List<M7DragonRenderer.Marker> renderMarkers(float partialTick) {
        if (!ready()) return List.of();
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || !inArena(client)) return List.of();
        List<M7DragonRenderer.Marker> markers = new ArrayList<>();
        for (Statue statue : Statue.values()) {
            if (config().m7DragonHelperEnabled() && config().dragonSpawnMarkersEnabled()) {
                if (config().dragonMarkerMode() == DungeonConfig.DragonMarkerMode.SKELETON) {
                    addSkeleton(markers, statue, partialTick);
                } else {
                    for (DungeonConfig.DragonPart part : DungeonConfig.DragonPart.values()) {
                        if (config().dragonCorePart(part)) {
                            markers.add(point(corePoint(statue, part, partialTick), statue.color(), spawning(statue)));
                        }
                    }
                }
            }
            if (config().m7DragonHelperEnabled() && config().dragonStatueBoxesEnabled() && !tracker.statueCounted(statue)) {
                var attempt = tracker.latest(statue);
                int color = attempt == null || attempt.dead() || !attempt.loaded() ? 0xFFAAAAAA
                    : statue.contains(attempt.position()) ? 0xFF55FF55 : 0xFFFF5555;
                markers.add(new M7DragonRenderer.Marker(statue.range(), color, 2.0F));
            }
        }
        if (config().m7DragonHelperEnabled()) addAimMarkers(markers, client, partialTick);
        if (config().devDragonDiagnosticsEnabled()) for (EnderDragon dragon : entities.values()) {
            if (dragon.isRemoved() || !dragon.isAlive()) continue;
            // White: received origin used by the estimate; yellow: interpolated body-box center.
            markers.add(point(DungeonDebuffFeature.matchingPosition(dragon), 0xFFFFFFFF));
            markers.add(point(dragon.getBoundingBox().getCenter()
                .add(dragon.getPosition(partialTick).subtract(dragon.position())), 0xFFFFFF55));
        }
        return markers;
    }

    /** Box Centre is the classic waypoint; every other part rides its own hitbox, or its spawn pose before the dragon exists. */
    private Vec3 corePoint(Statue statue, DungeonConfig.DragonPart part, float partialTick) {
        if (part == DungeonConfig.DragonPart.BOX) return waypoint(statue, partialTick);
        EnderDragon dragon = liveDragon(statue);
        if (dragon == null) {
            double[] pose = SPAWN_PARTS[part.part()];
            return statue.spawn().add(pose[0] + pose[3] / 2, pose[1] + pose[4] / 2, pose[2] + pose[5] / 2);
        }
        return trailPoint(dragon, part).add(dragon.getPosition(partialTick).subtract(dragon.position()));
    }

    /** Waypoint rests on the spawn anchor until that statue's dragon is in the air, then rides it. */
    private Vec3 waypoint(Statue statue, float partialTick) {
        var attempt = tracker.latest(statue);
        if (attempt == null || attempt.dead()) return spawnCentre(statue);
        EnderDragon dragon = entities.get(attempt.uuid());
        if (dragon == null || dragon.isRemoved() || !dragon.isAlive()) return spawnCentre(statue);
        // getPosition is the entity origin, which sits at the dragon's feet. Ride the body-box
        // centre instead, carrying over the interpolation delta so it does not stutter.
        Vec3 centre = dragonCentre(dragon)
            .add(dragon.getPosition(partialTick).subtract(dragon.position()));
        if (DungeonScanSchedule.due(tracker.tick(), lastWaypointTrace, 40)) {
            lastWaypointTrace = tracker.tick();
            trace("waypoint " + statue.label() + " origin=" + dragon.position()
                + " box=" + dragon.getBoundingBox() + " drawn=" + centre);
        }
        return centre;
    }

    /**
     * Extends the flight path while the dragon's origin is still inside its statue range, then
     * adds the first outside point and stops. Points are kept at least half a block apart, which
     * bounds the list without a timer - a dragon crossing a 27-block range leaves a few dozen.
     */
    /**
     * Dumps every part hitbox of a dragon once, as origin-relative x/y/z plus width/height/depth.
     * The body is the part you want the core inside, and its centre follows from its own box -
     * no eyeballing, no slider. One line per dragon per run.
     */
    private void recordHitbox(EnderDragon dragon) {
        if (!hitboxesRecorded.add(dragon.getUUID())) return;
        var attempt = tracker.attempt(dragon.getUUID());
        if (attempt == null) return;
        Vec3 origin = dragon.position();
        StringBuilder line = new StringBuilder("{\"statue\":\"").append(attempt.statue().name())
            .append("\",\"tick\":").append(tracker.tick()).append(",\"parts\":[");
        int index = 0;
        for (var part : dragon.getSubEntities()) {
            AABB box = part.getBoundingBox();
            if (index > 0) line.append(',');
            line.append("{\"i\":").append(index++)
                .append(",\"x\":").append(round(box.minX - origin.x))
                .append(",\"y\":").append(round(box.minY - origin.y))
                .append(",\"z\":").append(round(box.minZ - origin.z))
                .append(",\"w\":").append(round(box.getXsize()))
                .append(",\"h\":").append(round(box.getYsize()))
                .append(",\"d\":").append(round(box.getZsize())).append('}');
        }
        AABB own = dragon.getBoundingBox();
        line.append("],\"own\":{\"y\":").append(round(own.minY - origin.y))
            .append(",\"h\":").append(round(own.getYsize())).append("}}");
        appendLine("m7-dragon-hitboxes.jsonl", line.toString());
    }

    private void closeTrail(UUID uuid) {
        var attempt = tracker.attempt(uuid);
        if (attempt == null || !trailsClosed.add(attempt.statue())) return;
        recordPath(attempt.statue(), trails.getOrDefault(attempt.statue(), List.of()));
    }

    /**
     * Where the core marker rides. Part hitboxes describe collision; this box is what lines up
     * with the model. The flight path follows the configured part instead (trailPoint).
     */
    /**
     * Where dragonCentre will be the moment this statue's dragon spawns: its box sits on the
     * anchor and is centred horizontally, so the centre is half the dragon's height up. Without
     * this the resting core sat on the raw anchor - four blocks below the first trail point.
     */
    static Vec3 spawnCentre(Statue statue) {
        return statue.spawn().add(0, net.minecraft.world.entity.EntityType.ENDER_DRAGON.getHeight() / 2.0, 0);
    }

    static Vec3 dragonCentre(EnderDragon dragon) {
        return dragon.getBoundingBox().getCenter();
    }

    static Vec3 trailPoint(EnderDragon dragon, DungeonConfig.DragonPart part) {
        var parts = dragon.getSubEntities();
        return part.part() >= 0 && part.part() < parts.length
            ? parts[part.part()].getBoundingBox().getCenter() : dragonCentre(dragon);
    }

    /** Box Centre keeps the original file, so paths recorded before the switch stay usable. */
    static String pathsFile(DungeonConfig.DragonPart part) {
        return part == DungeonConfig.DragonPart.BOX ? "m7-dragon-paths.jsonl"
            : "m7-dragon-paths-" + part.name().toLowerCase(java.util.Locale.ROOT) + ".jsonl";
    }

    /** One row per server tick for the first TIMELINE_TICKS after spawn: every part, anchor-relative. */
    private void recordTimeline(UUID uuid, EnderDragon dragon) {
        Long spawn = spawnTicks.get(uuid);
        var attempt = tracker.attempt(uuid);
        if (spawn == null || attempt == null || attempt.dead()) return;
        long offset = tracker.tick() - spawn;
        if (offset >= M7DragonAim.TIMELINE_TICKS) {
            flushTimeline(uuid, true);
            return;
        }
        List<double[]> rows = timelineRows.computeIfAbsent(uuid, ignored -> new ArrayList<>());
        // Two client ticks can share a server tick; the first one wins.
        if (!rows.isEmpty() && rows.getLast()[0] >= offset) return;
        // Parts are simulated from the smoothed body; shift them onto the last server position.
        Vec3 lag = DungeonDebuffFeature.matchingPosition(dragon).subtract(dragon.position());
        var parts = DungeonConfig.DragonPart.values();
        double[] row = new double[1 + parts.length * 3];
        row[0] = offset;
        for (var part : parts) {
            Vec3 at = trailPoint(dragon, part).add(lag).subtract(attempt.statue().spawn());
            row[1 + part.ordinal() * 3] = at.x;
            row[2 + part.ordinal() * 3] = at.y;
            row[3 + part.ordinal() * 3] = at.z;
        }
        rows.add(row);
    }

    private void flushTimeline(UUID uuid) { flushTimeline(uuid, false); }

    /** Keeps only whole windows: a dragon killed early leaves a flight that stops, and the aim would freeze there. */
    private void flushTimeline(UUID uuid, boolean complete) {
        Long spawn = spawnTicks.remove(uuid);
        List<double[]> rows = timelineRows.remove(uuid);
        var attempt = tracker.attempt(uuid);
        if (!complete || spawn == null || rows == null || attempt == null) return;
        loadTimelines();
        Statue statue = attempt.statue();
        Long hint = spawnHints.get(statue);
        int hintTicks = hint == null || spawn - hint <= 0 || spawn - hint > 300 ? -1 : (int) (spawn - hint);
        var timeline = new M7DragonAim.Timeline(statue, hintTicks, List.copyOf(rows));
        timelines.put(statue, timeline);
        trace("timeline " + statue.label() + " rows=" + rows.size() + " hint=" + hintTicks);
        if (timelineCounts.merge(statue, 1, Integer::sum) <= MAX_TIMELINES) appendLine(TIMELINE_FILE, M7DragonAim.line(timeline));
    }

    private void loadTimelines() {
        if (timelinesLoaded) return;
        timelinesLoaded = true;
        Minecraft client = Minecraft.getInstance();
        Path gameDirectory = client == null ? Path.of(".") : client.gameDirectory.toPath();
        Path file = new KungFileLayout(gameDirectory, gameDirectory.resolve("config"))
            .dungeonDataDirectory().resolve(TIMELINE_FILE);
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                var timeline = M7DragonAim.parse(line);
                if (timeline == null) continue;
                timelines.put(timeline.statue(), timeline);
                timelineCounts.merge(timeline.statue(), 1, Integer::sum);
            }
        } catch (IOException exception) {
            trace("timeline-load-failed " + exception.getMessage());
        }
    }

    /** Terminator for Archer/Berserker, Last Breath for everyone else, unless picked by hand. */
    private DungeonConfig.DragonAimMode aimMode() {
        var mode = config().dragonAimMode();
        if (mode != DungeonConfig.DragonAimMode.AUTO) return mode;
        var selfClass = services().dungeonStateTracker().runStats().selfDungeonClass();
        return selfClass == DungeonRunStats.DungeonClass.ARCHER || selfClass == DungeonRunStats.DungeonClass.BERSERKER
            ? DungeonConfig.DragonAimMode.TERMINATOR : DungeonConfig.DragonAimMode.LAST_BREATH;
    }

    /** Terminator always fires at full speed; Last Breath fires on release, at whatever the draw has reached. */
    private static double arrowSpeed(DungeonConfig.DragonAimMode mode, net.minecraft.world.entity.player.Player player) {
        if (mode == DungeonConfig.DragonAimMode.LAST_BREATH && player.isUsingItem()
            && player.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem) {
            return M7DragonAim.ARROW_SPEED * net.minecraft.world.item.BowItem.getPowerForTime(player.getTicksUsingItem());
        }
        return M7DragonAim.ARROW_SPEED;
    }

    /** Ticks since this statue's dragon spawned, negative while its burst counts down; null outside the aim window. */
    private Double ticksSinceSpawn(Statue statue) {
        var attempt = tracker.latest(statue);
        Long spawn = attempt == null || attempt.dead() ? null : spawnTicks.get(attempt.uuid());
        if (spawn != null) return (double) (tracker.tick() - spawn);
        Long hint = spawnHints.get(statue);
        if (hint == null) return null;
        long sinceHint = tracker.tick() - hint;
        // Up to a second late: the burst-to-spawn gap varies by a few ticks between spawns.
        return sinceHint <= M7DragonAim.HINT_TO_SPAWN_TICKS + 20 ? (double) (sinceHint - M7DragonAim.HINT_TO_SPAWN_TICKS) : null;
    }

    private void addAimMarkers(List<M7DragonRenderer.Marker> markers, Minecraft client, float partialTick) {
        var mode = aimMode();
        if (mode == DungeonConfig.DragonAimMode.OFF || client.player == null) return;
        loadTimelines();
        Vec3 eye = client.player.getEyePosition(partialTick);
        double speed = arrowSpeed(mode, client.player);
        for (Statue statue : Statue.values()) {
            Double since = ticksSinceSpawn(statue);
            if (since == null) continue;
            var timeline = timelines.get(statue);
            // ponytail: without a recorded flight there is no lead - aim at the body where it spawns, then
            // where it is. Purple is killed at spawn, so it rarely needs more.
            Vec3 body = timeline == null ? corePoint(statue, DungeonConfig.DragonPart.BODY, partialTick) : null;
            var aim = M7DragonAim.aim(eye, tick -> body != null ? body : timeline.body(tick), since, speed);
            if (aim == null) continue;
            // White once an arrow fired now lands on a dragon that exists; the Terminator marker grows a
            // second before spawn, the moment to start running in.
            boolean runIn = mode == DungeonConfig.DragonAimMode.TERMINATOR && since < 0 && since >= -20;
            markers.add(point(aim.point(), aim.arrivalTick() >= 0 ? 0xFFFFFFFF : 0xFF888888, runIn));
        }
    }

    private void recordTrail(EnderDragon dragon) {
        loadRecordedPaths();
        var attempt = tracker.attempt(dragon.getUUID());
        if (attempt == null) return;
        Statue statue = attempt.statue();
        if (trailsClosed.contains(statue)) return;
        List<Vec3> points = trails.computeIfAbsent(statue, ignored -> new ArrayList<>());
        if (extendTrail(points, trailPoint(dragon, config().dragonTrailPart()), statue.contains(dragon.position()))) {
            trailsClosed.add(statue);
            recordPath(statue, points);
        }
    }

    /**
     * Appends one path point and reports whether the trail is finished. The exit point is appended
     * before closing, so the line reaches the range boundary instead of stopping short of it.
     * Returns false for a point too close to the previous one to be worth keeping.
     */
    static boolean extendTrail(List<Vec3> points, Vec3 centre, boolean insideRange) {
        if (!points.isEmpty() && points.get(points.size() - 1).distanceToSqr(centre) < 0.25) return false;
        points.add(centre);
        return !insideRange || points.size() >= MAX_TRAIL_POINTS;
    }

    /**
     * Writes one finished path per dragon. Offsets are relative to the spawn anchor so two runs can
     * be compared directly; the whole point of the file is to answer whether the paths repeat at
     * all. If they do not, delete the file and this method with it.
     *
     * <p>Roughly 60 points per dragon at the 0.5-block spacing, so about 10 KB per run.
     */
    private void recordPath(Statue statue, List<Vec3> points) {
        if (points.size() < 2) return;
        StringBuilder line = new StringBuilder("{\"tick\":").append(tracker.tick())
            .append(",\"statue\":\"").append(statue.name()).append("\",\"points\":[");
        for (int i = 0; i < points.size(); i++) {
            Vec3 point = points.get(i);
            if (i > 0) line.append(',');
            line.append('[').append(round(point.x - statue.spawn().x))
                .append(',').append(round(point.y - statue.spawn().y))
                .append(',').append(round(point.z - statue.spawn().z)).append(']');
        }
        line.append("]}");
        appendLine(pathsFile(config().dragonTrailPart()), line.toString());
        rememberPath(statue, List.copyOf(points));
    }

    private void rememberPath(Statue statue, List<Vec3> absolute) {
        var paths = recordedPaths.computeIfAbsent(statue, ignored -> new java.util.ArrayDeque<>());
        paths.addLast(absolute);
        while (paths.size() > MAX_RECORDED_PATHS) paths.removeFirst();
    }

    /**
     * Reads the recorded start paths once per trail part. Stored as offsets from the spawn anchor,
     * so they are rebuilt against it. Only the newest MAX_RECORDED_PATHS per statue survive: enough
     * to see whether the paths repeat, bounded no matter how much M7 gets played.
     */
    private void loadRecordedPaths() {
        var part = config().dragonTrailPart();
        if (part == recordedPathsPart) return;
        if (recordedPathsPart != null) {
            // Switched mid-run: stop this run's trails so no path mixes two parts in one file.
            trails.clear();
            trailsClosed.addAll(java.util.EnumSet.allOf(Statue.class));
        }
        recordedPathsPart = part;
        recordedPaths.clear();
        Minecraft client = Minecraft.getInstance();
        Path gameDirectory = client == null ? Path.of(".") : client.gameDirectory.toPath();
        Path file = new KungFileLayout(gameDirectory, gameDirectory.resolve("config"))
            .dungeonDataDirectory().resolve(pathsFile(part));
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Statue statue;
                com.google.gson.JsonArray points;
                try {
                    var json = com.google.gson.JsonParser.parseString(line).getAsJsonObject();
                    statue = Statue.valueOf(json.get("statue").getAsString());
                    points = json.getAsJsonArray("points");
                } catch (RuntimeException malformed) {
                    continue;
                }
                List<Vec3> absolute = new ArrayList<>(points.size());
                for (var point : points) {
                    var xyz = point.getAsJsonArray();
                    absolute.add(statue.spawn().add(xyz.get(0).getAsDouble(),
                        xyz.get(1).getAsDouble(), xyz.get(2).getAsDouble()));
                }
                if (absolute.size() >= 2) rememberPath(statue, List.copyOf(absolute));
            }
        } catch (IOException exception) {
            trace("path-load-failed " + exception.getMessage());
        }
    }

    private void appendLine(String fileName, String line) {
        Minecraft client = Minecraft.getInstance();
        Path gameDirectory = client == null ? Path.of(".") : client.gameDirectory.toPath();
        Path file = new KungFileLayout(gameDirectory, gameDirectory.resolve("config"))
            .dungeonDataDirectory().resolve(fileName);
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException exception) {
            trace("append-failed " + fileName + " " + exception.getMessage());
        }
    }

    /** Two decimals: the sampling step is 0.5 blocks, so more digits are noise. */
    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    List<M7DragonRenderer.Trail> renderTrails() {
        if (!ready()) return List.of();
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || !inArena(client)
            || !config().m7DragonHelperEnabled() || !config().dragonFlightPathsEnabled()) {
            return List.of();
        }
        loadRecordedPaths();
        List<M7DragonRenderer.Trail> drawn = new ArrayList<>();
        // Recorded paths first and dimmed, so the live one reads on top of them.
        for (var entry : recordedPaths.entrySet()) {
            int dim = (entry.getKey().color() & 0x00FFFFFF) | 0x80000000;
            for (List<Vec3> path : entry.getValue()) drawn.add(new M7DragonRenderer.Trail(path, dim));
        }
        for (var entry : trails.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            drawn.add(new M7DragonRenderer.Trail(List.copyOf(entry.getValue()),
                entry.getKey().color() | 0xFF000000));
        }
        return drawn;
    }

    /**
     * The spawn pose, measured from 11 dragons: {x, y, z, width, height, depth} per part, relative
     * to the spawn anchor. All five statues produced byte-identical layouts, so one table covers
     * them all - dragons always spawn facing the same way.
     */
    private static final double[][] SPAWN_PARTS = {
        {-0.5, 0.0, -7.0, 1, 1, 1},
        {-1.5, 0.0, -7.0, 3, 3, 3},
        {-2.5, 0.0, -3.0, 5, 3, 5},
        {-1.0, 1.5, 2.5, 2, 2, 2},
        {-1.0, 1.5, 4.5, 2, 2, 2},
        {-1.0, 1.5, 6.5, 2, 2, 2},
        {2.5, 2.0, -2.0, 4, 2, 4},
        {-6.5, 2.0, -2.0, 4, 2, 4},
    };

    /** Live part hitboxes while the dragon exists, the measured spawn pose before it does. */
    private void addSkeleton(List<M7DragonRenderer.Marker> markers, Statue statue, float partialTick) {
        int color = statue.color() | 0xFF000000;
        EnderDragon dragon = liveDragon(statue);
        if (dragon == null) {
            Vec3 anchor = statue.spawn();
            for (double[] part : SPAWN_PARTS) {
                markers.add(new M7DragonRenderer.Marker(new AABB(
                    anchor.x + part[0], anchor.y + part[1], anchor.z + part[2],
                    anchor.x + part[0] + part[3], anchor.y + part[1] + part[4],
                    anchor.z + part[2] + part[5]), color, spawning(statue) ? 3.0F : 1.5F));
            }
            return;
        }
        Vec3 smoothing = dragon.getPosition(partialTick).subtract(dragon.position());
        for (var part : dragon.getSubEntities()) {
            markers.add(new M7DragonRenderer.Marker(
                part.getBoundingBox().move(smoothing), color, 1.5F));
        }
    }

    private EnderDragon liveDragon(Statue statue) {
        var attempt = tracker.latest(statue);
        if (attempt == null || attempt.dead()) return null;
        EnderDragon dragon = entities.get(attempt.uuid());
        return dragon == null || dragon.isRemoved() || !dragon.isAlive() ? null : dragon;
    }

    /** True while the anchor is flashing its pre-spawn burst and no dragon has appeared yet. */
    private boolean spawning(Statue statue) {
        Long hint = spawnHints.get(statue);
        if (hint == null || tracker.tick() - hint > 60) return false;
        var attempt = tracker.latest(statue);
        return attempt == null || attempt.dead() || entities.get(attempt.uuid()) == null;
    }

    /** A wireframe needs a little more size than the old filled dot to stay readable across the arena. */
    private static M7DragonRenderer.Marker point(Vec3 center, int color) {
        return point(center, color, false);
    }

    private static M7DragonRenderer.Marker point(Vec3 center, int color, boolean imminent) {
        double radius = imminent ? 0.45 : 0.12;
        return new M7DragonRenderer.Marker(new AABB(center, center).inflate(radius),
            color | 0xFF000000, imminent ? 5.0F : 3.0F, true);
    }

    private void publish(List<M7DragonTracker.Result> results) {
        for (var result : results) {
            String name = result.statue() == null ? "Dragon" : result.statue().label() + " dragon";
            boolean counts = result.outcome() == M7DragonTracker.Outcome.COUNTS;
            String text = name + (counts ? " counts." : " result unknown — no statue confirmation.");
            trace("result " + text + " evidence=" + result.evidence());
            recordDeath(result);
            if (!config().m7DragonHelperEnabled() || !config().dragonCountNotificationsEnabled()) continue;
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) continue;
            // Buffer instead of drawing now: statueBroken and confirmMessage both publish in the
            // same tick, so drawing per result stacked two titles. flushTitles draws one.
            if (result.statue() != null) pendingTitles.put(result.statue(), counts);
        }
    }

    /**
     * Appends one line per resolved dragon so the real counting box can be fitted offline.
     * Runs unattended - no dev command - because the feature is already Beng114-only. A dragon
     * that first times out as UNKNOWN and is later confirmed writes a second line; dedupe by
     * taking the highest tick per (statue, x, y, z).
     */
    /** Offsets are relative to the spawn anchor, which is the frame the counting box is fitted in. */
    static String deathLine(long tick, Statue statue, M7DragonTracker.Outcome outcome, Vec3 at) {
        return "{\"tick\":" + tick
            + ",\"statue\":\"" + statue.name() + "\""
            + ",\"outcome\":\"" + outcome.name() + "\""
            + ",\"x\":" + at.x + ",\"y\":" + at.y + ",\"z\":" + at.z
            + ",\"dx\":" + (at.x - statue.spawn().x)
            + ",\"dy\":" + (at.y - statue.spawn().y)
            + ",\"dz\":" + (at.z - statue.spawn().z)
            + ",\"predictedInside\":" + statue.contains(at) + "}";
    }

    /** One recorded kill, in offsets from its own spawn anchor. */
    record DeathSample(String statue, boolean counts, double dx, double dy, double dz) { }

    private static final Pattern SAMPLE_LINE = Pattern.compile(
        "\"statue\":\"(\\w+)\".*?\"outcome\":\"(\\w+)\".*?"
            + "\"dx\":(-?[\\d.]+),\"dy\":(-?[\\d.]+),\"dz\":(-?[\\d.]+)");

    static List<DeathSample> parseSamples(List<String> lines) {
        List<DeathSample> samples = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = SAMPLE_LINE.matcher(line);
            if (!matcher.find()) continue;
            samples.add(new DeathSample(matcher.group(1), matcher.group(2).equals("COUNTS"),
                Double.parseDouble(matcher.group(3)), Double.parseDouble(matcher.group(4)),
                Double.parseDouble(matcher.group(5))));
        }
        return samples;
    }

    /**
     * Keep a kill only when it tightens the bracket around the real box. A counting kill sits
     * inside, so it is news only if it reaches farther out than every inside sample already held;
     * a non-counting kill sits outside and is news only if it sits closer in than every outside
     * one. Compared per statue and per octant, because the estimate is not symmetric around y and
     * the statue blocks are not placed identically - a cross-octant compare could silently drop a
     * sample that was the only evidence for its side. Grinding M7 therefore adds a handful of
     * frontier points, not a line per dragon.
     */
    static boolean tightensBracket(DeathSample candidate, List<DeathSample> existing) {
        for (DeathSample old : existing) {
            if (old.counts() != candidate.counts() || !old.statue().equals(candidate.statue())) continue;
            if ((old.dx() < 0) != (candidate.dx() < 0)
                || (old.dy() < 0) != (candidate.dy() < 0)
                || (old.dz() < 0) != (candidate.dz() < 0)) continue;
            boolean covered = candidate.counts()
                ? Math.abs(old.dx()) >= Math.abs(candidate.dx())
                    && Math.abs(old.dy()) >= Math.abs(candidate.dy())
                    && Math.abs(old.dz()) >= Math.abs(candidate.dz())
                : Math.abs(old.dx()) <= Math.abs(candidate.dx())
                    && Math.abs(old.dy()) <= Math.abs(candidate.dy())
                    && Math.abs(old.dz()) <= Math.abs(candidate.dz());
            if (covered) return false;
        }
        return true;
    }

    private void recordDeath(M7DragonTracker.Result result) {
        Statue statue = result.statue();
        Vec3 at = result.position();
        if (statue == null || at == null) return;
        Minecraft client = Minecraft.getInstance();
        Path gameDirectory = client == null ? Path.of(".") : client.gameDirectory.toPath();
        Path file = new KungFileLayout(gameDirectory, gameDirectory.resolve("config"))
            .dungeonDataDirectory().resolve("m7-dragon-deaths.jsonl");
        String line = deathLine(tracker.tick(), statue, result.outcome(), at);
        try {
            DeathSample candidate = new DeathSample(statue.name(),
                result.outcome() == M7DragonTracker.Outcome.COUNTS,
                at.x - statue.spawn().x, at.y - statue.spawn().y, at.z - statue.spawn().z);
            List<DeathSample> existing = Files.exists(file)
                ? parseSamples(Files.readAllLines(file, StandardCharsets.UTF_8)) : List.of();
            if (!tightensBracket(candidate, existing)) {
                trace("death-sample-skipped " + statue.label() + " already bracketed");
                return;
            }
        } catch (IOException exception) {
            trace("death-sample-read-failed " + exception.getMessage());
        }
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException exception) {
            trace("death-record-failed " + exception.getMessage());
        }
    }

    /**
     * Hypixel plays a dense burst on the spawn anchor five blocks up, roughly a second before the
     * dragon exists: count=40, spread 2/3/2, speed 0. Matching the burst shape rather than the
     * particle type keeps ambient arena effects out - three different types carry the same burst.
     */
    private void observeSpawnHint(ClientboundLevelParticlesPacket packet) {
        if (packet.getCount() < 40 || packet.getMaxSpeed() != 0.0F) return;
        if (packet.getXDist() != 2.0F || packet.getYDist() != 3.0F || packet.getZDist() != 2.0F) return;
        for (Statue statue : Statue.values()) {
            if (Math.abs(packet.getX() - statue.spawn().x) < 0.5
                && Math.abs(packet.getY() - (statue.spawn().y + 5)) < 0.5
                && Math.abs(packet.getZ() - statue.spawn().z) < 0.5) {
                if (spawnHints.put(statue, tracker.tick()) == null) {
                    trace("spawn-hint " + statue.label() + " tick=" + tracker.tick());
                }
                return;
            }
        }
    }

    public void observeParticles(ClientboundLevelParticlesPacket packet) {
        if (!initialized() || !ready()) return;
        if (packet.getY() < 0 || packet.getY() > 45 || packet.getX() < 0 || packet.getX() > 120
            || packet.getZ() < 25 || packet.getZ() > 150) return;
        observeSpawnHint(packet);
        if (!config().devDragonDiagnosticsEnabled()) return;
        ParticleType<?> type = packet.getParticle().getType();
        Long previous = particleSamples.get(type);
        // Sample each type once per second so common effects cannot crowd out a rare range signal.
        if (previous != null && tracker.tick() - previous < 20) return;
        if (previous == null && particleSamples.size() >= 16) return;
        particleSamples.put(type, tracker.tick());
        sample("particle=" + BuiltInRegistries.PARTICLE_TYPE.getKey(type)
            + " pos=" + new Vec3(packet.getX(), packet.getY(), packet.getZ()) + " count=" + packet.getCount()
            + " spread=" + packet.getXDist() + "," + packet.getYDist() + "," + packet.getZDist()
            + " speed=" + packet.getMaxSpeed());
    }

    private void capturePositions() {
        for (var entry : entities.entrySet()) {
            EnderDragon dragon = entry.getValue();
            var attempt = tracker.attempt(entry.getKey());
            if (attempt == null) continue;
            Vec3 position = DungeonDebuffFeature.matchingPosition(dragon);
            sample(attempt.statue().label() + " uuid=" + entry.getKey() + " received=" + position
                + " rendered=" + dragon.position() + " bounds=" + dragon.getBoundingBox()
                + " health=" + dragon.getHealth() + " estimatedInside=" + attempt.statue().contains(position));
        }
    }

    private void trace(String message) {
        KungDebugRecorder.event("m7-dragon", message);
        sample(message);
        if (!initialized() || !config().devDragonDiagnosticsEnabled()) return;
        developerEvents.addLast("tick=" + tracker.tick() + " " + message);
        while (developerEvents.size() > 128) developerEvents.removeFirst();
    }

    private void sample(String message) {
        if (!KungDeveloperAccess.allowed() || !initialized() || !config().devDragonDiagnosticsEnabled()) return;
        developerSamples.addLast("tick=" + tracker.tick() + " " + message);
        while (developerSamples.size() > 512) developerSamples.removeFirst();
    }

    public String captureDeveloperSample() {
        if (!KungDeveloperAccess.allowed()) return "Developer access required.";
        if (!ready() || !config().devDragonDiagnosticsEnabled()) return "Enable Developer Diagnostics in M7 first.";
        Minecraft client = Minecraft.getInstance();
        trace("manual player=" + (client.player == null ? "unknown" : client.player.position())
            + " lookedBlock=" + (client.hitResult instanceof BlockHitResult block ? block.getBlockPos() : "none"));
        capturePositions();
        return "Dragon sample captured. Use /kung dev dragons copy.";
    }

    public String developerReport() {
        if (!KungDeveloperAccess.allowed()) return "Developer access required.";
        return developerSamples.isEmpty() && !lastDeveloperReport.isEmpty()
            ? lastDeveloperReport : currentDeveloperReport();
    }

    private String currentDeveloperReport() {
        StringBuilder report = new StringBuilder("Kung M7 dragon measurements\n");
        report.append("epoch=").append(epoch).append(" samples=").append(developerSamples.size())
            .append("/512\nRange is a community estimate; origin, not body intersection.\n");
        for (Statue statue : Statue.values()) report.append(String.format(Locale.ROOT,
            "%s spawn=%s range=%s statueBlock=%s%n", statue.label(), statue.spawn(), statue.range(), statue.statueBlock()));
        report.append("Events (last 128, retained separately from periodic samples):\n");
        developerEvents.forEach(line -> report.append(line).append('\n'));
        report.append("Recent samples (last 512):\n");
        developerSamples.forEach(line -> report.append(line).append('\n'));
        return report.toString();
    }
}
