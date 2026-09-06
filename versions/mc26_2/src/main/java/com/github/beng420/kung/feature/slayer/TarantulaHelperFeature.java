package com.github.beng420.kung.feature.slayer;

import com.github.beng420.kung.feature.dungeon.DungeonMapOverlayConfig;
import com.github.beng420.kung.util.KungChat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TarantulaHelperFeature {
    private static final double SEARCH_RANGE = 30.0;
    private static final double NAME_TO_BODY_RANGE = 4.0;
    private static final double OWNER_SEARCH_RANGE = 5.0;
    private static final double EGG_SAC_SEARCH_RANGE = 24.0;
    private static final double EGG_SAC_PAIR_RANGE = 2.5;
    private static final double EGG_SAC_BOSS_RANGE = 24.0;
    private static final double EGG_SAC_CENTER_ABOVE_BOSS = 9.42;
    private static final double EGG_SAC_PREDICTION_HALF_X = 3.35;
    private static final double EGG_SAC_PREDICTION_HALF_Y = 0.42;
    private static final double EGG_SAC_PREDICTION_HALF_Z = 3.35;
    private static final float FIRST_EGG_SAC_PRE_TRIGGER_HEALTH = 0.86F;
    private static final float SECOND_EGG_SAC_PRE_TRIGGER_HEALTH = 0.54F;
    private static final long EGG_SAC_DONE_GRACE_MILLIS = 250L;
    private static final long EGG_SAC_MIN_PREDICTION_RENDER_MILLIS = 180L;
    private static final long BOSS_REBIND_GRACE_MILLIS = 2_000L;
    private static final long BOSS_MISSING_PREDICTION_MILLIS = 450L;
    private static final long SLAYER_END_SUPPRESS_MILLIS = 1_500L;
    private static final int PREDICTION_RED = 0;
    private static final int PREDICTION_GREEN = 255;
    private static final int PREDICTION_BLUE = 210;
    private static final int PREDICTION_ALPHA = 120;
    private static final double PREDICTION_GRID_SPACING = 0.75;
    private static final double PREDICTION_GRID_HALF_SIZE = 0.17;
    private static final double PREDICTION_GRID_BLOCK_SCAN_ABOVE = 1.0;
    private static final int PREDICTION_GRID_MAX_LOWERED_STEPS = 6;
    private static final int PREDICTION_GRID_ALPHA = 185;
    private static final int RED = 0;
    private static final int GREEN = 230;
    private static final int BLUE = 255;
    private static final int ALPHA = 235;
    private static Entity activeBoss;
    private static int activeBossId = -1;
    private static int activePhase;
    private static long lastPositionDebugMillis;
    private static long missingBossSinceMillis;
    private static boolean eggSacPhaseActive;
    private static boolean eggSacSeenDuringPhase;
    private static long eggSacPhaseStartedMillis;
    private static long lastEggSacSeenMillis;
    private static Vec3 eggSacPredictionAnchor;
    private static double eggSacPredictionHalfX = EGG_SAC_PREDICTION_HALF_X;
    private static double eggSacPredictionHalfY = EGG_SAC_PREDICTION_HALF_Y;
    private static double eggSacPredictionHalfZ = EGG_SAC_PREDICTION_HALF_Z;
    private static List<EggSac> visibleEggSacs = List.of();
    private static boolean eggSacPhaseStartDebugSent;
    private static int completedEggSacPhases;
    private static boolean firstEggSacPhasePredicted;
    private static boolean secondEggSacPhasePredicted;
    private static boolean cocoonEggSacPhasePredicted;
    private static float lastBossHealthPercent = Float.NaN;
    private static long slayerEndSeenMillis;
    private static boolean activeBossWasConjoined;

    private TarantulaHelperFeature() {
    }

    public static void initializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(TarantulaHelperFeature::tick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> observeMessage(Minecraft.getInstance(), message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
            observeMessage(Minecraft.getInstance(), message.getString()));
        LevelRenderEvents.END_MAIN.register(TarantulaHelperFeature::render);
    }

    private static void observeMessage(Minecraft client, String rawMessage) {
        DungeonMapOverlayConfig config = DungeonMapOverlayConfig.INSTANCE;
        if (!config.tarantulaHelperEnabled() || client.player == null) {
            return;
        }

        String message = clean(rawMessage);
        if (isEggSacPhaseStartMessage(message)) {
            Entity boss = activeBoss(client);
            if (boss != null) {
                startEggSacPhase(client, boss);
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

    private static void tick(Minecraft client) {
        DungeonMapOverlayConfig config = DungeonMapOverlayConfig.INSTANCE;
        boolean shouldTrack = config.tarantulaHelperEnabled()
            && (config.eggSacPredictionRendererEnabled()
                || config.eggSacPredictionEnabled()
                || config.tarantulaHelperDebugMessages());
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
                    startEggSacPhase(client, previousBoss);
                    visibleEggSacs = nearbyEggSacs;
                    markEggSacsSeen();
                    markBossMissing(client);
                    return;
                } else if (primeMissingBossPrediction(client)) {
                    markBossMissing(client);
                    return;
                } else if (!markBossMissing(client)) {
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
            return;
        }

        if (activeBossId == -1) {
            activeBoss = boss;
            activeBossId = boss.getId();
            activePhase = phaseFor(boss);
            lastPositionDebugMillis = 0L;
            missingBossSinceMillis = 0L;
            activeBossWasConjoined = isConjoinedBrood(boss);
            debug(client, DebugMessage.SLAYER_SPAWNED, "slayer spawned");
        } else {
            activeBoss = boss;
            missingBossSinceMillis = 0L;
            activeBossWasConjoined = activeBossWasConjoined || isConjoinedBrood(boss);
        }

        int phase = phaseFor(boss);
        if (phase != 0 && activePhase != 0 && phase != activePhase) {
            activePhase = phase;
            debug(client, DebugMessage.SLAYER_PHASE_CHANGE, "slayer phase change");
        } else if (activePhase == 0) {
            activePhase = phase;
        }

        anticipateEggSacPhase(client, boss);
        updateEggSacPhase(client, boss);
        if (config.eggSacPredictionEnabled() && canRenderPredictionForBoss(boss)) {
            updateEggSacPrediction(client, boss);
        }

        long now = System.currentTimeMillis();
        if (now - lastPositionDebugMillis >= 1000L) {
            lastPositionDebugMillis = now;
            debug(client, DebugMessage.SLAYER_POSITION, "slayer pos: " + positionText(boss));
        }
    }

    private static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        DungeonMapOverlayConfig config = DungeonMapOverlayConfig.INSTANCE;
        boolean renderBossArrow = config.tarantulaHelperEnabled() && config.eggSacPredictionRendererEnabled();
        boolean wantsEggSacPrediction = config.tarantulaHelperEnabled() && config.eggSacPredictionEnabled();
        if ((!renderBossArrow && !wantsEggSacPrediction)
            || client.level == null
            || client.player == null) {
            return;
        }

        Entity boss = activeBoss(client);
        if (boss == null) {
            return;
        }
        boolean renderEggSacPrediction = wantsEggSacPrediction
            && canRenderPredictionForBoss(boss)
            && !eggSacPredictionFinished()
            && visibleEggSacs.isEmpty()
            && !recentlySawSlayerEnd();

        PoseStack poseStack = context.poseStack();
        Vec3 camera = context.gameRenderer().mainCamera().position();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Vec3 predictionCenter = predictionAnchorFor(boss, renderPartialTick());
        Entity renderBoss = boss;
        if (renderEggSacPrediction) {
            context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                RenderTypes.debugQuads(),
                (pose, vertices) -> drawEggSacPrediction(pose, vertices, predictionCenter)
            );
        }
        if (renderBossArrow && boss != null) {
            context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                RenderTypes.debugQuads(),
                (pose, vertices) -> drawArrow(pose, vertices, renderBoss)
            );
        }
        poseStack.popPose();
    }

    private static Entity activeBoss(Minecraft client) {
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

    private static Entity findOwnBoss(Minecraft client) {
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

    private static Entity nearestBossBody(Minecraft client, Entity nameCarrier) {
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

    private static boolean isAliveCandidate(Minecraft client, Entity entity) {
        return entity != client.player
            && entity.isAlive()
            && client.player.distanceToSqr(entity) <= SEARCH_RANGE * SEARCH_RANGE;
    }

    private static boolean isSpiderBody(Entity entity) {
        EntityType<?> type = entity.getType();
        return type == EntityTypes.SPIDER || type == EntityTypes.CAVE_SPIDER;
    }

    private static boolean hasTarantulaBossName(Entity entity) {
        Component customName = entity.getCustomName();
        String name = clean(customName != null ? customName.getString() : entity.getName().getString());
        return name.contains("tarantula broodfather")
            || name.contains("primordial broodfather")
            || name.contains("primordial broodmother")
            || name.contains("conjoined brood");
    }

    private static boolean isOwnBossNameCarrier(Minecraft client, Entity nameCarrier) {
        String owner = ownerNameFor(client, nameCarrier);
        String playerName = client.player.getName().getString();
        return owner != null && owner.equalsIgnoreCase(playerName);
    }

    private static String ownerNameFor(Minecraft client, Entity nameCarrier) {
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

    private static String ownerNameFrom(Entity entity) {
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

    private static boolean isOwnSlayerEndMessage(String message) {
        return message.contains("slayer quest complete")
            || message.contains("slayer quest failed")
            || message.contains("slayer boss slain")
            || message.contains("boss slain")
            || message.contains("nice! slayer boss");
    }

    private static boolean isEggSacPhaseStartMessage(String message) {
        return message.contains("broodfather's hatchlings")
            || message.contains("broodfathers hatchlings")
            || message.contains("kill the hatchlings");
    }

    private static void anticipateEggSacPhase(Minecraft client, Entity boss) {
        if (eggSacPredictionFinished() || isConjoinedBrood(boss)) {
            lastBossHealthPercent = Float.NaN;
            return;
        }

        if (!(boss instanceof LivingEntity living) || living.getMaxHealth() <= 0.0F) {
            return;
        }

        float healthPercent = living.getHealth() / living.getMaxHealth();
        float previousHealthPercent = lastBossHealthPercent;
        lastBossHealthPercent = healthPercent;
        if (!firstEggSacPhasePredicted && crossedHealthThreshold(previousHealthPercent, healthPercent, FIRST_EGG_SAC_PRE_TRIGGER_HEALTH)) {
            updateEggSacPrediction(client, boss);
            return;
        }
        if (!secondEggSacPhasePredicted && crossedHealthThreshold(previousHealthPercent, healthPercent, SECOND_EGG_SAC_PRE_TRIGGER_HEALTH)) {
            updateEggSacPrediction(client, boss);
            return;
        }
        if (!cocoonEggSacPhasePredicted && hasCocoonSignal(client, boss)) {
            cocoonEggSacPhasePredicted = true;
            primeEggSacPhase(client, boss);
        }
    }

    private static boolean crossedHealthThreshold(float previousHealthPercent, float healthPercent, float threshold) {
        if (Float.isNaN(previousHealthPercent)) {
            return healthPercent <= threshold;
        }
        return previousHealthPercent > threshold && healthPercent <= threshold;
    }

    private static boolean primeMissingBossPrediction(Minecraft client) {
        if (activeBoss == null || eggSacPredictionFinished() || activeBossWasConjoined || recentlySawSlayerEnd()) {
            return false;
        }

        long missingFor = missingBossSinceMillis == 0L ? 0L : System.currentTimeMillis() - missingBossSinceMillis;
        if (missingFor > BOSS_MISSING_PREDICTION_MILLIS) {
            return false;
        }

        if (!firstEggSacPhasePredicted) {
            firstEggSacPhasePredicted = true;
            primeEggSacPhase(client, activeBoss);
            return true;
        }
        if (!secondEggSacPhasePredicted) {
            secondEggSacPhasePredicted = true;
            primeEggSacPhase(client, activeBoss);
            return true;
        }
        if (!cocoonEggSacPhasePredicted && eggSacSeenDuringPhase) {
            cocoonEggSacPhasePredicted = true;
            primeEggSacPhase(client, activeBoss);
            return true;
        }
        return false;
    }

    private static boolean recentlySawSlayerEnd() {
        return slayerEndSeenMillis != 0L && System.currentTimeMillis() - slayerEndSeenMillis <= SLAYER_END_SUPPRESS_MILLIS;
    }

    private static boolean hasCocoonSignal(Minecraft client, Entity boss) {
        if (clean(entityName(boss)).contains("cocoon")) {
            return true;
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.distanceToSqr(boss) > OWNER_SEARCH_RANGE * OWNER_SEARCH_RANGE) {
                continue;
            }
            String name = clean(entityName(entity));
            if (name.contains("cocoon")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConjoinedBrood(Entity entity) {
        return clean(entityName(entity)).contains("conjoined brood");
    }

    private static boolean canRenderPredictionForBoss(Entity boss) {
        return isSpiderBody(boss)
            && !isConjoinedBrood(boss)
            && !isDyingBoss(boss);
    }

    private static boolean isDyingBoss(Entity boss) {
        return boss instanceof LivingEntity living
            && living.getMaxHealth() > 0.0F
            && living.getHealth() <= 0.0F;
    }

    private static void updateEggSacPhase(Minecraft client, Entity boss) {
        long now = System.currentTimeMillis();
        List<EggSac> eggSacs = findEggSacs(client, boss);
        visibleEggSacs = eggSacs;
        if (!eggSacs.isEmpty()) {
            if (!eggSacPhaseActive) {
                startEggSacPhase(client, boss);
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

    private static List<EggSac> findEggSacs(Minecraft client, Entity boss) {
        java.util.ArrayList<EggSac> sacs = new java.util.ArrayList<>();
        for (Entity timer : client.level.entitiesForRendering()) {
            if (!(timer instanceof ArmorStand) || client.player.distanceToSqr(timer) > EGG_SAC_SEARCH_RANGE * EGG_SAC_SEARCH_RANGE) {
                continue;
            }

            String timerName = clean(entityName(timer));
            if (!isEggSacTimer(timerName)) {
                continue;
            }
            if (boss != null && timer.distanceToSqr(boss) > EGG_SAC_BOSS_RANGE * EGG_SAC_BOSS_RANGE) {
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

    private static void rebindBossAfterPhaseTransition(Minecraft client, Entity boss) {
        activeBoss = boss;
        activeBossId = boss.getId();
        activePhase = phaseFor(boss);
        activeBossWasConjoined = activeBossWasConjoined || isConjoinedBrood(boss);
        lastBossHealthPercent = Float.NaN;
        missingBossSinceMillis = 0L;
        debug(client, DebugMessage.SLAYER_PHASE_CHANGE, "slayer phase change");
    }

    private static boolean markBossMissing(Minecraft client) {
        long now = System.currentTimeMillis();
        if (missingBossSinceMillis == 0L) {
            missingBossSinceMillis = now;
            return false;
        }

        return now - missingBossSinceMillis > BOSS_REBIND_GRACE_MILLIS;
    }

    private static void markEggSacsSeen() {
        eggSacSeenDuringPhase = true;
        lastEggSacSeenMillis = System.currentTimeMillis();
    }

    private static String entityName(Entity entity) {
        Component customName = entity.getCustomName();
        return customName != null ? customName.getString() : entity.getName().getString();
    }

    private static boolean isEggSacTimer(String name) {
        return name.matches("\\d+s \\d+/\\d+");
    }

    private static void primeEggSacPhase(Minecraft client, Entity boss) {
        startEggSacPhase(client, boss, false);
    }

    private static void startEggSacPhase(Minecraft client, Entity boss) {
        startEggSacPhase(client, boss, true);
    }

    private static void startEggSacPhase(Minecraft client, Entity boss, boolean sendDebug) {
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
        eggSacPhaseActive = true;
        markEggSacPhasePredicted();
        eggSacSeenDuringPhase = false;
        eggSacPhaseStartedMillis = now;
        lastEggSacSeenMillis = now;
        visibleEggSacs = List.of();
        eggSacPhaseStartDebugSent = false;
        updateEggSacPrediction(client, boss);
        if (sendDebug) {
            sendEggSacPhaseStartDebug(client);
        }
    }

    private static void markEggSacPhasePredicted() {
        if (!firstEggSacPhasePredicted) {
            firstEggSacPhasePredicted = true;
        } else if (!secondEggSacPhasePredicted) {
            secondEggSacPhasePredicted = true;
        } else {
            cocoonEggSacPhasePredicted = true;
        }
    }

    private static void sendEggSacPhaseStartDebug(Minecraft client) {
        eggSacPhaseStartDebugSent = true;
        debug(client, DebugMessage.EGG_SAC_PHASE_START, "egg sac phase start");
    }

    private static void finishEggSacPhase(Minecraft client) {
        if (!eggSacPhaseActive) {
            return;
        }

        boolean completedRealEggSacPhase = eggSacSeenDuringPhase;
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
        if (completedRealEggSacPhase) {
            completedEggSacPhases++;
        }
        debug(client, DebugMessage.EGG_SAC_PHASE_DONE, "egg sac phase done");
    }

    private static boolean eggSacPredictionFinished() {
        return completedEggSacPhases >= 2;
    }

    private static String clean(String text) {
        return plain(text)
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    private static String plain(String text) {
        return text.replaceAll("(?:\\u00a7|\\u00c2\\u00a7).", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static int phaseFor(Entity boss) {
        if (!(boss instanceof LivingEntity living) || living.getMaxHealth() <= 0.0F) {
            return 0;
        }

        float healthPercent = living.getHealth() / living.getMaxHealth();
        if (healthPercent > 0.66F) {
            return 1;
        }
        if (healthPercent > 0.33F) {
            return 2;
        }
        return 3;
    }

    private static String positionText(Entity boss) {
        AABB bounds = boss.getBoundingBox();
        double x = (bounds.minX + bounds.maxX) * 0.5;
        double y = bounds.minY;
        double z = (bounds.minZ + bounds.maxZ) * 0.5;
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", x, y, z);
    }

    private static void debug(Minecraft client, DebugMessage type, String message) {
        DungeonMapOverlayConfig config = DungeonMapOverlayConfig.INSTANCE;
        if (config.tarantulaHelperDebugMessages() && isDebugMessageEnabled(config, type) && client.player != null) {
            client.player.sendSystemMessage(KungChat.message(message));
        }
    }

    private static boolean isDebugMessageEnabled(DungeonMapOverlayConfig config, DebugMessage type) {
        return switch (type) {
            case SLAYER_SPAWNED -> config.tarantulaDebugSlayerSpawned();
            case SLAYER_POSITION -> config.tarantulaDebugSlayerPosition();
            case SLAYER_PHASE_CHANGE -> config.tarantulaDebugSlayerPhaseChange();
            case SLAYER_DEAD -> config.tarantulaDebugSlayerDead();
            case EGG_SAC_PHASE_START -> config.tarantulaDebugEggSacPhaseStart();
            case EGG_SAC_PHASE_DONE -> config.tarantulaDebugEggSacPhaseDone();
        };
    }

    private static void clearTrackedBoss() {
        activeBoss = null;
        activeBossId = -1;
        activePhase = 0;
        lastPositionDebugMillis = 0L;
        missingBossSinceMillis = 0L;
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
        completedEggSacPhases = 0;
        firstEggSacPhasePredicted = false;
        secondEggSacPhasePredicted = false;
        cocoonEggSacPhasePredicted = false;
        lastBossHealthPercent = Float.NaN;
        activeBossWasConjoined = false;
    }

    private static void updateEggSacPrediction(Minecraft client, Entity boss) {
        AABB bounds = boss.getBoundingBox();
        double bossX = (bounds.minX + bounds.maxX) * 0.5;
        double bossZ = (bounds.minZ + bounds.maxZ) * 0.5;
        eggSacPredictionAnchor = new Vec3(bossX, bounds.maxY + EGG_SAC_CENTER_ABOVE_BOSS, bossZ);
        eggSacPredictionHalfX = EGG_SAC_PREDICTION_HALF_X;
        eggSacPredictionHalfY = EGG_SAC_PREDICTION_HALF_Y;
        eggSacPredictionHalfZ = EGG_SAC_PREDICTION_HALF_Z;
    }

    private static Vec3 predictionAnchorFor(Entity boss, float partialTick) {
        Vec3 position = boss.getPosition(partialTick);
        return new Vec3(position.x, position.y + boss.getBbHeight() + EGG_SAC_CENTER_ABOVE_BOSS, position.z);
    }

    private static float renderPartialTick() {
        Minecraft client = Minecraft.getInstance();
        return client.gameRenderer.mainCamera().getCameraEntityPartialTicks(client.getDeltaTracker());
    }

    private static void drawEggSacPrediction(PoseStack.Pose pose, VertexConsumer vertices, Vec3 center) {
        if (DungeonMapOverlayConfig.INSTANCE.eggSacPredictionRenderMode() == DungeonMapOverlayConfig.EggSacPredictionRenderMode.GRID) {
            drawEggSacPredictionGrid(Minecraft.getInstance(), pose, vertices, center);
            return;
        }

        drawBox(
            pose,
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

    private static void drawEggSacPredictionGrid(Minecraft client, PoseStack.Pose pose, VertexConsumer vertices, Vec3 center) {
        int minGridX = (int) Math.ceil(-eggSacPredictionHalfX / PREDICTION_GRID_SPACING);
        int maxGridX = (int) Math.floor(eggSacPredictionHalfX / PREDICTION_GRID_SPACING);
        int minGridZ = (int) Math.ceil(-eggSacPredictionHalfZ / PREDICTION_GRID_SPACING);
        int maxGridZ = (int) Math.floor(eggSacPredictionHalfZ / PREDICTION_GRID_SPACING);
        double halfY = Math.min(PREDICTION_GRID_HALF_SIZE, Math.max(0.10, eggSacPredictionHalfY * 0.4));
        for (int gridX = minGridX; gridX <= maxGridX; gridX++) {
            for (int gridZ = minGridZ; gridZ <= maxGridZ; gridZ++) {
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

    private static double gridYFor(Minecraft client, double x, double normalY, double z) {
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

    private static boolean isGridSlotBlocked(Minecraft client, double x, double centerY, double z) {
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

    private static void drawArrow(PoseStack.Pose pose, VertexConsumer vertices, Entity boss) {
        float partialTick = renderPartialTick();
        Vec3 position = boss.getPosition(partialTick);
        double x = position.x;
        double y = position.y + boss.getBbHeight();
        double z = position.z;
        double shaftTopY = y + 2.25;
        double shaftBottomY = y + 1.45;
        double tipY = y + 1.05;

        drawBox(pose, vertices, x - 0.08, shaftBottomY, z - 0.08, x + 0.08, shaftTopY, z + 0.08, RED, GREEN, BLUE, ALPHA);
        drawBox(pose, vertices, x - 0.32, tipY, z - 0.32, x + 0.32, shaftBottomY, z + 0.32, RED, GREEN, BLUE, ALPHA);
    }

    private static void drawBox(
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

    private static void addQuad(
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

    private static void addVertex(
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
