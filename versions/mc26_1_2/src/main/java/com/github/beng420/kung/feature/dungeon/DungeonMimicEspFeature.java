package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.config.category.DungeonConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.feature.Feature;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import com.github.beng420.kung.util.KungDebugRecorder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

public final class DungeonMimicEspFeature extends ConfigurableFeature<DungeonConfig> implements Feature {
    private static final Identifier WAYPOINT_HUD_ID = Identifier.fromNamespaceAndPath(KungMod.MOD_ID, "mimic_esp_waypoint");
    private static final long SCAN_INTERVAL_TICKS = 10L;
    private static final int CHUNK_SCAN_RADIUS = 12;
    private static final long ENTITY_MISSING_GRACE_TICKS = 10L;
    private static final long OPENED_CHEST_COMBAT_WINDOW_TICKS = 20L * 25L;
    private static final int TEXT_RED = 0xFFFF4040;
    private static final int WAYPOINT_BACKGROUND = 0x99000000;
    private static final int WAYPOINT_FILL = 0xAAFF3030;
    private static final int WAYPOINT_OUTLINE = 0xFFFFF15A;
    private static final double MIMIC_ENTITY_SEARCH_RANGE = 16.0;
    private static final int HIGHLIGHT_RED = 255;
    private static final int HIGHLIGHT_GREEN = 35;
    private static final int HIGHLIGHT_BLUE = 35;
    private static final int HIGHLIGHT_ALPHA = 120;
    private static final Set<String> KNOWN_STATIC_TRAPPED_CHEST_ROOMS = Set.of(
        "buttons",
        "slime",
        "slimemaze"
    );
    private static final Pattern COMBAT_XP_PATTERN =
        Pattern.compile("^\\+\\d+(?:\\.\\d+)?\\s+Combat\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIMIC_KILL_PATTERN =
        Pattern.compile(".*(?:Mimic dead!?|Mimic Killed!|\\$SKYTILS-DUNGEON-SCORE-MIMIC\\$).*", Pattern.CASE_INSENSITIVE);

    private final DungeonStateTracker tracker;
    private List<BlockPos> mimicChestPositions = List.of();
    private List<BlockPos> lastKnownMimicChestPositions = List.of();
    private Set<Integer> observedMimicEntityIds = Set.of();
    private ScanStats lastScanStats = ScanStats.empty();
    private Object observedLevel;
    private DungeonLiveMapWriter.CellKey lastPlayerRoom;
    private boolean mimicEncounterActive;
    private long mimicChestOpenedTick = Long.MIN_VALUE;
    private long mimicEntityMissingSinceTick = Long.MIN_VALUE;
    private long lastScanTick = Long.MIN_VALUE;
    private long lastRenderLogTick = Long.MIN_VALUE;
    private String lastRenderLogState = "";
    private String lastLoggedState = "";
    private DungeonMapSnapshot.GridKey observedMimicMapRoom;
    private BlockPos observedMimicMapPos;

    public DungeonMimicEspFeature(DungeonStateTracker tracker) {
        super(config -> config.dungeon);
        this.tracker = java.util.Objects.requireNonNull(tracker);
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
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.PLAYER_LIST,
            WAYPOINT_HUD_ID,
            this::renderWaypointHud
        );
    }

    @Override
    public boolean isEnabled() {
        DungeonConfig config = config();
        return config.enabled() && config.mimicEspEnabled();
    }

    private void tick(Minecraft client) {
        if (tracker.runStats().mimicKilled()) {
            tracker.mapSnapshot().clearMimicRooms("mimic-killed");
            clear();
            return;
        }
        if (!shouldTrack(client)) {
            clear();
            return;
        }

        if (observedLevel != client.level) {
            observedLevel = client.level;
            mimicChestPositions = List.of();
            lastScanStats = ScanStats.empty();
            lastScanTick = Long.MIN_VALUE;
            lastPlayerRoom = null;
            mimicEncounterActive = false;
            mimicChestOpenedTick = Long.MIN_VALUE;
            mimicEntityMissingSinceTick = Long.MIN_VALUE;
        }

        long nowTick = tracker.dungeonTick();
        DungeonLiveMapWriter.CellKey playerRoom = currentPlayerRoom(client);
        boolean enteredNewRoom = playerRoom != null && !playerRoom.equals(lastPlayerRoom);
        lastPlayerRoom = playerRoom;
        if (!enteredNewRoom && lastScanTick != Long.MIN_VALUE && nowTick - lastScanTick < SCAN_INTERVAL_TICKS) {
            return;
        }

        lastScanTick = nowTick;
        List<BlockPos> previousChestPositions = mimicChestPositions;
        mimicChestPositions = findMimicChests(client);
        observeOpenedMimicChest(client, previousChestPositions, mimicChestPositions, nowTick);
        if (!mimicChestPositions.isEmpty()) {
            lastKnownMimicChestPositions = mimicChestPositions;
        }
        observeMimicRooms(client);
        observeMimicEntityState(client);
        logState();
    }

    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!shouldTrack(client) || mimicChestPositions.isEmpty()) {
            return;
        }

        PoseStack poseStack = context.poseStack();
        try {
            Vec3 camera = client.gameRenderer.getMainCamera().position();
            MultiBufferSource buffers = context.bufferSource();
            drawChestHighlights(poseStack, buffers, camera, mimicChestPositions);
            logRenderState(camera, sameRoomMimicChestCount(client));
        } catch (RuntimeException | LinkageError exception) {
            tracker.reportRunError(client, "Mimic ESP render", exception);
            KungMod.LOGGER.warn("Failed to render dungeon mimic ESP.", exception);
        }
    }

    private void renderWaypointHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (!shouldTrack(client) || mimicChestPositions.isEmpty()) {
            return;
        }

        try {
            DungeonLiveMapWriter.MatchRenderPlan renderPlan = tracker.renderPlan();
            Set<String> drawnRooms = new HashSet<>();
            for (BlockPos pos : mimicChestPositions) {
                if (playerInSameRoom(client, renderPlan, pos)) {
                    String roomKey = roomKey(renderPlan, pos);
                    if (roomKey != null && !drawnRooms.add(roomKey)) {
                        continue;
                    }
                    drawScreenWaypoint(client, graphics, pos);
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            tracker.reportRunError(client, "Mimic ESP waypoint", exception);
            KungMod.LOGGER.warn("Failed to render dungeon mimic waypoint.", exception);
        }
    }

    private boolean shouldTrack(Minecraft client) {
        if (!isEnabled()
            || client.level == null
            || client.player == null
            || !tracker.isInDungeonArea()
            || tracker.runStats().mimicKilled()) {
            return false;
        }

        int floor = tracker.runStats().floor();
        return floor <= 0 || floor == 6 || floor == 7;
    }

    private List<BlockPos> findMimicChests(Minecraft client) {
        int playerChunkX = Math.floorDiv(client.player.blockPosition().getX(), 16);
        int playerChunkZ = Math.floorDiv(client.player.blockPosition().getZ(), 16);
        Set<BlockPos> result = new LinkedHashSet<>();
        int checkedChunks = 0;
        int checkedBlockEntities = 0;
        int scannedChunks = 0;
        DungeonLiveMapWriter.MatchRenderPlan renderPlan = tracker.renderPlan();
        forgetTrapMimicRooms(renderPlan);

        for (int chunkX = playerChunkX - CHUNK_SCAN_RADIUS; chunkX <= playerChunkX + CHUNK_SCAN_RADIUS; chunkX++) {
            for (int chunkZ = playerChunkZ - CHUNK_SCAN_RADIUS; chunkZ <= playerChunkZ + CHUNK_SCAN_RADIUS; chunkZ++) {
                LevelChunk chunk = client.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                checkedChunks++;
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    checkedBlockEntities++;
                    if (isMimicChest(client, renderPlan, pos)) {
                        result.add(copyPos(pos));
                    }
                }
                scannedChunks++;
                chunk.findBlocks(
                    state -> state.is(Blocks.TRAPPED_CHEST),
                    (pos, state) -> {
                        if (isMimicChest(client, renderPlan, pos)) {
                            result.add(copyPos(pos));
                        }
                    }
                );
            }
        }

        lastScanStats = new ScanStats(checkedChunks, checkedBlockEntities, scannedChunks);
        return List.copyOf(result);
    }

    private static boolean isMimicChest(
        Minecraft client,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        BlockPos pos
    ) {
        DungeonScanUtils.GridPosition grid = DungeonScanUtils.getRoomGridPosition(pos);
        if (!isInsideDungeonGrid(grid) || renderPlan.roomTypeAt(grid.gridX(), grid.gridZ()) == RoomType.TRAP) {
            return false;
        }
        if (isKnownStaticTrappedChestRoom(renderPlan, grid.gridX(), grid.gridZ())) {
            return false;
        }

        BlockState state = client.level.getBlockState(pos);
        return state.is(Blocks.TRAPPED_CHEST);
    }

    private static boolean isInsideDungeonGrid(DungeonScanUtils.GridPosition grid) {
        return grid.gridX() >= 0
            && grid.gridZ() >= 0
            && grid.gridX() <= DungeonScanUtils.SCAN_GRID_SIZE / 2
            && grid.gridZ() <= DungeonScanUtils.SCAN_GRID_SIZE / 2;
    }

    private static BlockPos copyPos(BlockPos pos) {
        return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static DungeonLiveMapWriter.CellKey currentPlayerRoom(Minecraft client) {
        if (client.player == null) {
            return null;
        }
        DungeonScanUtils.GridPosition grid = DungeonScanUtils.getRoomGridPosition(client.player.blockPosition());
        if (!isInsideDungeonGrid(grid)) {
            return null;
        }
        return new DungeonLiveMapWriter.CellKey(grid.gridX(), grid.gridZ());
    }

    private void forgetTrapMimicRooms(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        for (DungeonMapSnapshot.GridKey room : tracker.mapSnapshot().mimicRooms()) {
            if (renderPlan.roomTypeAt(room.gridX(), room.gridZ()) == RoomType.TRAP) {
                tracker.mapSnapshot().forgetMimicRoom(room.gridX(), room.gridZ(), "trap-room-filter");
            }
        }
    }

    private void observeMimicRooms(Minecraft client) {
        BlockPos pos = selectMapMimicChest(client);
        if (pos == null) {
            forgetObservedMimicMapRoom("mimic-candidate-ambiguous");
            return;
        }

        DungeonScanUtils.GridPosition grid = DungeonScanUtils.getRoomGridPosition(pos);
        DungeonMapSnapshot.GridKey room = new DungeonMapSnapshot.GridKey(grid.gridX(), grid.gridZ());
        if (observedMimicMapRoom != null && !observedMimicMapRoom.equals(room)) {
            tracker.mapSnapshot().forgetMimicRoom(
                observedMimicMapRoom.gridX(),
                observedMimicMapRoom.gridZ(),
                "mimic-candidate-changed"
            );
        }
        observedMimicMapRoom = room;
        observedMimicMapPos = pos;
        tracker.mapSnapshot().observeMimicRoom(
            grid.gridX(),
            grid.gridZ(),
            "trapped-chest@" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
        );
    }

    private BlockPos selectMapMimicChest(Minecraft client) {
        List<BlockPos> positions = mimicEncounterActive && !lastKnownMimicChestPositions.isEmpty()
            ? lastKnownMimicChestPositions
            : mimicChestPositions;
        if (positions.isEmpty()) {
            return null;
        }

        DungeonLiveMapWriter.MatchRenderPlan renderPlan = tracker.renderPlan();
        String selectedRoomKey = null;
        BlockPos selectedPos = null;
        for (BlockPos pos : positions) {
            String roomKey = roomKey(renderPlan, pos);
            if (roomKey == null) {
                return positions.size() == 1 ? pos : null;
            }
            if (selectedRoomKey == null) {
                selectedRoomKey = roomKey;
                selectedPos = pos;
                continue;
            }
            if (!selectedRoomKey.equals(roomKey)) {
                return null;
            }
        }
        return selectedPos;
    }

    private void forgetObservedMimicMapRoom(String source) {
        if (observedMimicMapRoom == null) {
            return;
        }
        tracker.mapSnapshot().forgetMimicRoom(
            observedMimicMapRoom.gridX(),
            observedMimicMapRoom.gridZ(),
            source
        );
        observedMimicMapRoom = null;
        observedMimicMapPos = null;
    }

    private void observeOpenedMimicChest(
        Minecraft client,
        List<BlockPos> previousPositions,
        List<BlockPos> currentPositions,
        long nowTick
    ) {
        if (previousPositions.isEmpty() || client.level == null) {
            return;
        }
        DungeonLiveMapWriter.MatchRenderPlan renderPlan = tracker.renderPlan();
        for (BlockPos previous : previousPositions) {
            if (currentPositions.contains(previous) || !playerInSameRoom(client, renderPlan, previous)) {
                continue;
            }
            if (client.level.getBlockState(previous).is(Blocks.TRAPPED_CHEST)) {
                continue;
            }
            mimicEncounterActive = true;
            mimicChestOpenedTick = nowTick;
            mimicEntityMissingSinceTick = Long.MIN_VALUE;
            KungDebugRecorder.event("mimic-esp", "mimic chest opened pos="
                + previous.getX() + "," + previous.getY() + "," + previous.getZ());
            return;
        }
    }

    private void observeMimicEntityState(Minecraft client) {
        if (client.level == null || lastKnownMimicChestPositions.isEmpty()) {
            observedMimicEntityIds = Set.of();
            return;
        }

        Set<Integer> currentIds = new LinkedHashSet<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (isMimicSpawnCandidate(entity)) {
                currentIds.add(entity.getId());
            }
        }

        long nowTick = tracker.dungeonTick();
        if (currentIds.isEmpty()) {
            if (!observedMimicEntityIds.isEmpty()) {
                if (mimicEntityMissingSinceTick == Long.MIN_VALUE) {
                    mimicEntityMissingSinceTick = nowTick;
                }
                if (nowTick - mimicEntityMissingSinceTick >= ENTITY_MISSING_GRACE_TICKS) {
                    KungDebugRecorder.event("mimic-esp", "mimic entity gone ids=" + observedMimicEntityIds.size());
                    completeMimic(client, "mimic-entity-gone");
                    return;
                }
            }
        } else {
            mimicEncounterActive = true;
            mimicEntityMissingSinceTick = Long.MIN_VALUE;
        }

        observedMimicEntityIds = currentIds;
    }

    private boolean isMimicSpawnCandidate(Entity entity) {
        if (!entity.isAlive() || !nearKnownMimicChest(entity)) {
            return false;
        }
        String name = entityName(entity).toLowerCase(java.util.Locale.ROOT);
        if (name.contains("mimic")) {
            return true;
        }
        return entity instanceof Zombie zombie && zombie.isBaby();
    }

    private boolean nearKnownMimicChest(Entity entity) {
        for (BlockPos pos : lastKnownMimicChestPositions) {
            if (entity.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)
                <= MIMIC_ENTITY_SEARCH_RANGE * MIMIC_ENTITY_SEARCH_RANGE) {
                return true;
            }
        }
        return false;
    }

    private static String entityName(Entity entity) {
        net.minecraft.network.chat.Component customName = entity.getCustomName();
        return (customName != null ? customName.getString() : entity.getName().getString())
            .replaceAll("(?:\\u00a7|\\u00c2\\u00a7).", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private void observeMessage(Minecraft client, String rawMessage) {
        if (!shouldTrack(client) || rawMessage == null) {
            return;
        }

        String message = rawMessage.replaceAll("(?:\\u00a7|\\u00c2\\u00a7).", "")
            .replaceAll("\\s+", " ")
            .trim();
        if (message.isBlank()) {
            return;
        }
        if (MIMIC_KILL_PATTERN.matcher(message).matches()) {
            completeMimic(client, "chat");
            return;
        }

        long nowTick = tracker.dungeonTick();
        boolean openedRecently = mimicEncounterActive
            && mimicChestOpenedTick != Long.MIN_VALUE
            && nowTick - mimicChestOpenedTick <= OPENED_CHEST_COMBAT_WINDOW_TICKS;
        if (openedRecently && COMBAT_XP_PATTERN.matcher(message).matches()) {
            KungDebugRecorder.event("mimic-esp", "mimic combat xp message=" + KungDebugRecorder.compact(message));
            completeMimic(client, "combat-xp");
        }
    }

    private void completeMimic(Minecraft client, String source) {
        if (tracker.runStats().mimicKilled()) {
            clear();
            return;
        }
        KungDebugRecorder.event("mimic-esp", "mimic complete source=" + source);
        tracker.runStats().observeMimicEspKill(client);
        tracker.mapSnapshot().clearMimicRooms(source);
        clear();
    }

    private static boolean playerInSameRoom(
        Minecraft client,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        BlockPos mimicPos
    ) {
        if (client.player == null) {
            return false;
        }
        DungeonScanUtils.GridPosition playerGrid = DungeonScanUtils.getRoomGridPosition(client.player.blockPosition());
        DungeonScanUtils.GridPosition mimicGrid = DungeonScanUtils.getRoomGridPosition(mimicPos);
        if (!isInsideDungeonGrid(playerGrid) || !isInsideDungeonGrid(mimicGrid)) {
            return false;
        }
        DungeonLiveMapWriter.CellKey playerCell = new DungeonLiveMapWriter.CellKey(playerGrid.gridX(), playerGrid.gridZ());
        DungeonLiveMapWriter.CellKey mimicCell = new DungeonLiveMapWriter.CellKey(mimicGrid.gridX(), mimicGrid.gridZ());
        return playerCell.equals(mimicCell)
            || renderPlan.sameRoomOwner(playerCell, mimicCell)
            || sameRoomCells(renderPlan, mimicCell).contains(playerCell);
    }

    private static Set<DungeonLiveMapWriter.CellKey> sameRoomCells(
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        DungeonLiveMapWriter.CellKey cell
    ) {
        String owner = renderPlan.roomOwners().get(cell);
        if (owner != null) {
            Set<DungeonLiveMapWriter.CellKey> cells = new HashSet<>();
            for (java.util.Map.Entry<DungeonLiveMapWriter.CellKey, String> entry : renderPlan.roomOwners().entrySet()) {
                if (owner.equals(entry.getValue())) {
                    cells.add(entry.getKey());
                }
            }
            return cells;
        }

        DungeonKnownRoomCatalog.MatchedRoom match = matchContaining(renderPlan, cell.x(), cell.z());
        if (match == null) {
            return Set.of(cell);
        }

        Set<DungeonLiveMapWriter.CellKey> cells = new HashSet<>();
        for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
            cells.add(new DungeonLiveMapWriter.CellKey(component.roomGridX(), component.roomGridZ()));
        }
        return cells;
    }

    private static DungeonKnownRoomCatalog.MatchedRoom matchContaining(
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        int roomGridX,
        int roomGridZ
    ) {
        for (DungeonKnownRoomCatalog.MatchedRoom match : renderPlan.matches()) {
            if (match.contains(roomGridX, roomGridZ)) {
                return match;
            }
        }
        return null;
    }

    private static boolean isKnownStaticTrappedChestRoom(
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        int roomGridX,
        int roomGridZ
    ) {
        DungeonKnownRoomCatalog.MatchedRoom match = matchContaining(renderPlan, roomGridX, roomGridZ);
        if (match != null) {
            return KNOWN_STATIC_TRAPPED_CHEST_ROOMS.contains(canonicalRoomName(match.template().name()));
        }

        DungeonKnownRoomCatalog.KnownCoreHint hint = renderPlan.hintAt(roomGridX, roomGridZ);
        return hint != null && KNOWN_STATIC_TRAPPED_CHEST_ROOMS.contains(canonicalRoomName(hint.name()));
    }

    private static String roomKey(DungeonLiveMapWriter.MatchRenderPlan renderPlan, BlockPos pos) {
        DungeonScanUtils.GridPosition grid = DungeonScanUtils.getRoomGridPosition(pos);
        if (!isInsideDungeonGrid(grid)) {
            return null;
        }
        DungeonLiveMapWriter.CellKey cell = new DungeonLiveMapWriter.CellKey(grid.gridX(), grid.gridZ());
        String owner = renderPlan.roomOwners().get(cell);
        if (owner != null) {
            return owner;
        }
        DungeonKnownRoomCatalog.MatchedRoom match = matchContaining(renderPlan, cell.x(), cell.z());
        if (match != null) {
            return "match-room:" + canonicalRoomName(match.template().name()) + ":" + match.components().size();
        }
        return "cell:" + cell.x() + "," + cell.z();
    }

    private static String canonicalRoomName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private static void drawScreenWaypoint(
        Minecraft client,
        GuiGraphicsExtractor graphics,
        BlockPos pos
    ) {
        String label = "MIMIC";
        Vec3 screen = client.gameRenderer.projectPointToScreen(new Vec3(
            pos.getX() + 0.5,
            pos.getY() + 1.35,
            pos.getZ() + 0.5
        ));
        if (!Double.isFinite(screen.x)
            || !Double.isFinite(screen.y)
            || !Double.isFinite(screen.z)
            || screen.z < -1.0
            || screen.z > 1.0) {
            return;
        }

        int screenX = (int) Math.round((screen.x + 1.0) * 0.5 * graphics.guiWidth());
        int screenY = (int) Math.round((1.0 - screen.y) * 0.5 * graphics.guiHeight());
        int markerSize = 18;
        int half = markerSize / 2;
        int labelWidth = client.font.width(label);
        int labelX = clampInt(screenX - labelWidth / 2, 2, Math.max(2, graphics.guiWidth() - labelWidth - 2));
        int markerX = clampInt(screenX - half, 2, Math.max(2, graphics.guiWidth() - markerSize - 2));
        int markerY = clampInt(screenY - half, 2, Math.max(2, graphics.guiHeight() - markerSize - client.font.lineHeight - 6));
        int labelY = markerY + markerSize + 2;

        graphics.fill(markerX - 2, markerY - 2, markerX + markerSize + 2, markerY + markerSize + 2, WAYPOINT_BACKGROUND);
        graphics.fill(markerX + 4, markerY + 4, markerX + markerSize - 4, markerY + markerSize - 4, WAYPOINT_FILL);
        graphics.outline(markerX, markerY, markerSize, markerSize, WAYPOINT_OUTLINE);
        graphics.text(client.font, label, labelX, labelY, TEXT_RED, true);
    }

    private static void drawChestHighlights(
        PoseStack poseStack,
        MultiBufferSource buffers,
        Vec3 camera,
        List<BlockPos> positions
    ) {
        poseStack.pushPose();
        try {
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            VertexConsumer vertices = buffers.getBuffer(RenderTypes.debugQuads());
            PoseStack.Pose pose = poseStack.last();
            for (BlockPos pos : positions) {
                drawBox(
                    pose,
                    vertices,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    pos.getX() + 1.0,
                    pos.getY() + 1.0,
                    pos.getZ() + 1.0,
                    HIGHLIGHT_RED,
                    HIGHLIGHT_GREEN,
                    HIGHLIGHT_BLUE,
                    HIGHLIGHT_ALPHA
                );
            }
        } finally {
            poseStack.popPose();
        }
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

    private void clear() {
        if (mimicChestPositions.isEmpty()
            && lastKnownMimicChestPositions.isEmpty()
            && observedMimicEntityIds.isEmpty()
            && observedLevel == null
            && lastScanTick == Long.MIN_VALUE) {
            return;
        }
        tracker.mapSnapshot().clearMimicRooms("mimic-clear");
        mimicChestPositions = List.of();
        lastKnownMimicChestPositions = List.of();
        observedMimicEntityIds = Set.of();
        observedLevel = null;
        lastPlayerRoom = null;
        mimicEncounterActive = false;
        mimicChestOpenedTick = Long.MIN_VALUE;
        mimicEntityMissingSinceTick = Long.MIN_VALUE;
        lastScanTick = Long.MIN_VALUE;
        lastRenderLogTick = Long.MIN_VALUE;
        lastRenderLogState = "";
        lastLoggedState = "";
        observedMimicMapRoom = null;
        observedMimicMapPos = null;
    }

    private void logRenderState(Vec3 camera, int sameRoomMarkers) {
        long nowTick = tracker.dungeonTick();
        if (lastRenderLogTick != Long.MIN_VALUE && nowTick - lastRenderLogTick < 20L) {
            return;
        }
        lastRenderLogTick = nowTick;
        BlockPos first = mimicChestPositions.getFirst();
        double dx = first.getX() + 0.5 - camera.x;
        double dy = first.getY() + 0.5 - camera.y;
        double dz = first.getZ() + 0.5 - camera.z;
        int distance = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
        String state = "render chests=" + mimicChestPositions.size()
            + " sameRoom=" + sameRoomMarkers
            + " pos=" + first.getX() + "," + first.getY() + "," + first.getZ()
            + " distance=" + distance;
        if (state.equals(lastRenderLogState)) {
            return;
        }
        lastRenderLogState = state;
        KungDebugRecorder.event("mimic-esp", state);
    }

    private void logState() {
        String state = "floor=" + tracker.runStats().floor()
            + " killed=" + tracker.runStats().mimicKilled()
            + " chests=" + mimicChestPositions.size()
            + " known=" + lastKnownMimicChestPositions.size()
            + " entities=" + observedMimicEntityIds.size()
            + " room=" + (lastPlayerRoom == null ? "none" : lastPlayerRoom.x() + "," + lastPlayerRoom.z())
            + " active=" + mimicEncounterActive
            + " chunks=" + lastScanStats.checkedChunks()
            + " blockEntities=" + lastScanStats.checkedBlockEntities()
            + " blockStateChunks=" + lastScanStats.blockStateScannedChunks();
        if (state.equals(lastLoggedState)) {
            return;
        }
        lastLoggedState = state;
        KungDebugRecorder.event("mimic-esp", state);
    }

    private int sameRoomMimicChestCount(Minecraft client) {
        DungeonLiveMapWriter.MatchRenderPlan renderPlan = tracker.renderPlan();
        int count = 0;
        for (BlockPos pos : mimicChestPositions) {
            if (playerInSameRoom(client, renderPlan, pos)) {
                count++;
            }
        }
        return count;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ScanStats(int checkedChunks, int checkedBlockEntities, int blockStateScannedChunks) {
        static ScanStats empty() {
            return new ScanStats(0, 0, 0);
        }
    }
}
