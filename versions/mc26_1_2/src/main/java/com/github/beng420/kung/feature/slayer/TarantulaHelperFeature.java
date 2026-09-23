package com.github.beng420.kung.feature.slayer;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.SlayerConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.message.KungMessages;
import com.github.beng420.kung.runtime.KungDeveloperAccess;
import com.github.beng420.kung.runtime.KungFileLayout;
import com.github.beng420.kung.util.KungDebugRecorder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TarantulaHelperFeature extends ConfigurableFeature<SlayerConfig> {
    public static final TarantulaHelperFeature INSTANCE = new TarantulaHelperFeature();

    private static final double SEARCH_RANGE = 30.0;
    private static final double NAME_TO_BODY_RANGE = 4.0;
    private static final double OWNER_SEARCH_RANGE = 5.0;
    private static final double EGG_SAC_SEARCH_RANGE = 24.0;
    private static final double EGG_SAC_PAIR_RANGE = 2.5;
    private static final double EGG_SAC_BOSS_RANGE = 24.0;
    private static final double EGG_SAC_CENTER_ABOVE_BOSS = 9.42;
    /**
     * 646 recorded sacs from 143 phases: they sit in a disc around the centre, never in the corners of
     * the old square, and their height matches the centre exactly (99% within 3.05 blocks, |dy| < 0.03).
     */
    private static final double EGG_SAC_PREDICTION_RADIUS = 3.05;
    private static final double EGG_SAC_PREDICTION_HALF_X = EGG_SAC_PREDICTION_RADIUS;
    private static final double EGG_SAC_PREDICTION_HALF_Y = 0.42;
    private static final double EGG_SAC_PREDICTION_HALF_Z = EGG_SAC_PREDICTION_RADIUS;
    /** Other players fight their bosses next to ours; their sacs are far outside our own disc. */
    private static final double EGG_SAC_CLUSTER_RANGE = 8.0;
    private static final double EGG_SAC_CLUSTER_HEIGHT = 4.0;
    private static final float FIRST_EGG_SAC_PRE_TRIGGER_HEALTH = 0.86F;
    private static final float SECOND_EGG_SAC_PRE_TRIGGER_HEALTH = 0.54F;
    private static final long EGG_SAC_DONE_GRACE_MILLIS = 250L;
    private static final long BOSS_REBIND_GRACE_MILLIS = 2_000L;
    private static final long BOSS_MISSING_PREDICTION_MILLIS = 450L;
    private static final long SLAYER_END_SUPPRESS_MILLIS = 1_500L;
    /** The leap into the egg sac phase gains far more height in one tick than walking over a slab. */
    private static final double JUMP_RISE_PER_TICK = 0.3;
    private static final int PREDICTION_RED = 0;
    private static final int PREDICTION_GREEN = 255;
    private static final int PREDICTION_BLUE = 210;
    private static final int PREDICTION_ALPHA = 120;
    private static final double PREDICTION_GRID_SPACING = 0.75;
    /**
     * Hypixel keeps the real health in the nametag. Mobs carry "795.1k/1.2M❤", a slayer boss only its
     * current health, as in "☠ Tarantula Broodfather V 9.4M❤".
     */
    private static final java.util.regex.Pattern NAMETAG_HEALTH =
        java.util.regex.Pattern.compile("(?:([\\d.,]+[kKmMbB]?)/)?([\\d.,]+[kKmMbB]?)❤");
    private static final String EGG_SAC_FILE = "tarantula-egg-sacs.txt";
    private static final double PREDICTION_GRID_HALF_SIZE = 0.17;
    private static final double PREDICTION_GRID_BLOCK_SCAN_ABOVE = 1.0;
    private static final int PREDICTION_GRID_MAX_LOWERED_STEPS = 6;
    private static final int PREDICTION_GRID_ALPHA = 185;

    private Entity activeBoss;
    private int activeBossId = -1;
    private int activePhase;
    private long lastPositionDebugMillis;
    private long missingBossSinceMillis;
    private boolean eggSacPhaseActive;
    private boolean eggSacSeenDuringPhase;
    private long eggSacPhaseStartedMillis;
    private long lastEggSacSeenMillis;
    private Vec3 eggSacPredictionAnchor;
    private double eggSacPredictionHalfX = EGG_SAC_PREDICTION_HALF_X;
    private double eggSacPredictionHalfY = EGG_SAC_PREDICTION_HALF_Y;
    private double eggSacPredictionHalfZ = EGG_SAC_PREDICTION_HALF_Z;
    private List<EggSac> visibleEggSacs = List.of();
    private boolean eggSacPhaseStartDebugSent;
    private int completedEggSacPhases;
    private boolean firstEggSacPhasePredicted;
    private boolean secondEggSacPhasePredicted;
    private boolean cocoonEggSacPhasePredicted;
    private boolean eggSacJumpArmed;
    private boolean trackingTraced = true;
    private double lastBossY = Double.NaN;
    /** Offsets from the jump spot of every sac seen in the running phase, one entry per spawn point. */
    private final Map<String, Vec3> phaseSacOffsets = new LinkedHashMap<>();
    private float lastBossHealthPercent = Float.NaN;
    private long slayerEndSeenMillis;
    private boolean activeBossWasConjoined;
    /** A slayer nametag names no maximum, so the boss's full health is the highest value seen. */
    private double bossMaxHealth;
    private int activeNameCarrierId = -1;
    /** The health this phase started at; while it runs the boss cannot lose any. */
    private float phaseStartHealth = Float.NaN;

    private TarantulaHelperFeature() {
        super(cfg -> cfg.slayer);
    }

    @Override
    protected void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
            observeMessage(Minecraft.getInstance(), message.getString())
        );
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
            observeMessage(Minecraft.getInstance(), message.getString())
        );
        LevelRenderEvents.END_MAIN.register(this::render);
    }

    @Override
    public boolean isEnabled() {
        return config().tarantulaHelperEnabled();
    }

    private void observeMessage(Minecraft client, String rawMessage) {
        if (!isEnabled() || client.player == null) {
            return;
        }

        String message = clean(rawMessage);
        if (isEggSacPhaseStartMessage(message)) {
            Entity boss = activeBoss(client);
            if (eggSacPhaseActive && eggSacSeenDuringPhase) {
                // The sacs of the running phase are done with; this message belongs to the next one.
                finishEggSacPhase(client);
            }
            if (boss != null) {
                // The health here is what the real threshold looks like; it tunes the jump arming.
                float fraction = bossHealthFraction(client, boss);
                startEggSacPhase(client, boss, "hatchling message at "
                    + (Float.isNaN(fraction) ? "unknown" : Math.round(fraction * 100) + "%"));
            } else {
                // No boss entity while he cocoons; the message alone is proof enough to show the box.
                startEggSacPhase(client, null, "hatchling message");
            }
            return;
        }

        if (isOwnSlayerEndMessage(message)) {
            slayerEndSeenMillis = System.currentTimeMillis();
            if (activeBossId != -1) {
                finishEggSacPhase(client);
                debug(client, DebugMessage.SLAYER_DEAD, "slayer dead");
            }
            clearTrackedBoss();
        }
    }

    private void tick(Minecraft client) {
        boolean shouldTrack = isEnabled()
            && (config().eggSacPredictionEnabled() || KungConfig.get().debug.tarantulaMessages());

        if (!shouldTrack || client.level == null || client.player == null) {
            clearTrackedBoss();
            return;
        }

        Entity boss = activeBoss(client);
        if (boss == null && activeBossId != -1) {
            boss = findOwnBoss(client);
            if (boss != null) {
                rebindBossAfterPhaseTransition(client, boss);
            } else {
                List<EggSac> nearbyEggSacs = findEggSacs(client, null);
                visibleEggSacs = nearbyEggSacs;
                if (!nearbyEggSacs.isEmpty()) {
                    Entity previousBoss = activeBoss != null ? activeBoss : client.player;
                    startEggSacPhase(client, previousBoss, "sacs visible");
                    visibleEggSacs = nearbyEggSacs;
                    markEggSacsSeen();
                    markBossMissing(client);
                    return;
                } else if (primeMissingBossPrediction(client)) {
                    markBossMissing(client);
                    return;
                } else if (!markBossMissing(client)) {
                    return;
                } else if (eggSacPhaseActive) {
                    // He sits in his cocoon: no entity to find, but the phase is still running.
                    return;
                } else {
                    finishEggSacPhase(client);
                    debug(client, DebugMessage.SLAYER_DEAD, "slayer dead");
                    clearTrackedBoss();
                    return;
                }
            }
        } else if (boss == null) {
            boss = findOwnBoss(client);
        }

        if (boss == null) {
            // Nothing bound: the phase still needs a spot, so take the nearest boss body there is.
            if (eggSacPhaseActive) anchorToAnyBossBody(client);
            traceTracking(client, false);
            return;
        }
        traceTracking(client, true);
        if (activeBossId == -1) {
            bossMaxHealth = 0;
            activeNameCarrierId = -1;
            float fraction = bossHealthFraction(client, boss);
            KungDebugRecorder.event("tarantula", "boss bound type=" + boss.getType().toShortString()
                + " health=" + (Float.isNaN(fraction) ? "unknown" : Math.round(fraction * 100) + "%"));
        }

        if (activeBossId == -1) {
            activeBoss = boss;
            activeBossId = boss.getId();
            activePhase = phaseFor(client, boss);
            lastPositionDebugMillis = 0L;
            missingBossSinceMillis = 0L;
            activeBossWasConjoined = isConjoinedBrood(boss);
            debug(client, DebugMessage.SLAYER_SPAWNED, "slayer spawned");
        } else {
            activeBoss = boss;
            missingBossSinceMillis = 0L;
            activeBossWasConjoined = activeBossWasConjoined || isConjoinedBrood(boss);
        }

        int phase = phaseFor(client, boss);
        if (phase != 0 && activePhase != 0 && phase != activePhase) {
            activePhase = phase;
            debug(client, DebugMessage.SLAYER_PHASE_CHANGE, "slayer phase change");
        } else if (activePhase == 0) {
            activePhase = phase;
        }

        anticipateEggSacPhase(client, boss);
        updateEggSacPhase(client, boss);

        long now = System.currentTimeMillis();
        if (now - lastPositionDebugMillis >= 1000L) {
            lastPositionDebugMillis = now;
            debug(client, DebugMessage.SLAYER_POSITION, "slayer pos: " + positionText(boss));
        }
    }

    /**
     * Only while an egg sac phase runs, over the boss. He disappears into its cocoon just as the sacs
     * spawn, so the last anchor carries the box through that gap instead of letting it blink out.
     */
    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled()
            || !config().eggSacPredictionEnabled()
            || client.level == null
            || client.player == null
            || !eggSacPhaseActive
            || eggSacSeenDuringPhase
            || recentlySawSlayerEnd()) {
            return;
        }

        Entity boss = activeBoss(client);
        Vec3 center = boss != null && canRenderPredictionForBoss(boss)
            ? predictionAnchorFor(boss, renderPartialTick())
            : eggSacPredictionAnchor;
        if (center == null) {
            return;
        }

        PoseStack poseStack = context.poseStack();
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        drawEggSacPrediction(poseStack, context.bufferSource(), center);
        poseStack.popPose();
    }

    private Vec3 predictionAnchorFor(Entity boss, float partialTick) {
        Vec3 position = boss.getPosition(partialTick);
        return new Vec3(position.x, position.y + boss.getBbHeight() + EGG_SAC_CENTER_ABOVE_BOSS, position.z);
    }

    private float renderPartialTick() {
        Minecraft client = Minecraft.getInstance();
        return client.gameRenderer.getMainCamera().getCameraEntityPartialTicks(client.getDeltaTracker());
    }

    private Entity activeBoss(Minecraft client) {
        if (activeBossId == -1 || client.level == null) {
            return null;
        }

        Entity boss = client.level.getEntity(activeBossId);
        if (boss == null
            || !boss.isAlive()
            || client.player == null
            || client.player.distanceToSqr(boss) > SEARCH_RANGE * SEARCH_RANGE) {
            return null;
        }
        return boss;
    }

    /** Why the helper has no boss: the names it does see decide whether matching or ownership failed. */
    private void traceTracking(Minecraft client, boolean found) {
        if (found == trackingTraced) return;
        trackingTraced = found;
        if (found) {
            return;
        }
        StringBuilder names = new StringBuilder();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!entity.hasCustomName() || client.player.distanceToSqr(entity) > SEARCH_RANGE * SEARCH_RANGE) continue;
            String name = plain(entityName(entity));
            if (name.isBlank() || names.length() > 300) continue;
            names.append(names.isEmpty() ? "" : " | ").append(name);
        }
        KungDebugRecorder.event("tarantula", "no own boss; nearby=[" + names + "]");
    }

    /** Anchor without ownership proof: only used while a phase already started, so it is our fight. */
    private void anchorToAnyBossBody(Minecraft client) {
        double best = SEARCH_RANGE * SEARCH_RANGE;
        Entity body = null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!hasTarantulaBossName(entity)) continue;
            Entity candidate = nearestBossBody(client, entity);
            if (candidate == null) continue;
            double distance = client.player.distanceToSqr(candidate);
            if (distance < best) {
                best = distance;
                body = candidate;
            }
        }
        if (body != null) updateEggSacPrediction(client, body);
    }

    private Entity findOwnBoss(Minecraft client) {
        Entity best = null;
        double bestDistance = SEARCH_RANGE * SEARCH_RANGE;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!isAliveCandidate(client, entity) || !hasTarantulaBossName(entity)) {
                continue;
            }
            if (!isOwnBossNameCarrier(client, entity)) {
                continue;
            }

            Entity body = nearestBossBody(client, entity);
            Entity candidate = body != null ? body : entity;
            double distance = client.player.distanceToSqr(candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }

        return best;
    }

    private Entity nearestBossBody(Minecraft client, Entity nameCarrier) {
        Entity best = null;
        double bestDistance = NAME_TO_BODY_RANGE * NAME_TO_BODY_RANGE;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!isAliveCandidate(client, entity) || !isSpiderBody(entity)) {
                continue;
            }

            double distance = entity.distanceToSqr(nameCarrier);
            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean isAliveCandidate(Minecraft client, Entity entity) {
        return entity != client.player
            && entity.isAlive()
            && client.player.distanceToSqr(entity) <= SEARCH_RANGE * SEARCH_RANGE;
    }

    private boolean isSpiderBody(Entity entity) {
        EntityType<?> type = entity.getType();
        return type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER;
    }

    private boolean hasTarantulaBossName(Entity entity) {
        Component customName = entity.getCustomName();
        String name = clean(customName != null ? customName.getString() : entity.getName().getString());
        return name.contains("tarantula broodfather")
            || name.contains("primordial broodfather")
            || name.contains("primordial broodmother")
            || name.contains("conjoined brood");
    }

    private boolean isOwnBossNameCarrier(Minecraft client, Entity nameCarrier) {
        String owner = ownerNameFor(client, nameCarrier);
        String playerName = client.player.getName().getString();
        return owner != null && owner.equalsIgnoreCase(playerName);
    }

    private String ownerNameFor(Minecraft client, Entity nameCarrier) {
        for (int offset = 1; offset <= 4; offset++) {
            Entity stackedEntity = client.level.getEntity(nameCarrier.getId() + offset);
            String owner = ownerNameFrom(stackedEntity);
            if (owner != null) {
                return owner;
            }
        }

        Entity nearestOwner = null;
        double bestDistance = OWNER_SEARCH_RANGE * OWNER_SEARCH_RANGE;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand) || ownerNameFrom(entity) == null) {
                continue;
            }

            double distance = entity.distanceToSqr(nameCarrier);
            if (distance < bestDistance) {
                nearestOwner = entity;
                bestDistance = distance;
            }
        }

        return ownerNameFrom(nearestOwner);
    }

    private String ownerNameFrom(Entity entity) {
        if (entity == null) {
            return null;
        }

        Component customName = entity.getCustomName();
        String text = plain(customName != null ? customName.getString() : entity.getName().getString());
        String lower = text.toLowerCase(Locale.ROOT);
        String marker = "spawned by:";
        int markerIndex = lower.indexOf(marker);
        if (markerIndex == -1) {
            return null;
        }

        String rest = text.substring(markerIndex + marker.length()).trim();
        StringBuilder owner = new StringBuilder();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                owner.append(c);
            } else if (!owner.isEmpty()) {
                break;
            }
        }

        return owner.isEmpty() ? null : owner.toString();
    }

    private boolean isOwnSlayerEndMessage(String message) {
        return message.contains("slayer quest complete")
            || message.contains("slayer quest failed")
            || message.contains("slayer boss slain")
            || message.contains("boss slain")
            || message.contains("nice! slayer boss");
    }

    private boolean isEggSacPhaseStartMessage(String message) {
        return message.contains("broodfather's hatchlings")
            || message.contains("broodfathers hatchlings")
            || message.contains("kill the hatchlings");
    }

    /**
     * Tarantula jumps when his health reaches a threshold, spins on the spot and only then spawns the
     * cobwebs and egg sacs. The jump is the cue, and the spot he jumps from is where they appear.
     */
    private void anticipateEggSacPhase(Minecraft client, Entity boss) {
        double previousY = lastBossY;
        lastBossY = boss.getY();
        double rise = Double.isNaN(previousY) ? 0.0 : boss.getY() - previousY;
        if (eggSacPredictionFinished() || isConjoinedBrood(boss)) {
            lastBossHealthPercent = Float.NaN;
            return;
        }

        // The box belongs over the boss, so the anchor follows him; it only has to outlive him.
        if (canRenderPredictionForBoss(boss)) {
            updateEggSacPrediction(client, boss);
        }

        float healthPercent = bossHealthFraction(client, boss);
        if (Float.isNaN(healthPercent)) {
            return;
        }

        float previousHealthPercent = lastBossHealthPercent;
        lastBossHealthPercent = healthPercent;
        if ((!firstEggSacPhasePredicted && crossedHealthThreshold(previousHealthPercent, healthPercent, FIRST_EGG_SAC_PRE_TRIGGER_HEALTH))
            || (firstEggSacPhasePredicted && !secondEggSacPhasePredicted
                && crossedHealthThreshold(previousHealthPercent, healthPercent, SECOND_EGG_SAC_PRE_TRIGGER_HEALTH))) {
            eggSacJumpArmed = true;
            KungDebugRecorder.event("tarantula", "egg sac armed health=" + Math.round(healthPercent * 100) + "%");
        }

        // Real damage lands again, so the sacs this phase waited for were never coming. While the phase
        // runs he is invulnerable, so anything past health jitter means the phase was a false alarm.
        if (eggSacPhaseActive && !eggSacSeenDuringPhase && !Float.isNaN(phaseStartHealth)
            && healthPercent < phaseStartHealth - 0.005F) {
            KungDebugRecorder.event("tarantula", "boss damageable again, no sacs");
            finishEggSacPhase(client);
        }

        if (eggSacJumpArmed && rise > JUMP_RISE_PER_TICK) {
            eggSacJumpArmed = false;
            KungDebugRecorder.event("tarantula", String.format(Locale.ROOT, "egg sac jump rise=%.2f", rise));
            primeEggSacPhase(client, boss, "jump");
            return;
        }
    }

    /**
     * The entity's own health stays full on Hypixel, so the fraction comes from the nametag above the
     * body. Slayer bosses stack several stands (owner, name, health), so any of them may carry it.
     */
    private float bossHealthFraction(Minecraft client, Entity boss) {
        Entity carrier = ownNameCarrier(client, boss);
        double[] health = carrier == null ? null : nametagHealth(plain(entityName(carrier)));
        if (health == null) {
            return Float.NaN;
        }
        if (health[1] > 0) {
            return (float) Math.clamp(health[0] / health[1], 0.0, 1.0);
        }

        // A boss tag names no maximum, so the highest health seen for this boss is its full health.
        bossMaxHealth = Math.max(bossMaxHealth, health[0]);
        return bossMaxHealth <= 0 ? Float.NaN : (float) Math.clamp(health[0] / bossMaxHealth, 0.0, 1.0);
    }

    /**
     * Our own boss's nametag. Other players fight their bosses right next to ours here, so the nearest
     * boss tag is regularly someone else's and its health would jump between fights.
     */
    private Entity ownNameCarrier(Minecraft client, Entity boss) {
        Entity remembered = activeNameCarrierId == -1 ? null : client.level.getEntity(activeNameCarrierId);
        if (remembered != null && hasTarantulaBossName(remembered)
            && remembered.distanceToSqr(boss) <= NAME_TO_BODY_RANGE * NAME_TO_BODY_RANGE) {
            return remembered;
        }

        double bestDistance = NAME_TO_BODY_RANGE * NAME_TO_BODY_RANGE;
        Entity best = null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!hasTarantulaBossName(entity) || !isOwnBossNameCarrier(client, entity)) {
                continue;
            }

            double distance = entity.distanceToSqr(boss);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        activeNameCarrierId = best == null ? -1 : best.getId();
        return best;
    }

    /** "... 795.1k/1.2M❤" -> {795100, 1200000}; "... 9.4M❤" -> {9400000, -1}; null without health. */
    static double[] nametagHealth(String name) {
        var matcher = NAMETAG_HEALTH.matcher(name);
        if (!matcher.find()) {
            return null;
        }

        boolean hasMax = matcher.group(1) != null;
        double current = nametagAmount(hasMax ? matcher.group(1) : matcher.group(2));
        double max = hasMax ? nametagAmount(matcher.group(2)) : -1;
        return current < 0 ? null : new double[] {current, max};
    }

    private static double nametagAmount(String text) {
        String value = text.replace(",", "").toLowerCase(Locale.ROOT);
        double factor = value.endsWith("k") ? 1_000 : value.endsWith("m") ? 1_000_000 : value.endsWith("b") ? 1_000_000_000 : 1;
        if (factor > 1) {
            value = value.substring(0, value.length() - 1);
        }
        try {
            return Double.parseDouble(value) * factor;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private boolean crossedHealthThreshold(float previousHealthPercent, float healthPercent, float threshold) {
        if (Float.isNaN(previousHealthPercent)) {
            return healthPercent <= threshold;
        }
        return previousHealthPercent > threshold && healthPercent <= threshold;
    }

    private boolean primeMissingBossPrediction(Minecraft client) {
        if (activeBoss == null || eggSacPredictionFinished() || activeBossWasConjoined || recentlySawSlayerEnd()) {
            return false;
        }

        long missingFor = missingBossSinceMillis == 0L ? 0L : System.currentTimeMillis() - missingBossSinceMillis;
        if (missingFor > BOSS_MISSING_PREDICTION_MILLIS) {
            return false;
        }

        if (!firstEggSacPhasePredicted) {
            firstEggSacPhasePredicted = true;
            primeEggSacPhase(client, activeBoss, "boss vanished");
            return true;
        }
        if (!secondEggSacPhasePredicted) {
            secondEggSacPhasePredicted = true;
            primeEggSacPhase(client, activeBoss, "boss vanished");
            return true;
        }
        if (!cocoonEggSacPhasePredicted && eggSacSeenDuringPhase) {
            cocoonEggSacPhasePredicted = true;
            primeEggSacPhase(client, activeBoss, "boss vanished");
            return true;
        }
        return false;
    }

    private boolean recentlySawSlayerEnd() {
        return slayerEndSeenMillis != 0L && System.currentTimeMillis() - slayerEndSeenMillis <= SLAYER_END_SUPPRESS_MILLIS;
    }

    private boolean isConjoinedBrood(Entity entity) {
        return clean(entityName(entity)).contains("conjoined brood");
    }

    private boolean canRenderPredictionForBoss(Entity boss) {
        return isSpiderBody(boss)
            && !isConjoinedBrood(boss)
            && !isDyingBoss(boss);
    }

    private boolean isDyingBoss(Entity boss) {
        return boss instanceof LivingEntity living
            && living.getMaxHealth() > 0.0F
            && living.getHealth() <= 0.0F;
    }

    private void updateEggSacPhase(Minecraft client, Entity boss) {
        long now = System.currentTimeMillis();
        List<EggSac> eggSacs = findEggSacs(client, boss);
        visibleEggSacs = eggSacs;
        if (!eggSacs.isEmpty()) {
            if (!eggSacPhaseActive) {
                startEggSacPhase(client, boss, "sacs visible");
            } else if (!eggSacPhaseStartDebugSent) {
                sendEggSacPhaseStartDebug(client);
            }
            markEggSacsSeen();
            return;
        }

        if (!eggSacPhaseActive) {
            return;
        }

        boolean sacsDisappeared = eggSacSeenDuringPhase && now - lastEggSacSeenMillis > EGG_SAC_DONE_GRACE_MILLIS;
        if (sacsDisappeared) {
            finishEggSacPhase(client);
        }
    }

    /** Our own sacs sit in one tight cluster around the prediction; anything else is another fight. */
    private boolean belongsToOurPhase(Entity timer, Entity boss) {
        if (eggSacPredictionAnchor != null) {
            double dx = timer.getX() - eggSacPredictionAnchor.x;
            double dz = timer.getZ() - eggSacPredictionAnchor.z;
            return Math.hypot(dx, dz) <= EGG_SAC_CLUSTER_RANGE
                && Math.abs(timer.getY() - eggSacPredictionAnchor.y) <= EGG_SAC_CLUSTER_HEIGHT;
        }
        return boss == null || timer.distanceToSqr(boss) <= EGG_SAC_BOSS_RANGE * EGG_SAC_BOSS_RANGE;
    }

    private List<EggSac> findEggSacs(Minecraft client, Entity boss) {
        java.util.ArrayList<EggSac> sacs = new java.util.ArrayList<>();
        for (Entity timer : client.level.entitiesForRendering()) {
            if (!(timer instanceof ArmorStand) || client.player.distanceToSqr(timer) > EGG_SAC_SEARCH_RANGE * EGG_SAC_SEARCH_RANGE) {
                continue;
            }

            String timerName = clean(entityName(timer));
            if (!isEggSacTimer(timerName)) {
                continue;
            }
            if (!belongsToOurPhase(timer, boss)) {
                continue;
            }

            for (Entity shootMe : client.level.entitiesForRendering()) {
                if (!(shootMe instanceof ArmorStand)) {
                    continue;
                }

                String shootName = clean(entityName(shootMe));
                if (shootName.equals("shoot me!") && shootMe.distanceToSqr(timer) < EGG_SAC_PAIR_RANGE * EGG_SAC_PAIR_RANGE) {
                    Vec3 timerCenter = timer.getBoundingBox().getCenter();
                    Vec3 shootCenter = shootMe.getBoundingBox().getCenter();
                    double centerY = (timerCenter.y + shootCenter.y) * 0.5;
                    double halfHeight = Math.clamp(Math.abs(timerCenter.y - shootCenter.y) * 0.35 + 0.22, 0.28, 0.42);
                    sacs.add(new EggSac(
                        new Vec3(
                            (timerCenter.x + shootCenter.x) * 0.5,
                            centerY,
                            (timerCenter.z + shootCenter.z) * 0.5
                        ),
                        halfHeight
                    ));
                    break;
                }
            }
        }
        return sacs;
    }

    private void rebindBossAfterPhaseTransition(Minecraft client, Entity boss) {
        activeBoss = boss;
        activeBossId = boss.getId();
        activePhase = phaseFor(client, boss);
        activeBossWasConjoined = activeBossWasConjoined || isConjoinedBrood(boss);
        lastBossHealthPercent = Float.NaN;
        missingBossSinceMillis = 0L;
        debug(client, DebugMessage.SLAYER_PHASE_CHANGE, "slayer phase change");
    }

    private boolean markBossMissing(Minecraft client) {
        long now = System.currentTimeMillis();
        if (missingBossSinceMillis == 0L) {
            missingBossSinceMillis = now;
            return false;
        }

        return now - missingBossSinceMillis > BOSS_REBIND_GRACE_MILLIS;
    }

    private void markEggSacsSeen() {
        eggSacSeenDuringPhase = true;
        lastEggSacSeenMillis = System.currentTimeMillis();
        recordSacOffsets();
    }

    /** Which of the predicted spots actually carry a sac, collected over many bosses for the developer. */
    private void recordSacOffsets() {
        if (eggSacPredictionAnchor == null || !KungDeveloperAccess.allowed()) {
            return;
        }

        for (EggSac sac : visibleEggSacs) {
            Vec3 offset = sac.center().subtract(eggSacPredictionAnchor);
            phaseSacOffsets.putIfAbsent(String.format(Locale.ROOT, "%.1f,%.1f", offset.x, offset.z), offset);
        }
    }

    private void writeSacOffsets() {
        if (phaseSacOffsets.isEmpty() || eggSacPredictionAnchor == null) {
            phaseSacOffsets.clear();
            return;
        }

        StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "anchor=%.2f,%.2f,%.2f sacs=",
            eggSacPredictionAnchor.x, eggSacPredictionAnchor.y, eggSacPredictionAnchor.z));
        boolean first = true;
        for (Vec3 offset : phaseSacOffsets.values()) {
            if (!first) line.append(';');
            first = false;
            line.append(String.format(Locale.ROOT, "%.2f,%.2f,%.2f", offset.x, offset.y, offset.z));
        }
        phaseSacOffsets.clear();
        Path file = eggSacFile();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            KungDebugRecorder.event("tarantula", "egg sac write failed: " + exception.getMessage());
        }
    }

    private static Path eggSacFile() {
        Minecraft client = Minecraft.getInstance();
        Path gameDirectory = client == null ? Path.of(".") : client.gameDirectory.toPath();
        return new KungFileLayout(gameDirectory, gameDirectory.resolve("config"))
            .slayerDataDirectory().resolve(EGG_SAC_FILE);
    }

    /** How often each grid cell carried a sac, over every phase recorded so far. */
    public String eggSacGridReport() {
        Path file = eggSacFile();
        List<String> lines;
        try {
            lines = Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
        } catch (IOException exception) {
            return "Egg sac data unreadable: " + exception.getMessage();
        }
        if (lines.isEmpty()) {
            return "No egg sac phases recorded yet. File: " + file;
        }

        Map<String, Integer> counts = eggSacGridCounts(lines);
        int columns = (int) Math.floor(EGG_SAC_PREDICTION_HALF_X / PREDICTION_GRID_SPACING);
        int rows = (int) Math.floor(EGG_SAC_PREDICTION_HALF_Z / PREDICTION_GRID_SPACING);
        StringBuilder report = new StringBuilder("Egg sac spots over " + lines.size() + " phases (x right, z down):");
        for (int gridZ = -rows; gridZ <= rows; gridZ++) {
            report.append(String.format(Locale.ROOT, "%n%3d:", gridZ));
            for (int gridX = -columns; gridX <= columns; gridX++) {
                report.append(String.format(Locale.ROOT, "%5d", counts.getOrDefault(gridX + "," + gridZ, 0)));
            }
        }
        return report + System.lineSeparator() + "File: " + file;
    }

    /** "anchor=x,y,z sacs=dx,dy,dz;..." per phase -> hits per "gridX,gridZ" cell. */
    static Map<String, Integer> eggSacGridCounts(List<String> lines) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String line : lines) {
            int start = line.indexOf("sacs=");
            if (start < 0) continue;
            for (String entry : line.substring(start + 5).split(";")) {
                String[] parts = entry.split(",");
                if (parts.length != 3) continue;
                try {
                    int gridX = (int) Math.round(Double.parseDouble(parts[0]) / PREDICTION_GRID_SPACING);
                    int gridZ = (int) Math.round(Double.parseDouble(parts[2]) / PREDICTION_GRID_SPACING);
                    counts.merge(gridX + "," + gridZ, 1, Integer::sum);
                } catch (NumberFormatException exception) {
                    // A truncated line from a crash; the rest of the file still counts.
                }
            }
        }
        return counts;
    }

    private String entityName(Entity entity) {
        Component customName = entity.getCustomName();
        return customName != null ? customName.getString() : entity.getName().getString();
    }

    private boolean isEggSacTimer(String name) {
        return name.matches("\\d+s \\d+/\\d+");
    }

    private void primeEggSacPhase(Minecraft client, Entity boss, String reason) {
        startEggSacPhase(client, boss, false, reason);
    }

    private void startEggSacPhase(Minecraft client, Entity boss, String reason) {
        startEggSacPhase(client, boss, true, reason);
    }

    private void startEggSacPhase(Minecraft client, Entity boss, boolean sendDebug, String reason) {
        if (eggSacPredictionFinished()) {
            return;
        }

        if (eggSacPhaseActive) {
            if (sendDebug && !eggSacPhaseStartDebugSent) {
                sendEggSacPhaseStartDebug(client);
            }
            return;
        }

        long now = System.currentTimeMillis();
        phaseStartHealth = boss == null ? Float.NaN : bossHealthFraction(client, boss);
        eggSacPhaseActive = true;
        KungDebugRecorder.event("tarantula", "egg sac phase started: " + reason);
        markEggSacPhasePredicted();
        eggSacSeenDuringPhase = false;
        eggSacPhaseStartedMillis = now;
        lastEggSacSeenMillis = now;
        visibleEggSacs = List.of();
        eggSacPhaseStartDebugSent = false;
        if (eggSacPredictionAnchor == null && boss != null) {
            updateEggSacPrediction(client, boss);
        }
        if (sendDebug) {
            sendEggSacPhaseStartDebug(client);
        }
    }

    private void markEggSacPhasePredicted() {
        if (!firstEggSacPhasePredicted) {
            firstEggSacPhasePredicted = true;
        } else if (!secondEggSacPhasePredicted) {
            secondEggSacPhasePredicted = true;
        } else {
            cocoonEggSacPhasePredicted = true;
        }
    }

    private void sendEggSacPhaseStartDebug(Minecraft client) {
        eggSacPhaseStartDebugSent = true;
        debug(client, DebugMessage.EGG_SAC_PHASE_START, "egg sac phase start");
    }

    private void finishEggSacPhase(Minecraft client) {
        if (!eggSacPhaseActive) {
            return;
        }

        boolean completedRealEggSacPhase = eggSacSeenDuringPhase;
        writeSacOffsets();
        eggSacPhaseActive = false;
        eggSacSeenDuringPhase = false;
        eggSacPhaseStartedMillis = 0L;
        lastEggSacSeenMillis = 0L;
        eggSacPredictionAnchor = null;
        eggSacPredictionHalfX = EGG_SAC_PREDICTION_HALF_X;
        eggSacPredictionHalfY = EGG_SAC_PREDICTION_HALF_Y;
        eggSacPredictionHalfZ = EGG_SAC_PREDICTION_HALF_Z;
        visibleEggSacs = List.of();
        eggSacPhaseStartDebugSent = false;
        eggSacJumpArmed = false;
        if (completedRealEggSacPhase) {
            completedEggSacPhases++;
        }
        debug(client, DebugMessage.EGG_SAC_PHASE_DONE, "egg sac phase done");
    }

    private boolean eggSacPredictionFinished() {
        return completedEggSacPhases >= 2;
    }

    private String clean(String text) {
        return plain(text)
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    private String plain(String text) {
        return text.replaceAll("(?:\\u00a7|\\u00c2\\u00a7).", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private int phaseFor(Minecraft client, Entity boss) {
        float healthPercent = bossHealthFraction(client, boss);
        if (Float.isNaN(healthPercent)) {
            return 0;
        }

        if (healthPercent > 0.66F) {
            return 1;
        }
        if (healthPercent > 0.33F) {
            return 2;
        }
        return 3;
    }

    private String positionText(Entity boss) {
        AABB bounds = boss.getBoundingBox();
        double x = (bounds.minX + bounds.maxX) * 0.5;
        double y = bounds.minY;
        double z = (bounds.minZ + bounds.maxZ) * 0.5;
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", x, y, z);
    }

    private void debug(Minecraft client, DebugMessage type, String message) {
        boolean debugEnabled = KungConfig.get().debug.tarantulaMessagesEnabled();
        if (debugEnabled && isDebugMessageEnabled(type) && client.player != null) {
            client.player.sendSystemMessage(KungMessages.debug("Tarantula", message));
        }
    }

    private boolean isDebugMessageEnabled(DebugMessage type) {
        return switch (type) {
            case SLAYER_SPAWNED -> config().tarantulaDebugSlayerSpawned();
            case SLAYER_POSITION -> config().tarantulaDebugSlayerPosition();
            case SLAYER_PHASE_CHANGE -> config().tarantulaDebugSlayerPhaseChange();
            case SLAYER_DEAD -> config().tarantulaDebugSlayerDead();
            case EGG_SAC_PHASE_START -> config().tarantulaDebugEggSacPhaseStart();
            case EGG_SAC_PHASE_DONE -> config().tarantulaDebugEggSacPhaseDone();
        };
    }

    private void clearTrackedBoss() {
        activeBoss = null;
        activeBossId = -1;
        activePhase = 0;
        lastPositionDebugMillis = 0L;
        missingBossSinceMillis = 0L;
        eggSacPhaseActive = false;
        eggSacSeenDuringPhase = false;
        eggSacPhaseStartedMillis = 0L;
        lastEggSacSeenMillis = 0L;
        // The anchor stays: a box on its own clock still needs a spot when the boss entity is gone.
        eggSacPredictionHalfX = EGG_SAC_PREDICTION_HALF_X;
        eggSacPredictionHalfY = EGG_SAC_PREDICTION_HALF_Y;
        eggSacPredictionHalfZ = EGG_SAC_PREDICTION_HALF_Z;
        visibleEggSacs = List.of();
        eggSacPhaseStartDebugSent = false;
        completedEggSacPhases = 0;
        firstEggSacPhasePredicted = false;
        secondEggSacPhasePredicted = false;
        cocoonEggSacPhasePredicted = false;
        lastBossHealthPercent = Float.NaN;
        activeBossWasConjoined = false;
        eggSacJumpArmed = false;
        lastBossY = Double.NaN;
        bossMaxHealth = 0;
        activeNameCarrierId = -1;
        phaseStartHealth = Float.NaN;
        phaseSacOffsets.clear();
    }

    private void updateEggSacPrediction(Minecraft client, Entity boss) {
        AABB bounds = boss.getBoundingBox();
        double bossX = (bounds.minX + bounds.maxX) * 0.5;
        double bossZ = (bounds.minZ + bounds.maxZ) * 0.5;
        eggSacPredictionAnchor = new Vec3(bossX, bounds.maxY + EGG_SAC_CENTER_ABOVE_BOSS, bossZ);
        eggSacPredictionHalfX = EGG_SAC_PREDICTION_HALF_X;
        eggSacPredictionHalfY = EGG_SAC_PREDICTION_HALF_Y;
        eggSacPredictionHalfZ = EGG_SAC_PREDICTION_HALF_Z;
    }

    private void drawEggSacPrediction(PoseStack poseStack, MultiBufferSource buffers, Vec3 center) {
        VertexConsumer vertices = buffers.getBuffer(RenderTypes.debugQuads());
        if (config().eggSacPredictionRenderMode() == SlayerConfig.EggSacPredictionRenderMode.GRID) {
            drawEggSacPredictionGrid(Minecraft.getInstance(), poseStack.last(), vertices, center);
            return;
        }

        drawBox(
            poseStack.last(),
            vertices,
            center.x - eggSacPredictionHalfX,
            center.y - eggSacPredictionHalfY,
            center.z - eggSacPredictionHalfZ,
            center.x + eggSacPredictionHalfX,
            center.y + eggSacPredictionHalfY,
            center.z + eggSacPredictionHalfZ,
            PREDICTION_RED,
            PREDICTION_GREEN,
            PREDICTION_BLUE,
            PREDICTION_ALPHA
        );
    }

    private void drawEggSacPredictionGrid(Minecraft client, PoseStack.Pose pose, VertexConsumer vertices, Vec3 center) {
        int minGridX = (int) Math.ceil(-eggSacPredictionHalfX / PREDICTION_GRID_SPACING);
        int maxGridX = (int) Math.floor(eggSacPredictionHalfX / PREDICTION_GRID_SPACING);
        int minGridZ = (int) Math.ceil(-eggSacPredictionHalfZ / PREDICTION_GRID_SPACING);
        int maxGridZ = (int) Math.floor(eggSacPredictionHalfZ / PREDICTION_GRID_SPACING);
        double halfY = Math.min(PREDICTION_GRID_HALF_SIZE, Math.max(0.10, eggSacPredictionHalfY * 0.4));
        for (int gridX = minGridX; gridX <= maxGridX; gridX++) {
            for (int gridZ = minGridZ; gridZ <= maxGridZ; gridZ++) {
                // The corners of the square never carried a sac, so only the disc is drawn.
                if (Math.hypot(gridX * PREDICTION_GRID_SPACING, gridZ * PREDICTION_GRID_SPACING)
                    > EGG_SAC_PREDICTION_RADIUS) {
                    continue;
                }

                double x = center.x + gridX * PREDICTION_GRID_SPACING;
                double z = center.z + gridZ * PREDICTION_GRID_SPACING;
                double y = gridYFor(client, x, center.y, z);
                drawBox(
                    pose,
                    vertices,
                    x - PREDICTION_GRID_HALF_SIZE,
                    y - halfY,
                    z - PREDICTION_GRID_HALF_SIZE,
                    x + PREDICTION_GRID_HALF_SIZE,
                    y + halfY,
                    z + PREDICTION_GRID_HALF_SIZE,
                    PREDICTION_RED,
                    PREDICTION_GREEN,
                    PREDICTION_BLUE,
                    PREDICTION_GRID_ALPHA
                );
            }
        }
    }

    private double gridYFor(Minecraft client, double x, double normalY, double z) {
        if (client.level == null) {
            return normalY;
        }

        for (int step = 0; step <= PREDICTION_GRID_MAX_LOWERED_STEPS; step++) {
            double candidateY = normalY - step;
            if (!isGridSlotBlocked(client, x, candidateY, z)) {
                return candidateY;
            }
        }

        return normalY - PREDICTION_GRID_MAX_LOWERED_STEPS;
    }

    private boolean isGridSlotBlocked(Minecraft client, double x, double centerY, double z) {
        int baseY = (int) Math.floor(centerY);
        int topY = (int) Math.floor(centerY + PREDICTION_GRID_BLOCK_SCAN_ABOVE);
        for (int y = baseY; y <= topY; y++) {
            BlockPos pos = BlockPos.containing(x, y, z);
            BlockState state = client.level.getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(client.level, pos).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private void drawBox(
        PoseStack.Pose pose,
        VertexConsumer vertices,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        addQuad(pose, vertices, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        addQuad(pose, vertices, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        addQuad(pose, vertices, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, red, green, blue, alpha);
        addQuad(pose, vertices, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, red, green, blue, alpha);
        addQuad(pose, vertices, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        addQuad(pose, vertices, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, red, green, blue, alpha);
    }

    private void addQuad(
        PoseStack.Pose pose,
        VertexConsumer vertices,
        double x1,
        double y1,
        double z1,
        double x2,
        double y2,
        double z2,
        double x3,
        double y3,
        double z3,
        double x4,
        double y4,
        double z4,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        addVertex(pose, vertices, x1, y1, z1, red, green, blue, alpha);
        addVertex(pose, vertices, x2, y2, z2, red, green, blue, alpha);
        addVertex(pose, vertices, x3, y3, z3, red, green, blue, alpha);
        addVertex(pose, vertices, x4, y4, z4, red, green, blue, alpha);
    }

    private void addVertex(
        PoseStack.Pose pose,
        VertexConsumer vertices,
        double x,
        double y,
        double z,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        vertices.addVertex(pose, (float) x, (float) y, (float) z)
            .setColor(red, green, blue, alpha);
    }

    private enum DebugMessage {
        SLAYER_SPAWNED,
        SLAYER_POSITION,
        SLAYER_PHASE_CHANGE,
        SLAYER_DEAD,
        EGG_SAC_PHASE_START,
        EGG_SAC_PHASE_DONE
    }

    record EggSac(Vec3 center, double halfHeight) {
    }
}
