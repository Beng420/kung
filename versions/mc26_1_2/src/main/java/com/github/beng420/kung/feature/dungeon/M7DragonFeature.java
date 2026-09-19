package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.config.category.DungeonConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.feature.dungeon.M7DragonTracker.Statue;
import com.github.beng420.kung.message.KungMessages;
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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
    private final List<String> pendingMessages = new ArrayList<>();
    private final ArrayDeque<String> developerSamples = new ArrayDeque<>();
    private final ArrayDeque<String> developerEvents = new ArrayDeque<>();
    private final Map<String, Long> particleSamples = new LinkedHashMap<>();
    private String lastDeveloperReport = "";
    private long epoch = Long.MIN_VALUE;
    private long witherKingEpoch = Long.MIN_VALUE;
    private long lastSampleTick = Long.MIN_VALUE;

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
            if (dragon.getHealth() <= 0) observeDeath(dragon);
            if (dragon.isRemoved()) {
                tracker.unload(entry.getKey());
                iterator.remove();
                continue;
            }
            tracker.move(entry.getKey(), DungeonDebuffFeature.matchingPosition(dragon));
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
                markers.add(point(waypoint(statue, partialTick), statue.color()));
            }
            if (config().m7DragonHelperEnabled() && config().dragonStatueBoxesEnabled() && !tracker.statueCounted(statue)) {
                var attempt = tracker.latest(statue);
                int color = attempt == null || attempt.dead() || !attempt.loaded() ? 0xFFAAAAAA
                    : statue.contains(attempt.position()) ? 0xFF55FF55 : 0xFFFF5555;
                markers.add(new M7DragonRenderer.Marker(statue.range(), color, false));
            }
        }
        if (config().devDragonDiagnosticsEnabled()) for (EnderDragon dragon : entities.values()) {
            if (dragon.isRemoved() || !dragon.isAlive()) continue;
            // White: received origin used by the estimate; yellow: interpolated body-box center.
            markers.add(point(DungeonDebuffFeature.matchingPosition(dragon), 0xFFFFFFFF));
            markers.add(point(dragon.getBoundingBox().getCenter()
                .add(dragon.getPosition(partialTick).subtract(dragon.position())), 0xFFFFFF55));
        }
        return markers;
    }

    /** Waypoint rests on the spawn anchor until that statue's dragon is in the air, then rides it. */
    private Vec3 waypoint(Statue statue, float partialTick) {
        var attempt = tracker.latest(statue);
        if (attempt == null || attempt.dead()) return statue.spawn();
        EnderDragon dragon = entities.get(attempt.uuid());
        if (dragon == null || dragon.isRemoved() || !dragon.isAlive()) return statue.spawn();
        return dragon.getPosition(partialTick);
    }

    private static M7DragonRenderer.Marker point(Vec3 center, int color) {
        return new M7DragonRenderer.Marker(new AABB(center, center).inflate(0.12), color | 0xFF000000, true);
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
            client.player.sendSystemMessage(counts ? KungMessages.success("Dragons", text) : KungMessages.warning("Dragons", text));
            // Subtitle slot rather than title: it draws at half the scale. Only an identified
            // statue gets one - an unnamed dragon has no colour to recognise, so it stays in chat.
            if (result.statue() != null && client.gui != null) {
                client.gui.setTimes(0, 30, 5);
                client.gui.setTitle(Component.empty());
                client.gui.setSubtitle(Component.literal(result.statue().label())
                    .withColor(result.statue().color() & 0xFFFFFF)
                    .append(Component.literal(counts ? " counts" : " unsure")
                        .withColor(counts ? 0x55FF55 : 0xFFAA00)));
            }
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

    public void observeParticles(ClientboundLevelParticlesPacket packet) {
        if (!initialized() || !config().devDragonDiagnosticsEnabled() || !ready()) return;
        if (packet.getY() < 0 || packet.getY() > 45 || packet.getX() < 0 || packet.getX() > 120
            || packet.getZ() < 25 || packet.getZ() > 150) return;
        String type = BuiltInRegistries.PARTICLE_TYPE.getKey(packet.getParticle().getType()).toString();
        Long previous = particleSamples.get(type);
        // Sample each type once per second so common effects cannot crowd out a rare range signal.
        if (previous != null && tracker.tick() - previous < 20) return;
        if (previous == null && particleSamples.size() >= 16) return;
        particleSamples.put(type, tracker.tick());
        sample("particle=" + type
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
