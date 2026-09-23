package com.github.beng420.kung.feature.dungeon;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import com.github.beng420.kung.util.LineBoxes;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class M7DragonRenderer {
    private static final RenderStateDataKey<List<Marker>> MARKERS = RenderStateDataKey.create(() -> "kung:m7-dragons");
    private static final RenderStateDataKey<List<Trail>> TRAILS = RenderStateDataKey.create(() -> "kung:m7-dragon-trails");

    /** Corner indices per face; bit 0 picks x, bit 1 picks y, bit 2 picks z. */
    private static final int[][] FACES = {{0, 4, 6, 2}, {5, 1, 3, 7}, {0, 1, 5, 4},
        {6, 7, 3, 2}, {1, 0, 2, 3}, {4, 5, 7, 6}};
    private static boolean fillBroken;

    private M7DragonRenderer() { }

    static void initialize() {
        LevelRenderEvents.END_EXTRACTION.register(context -> {
            float partialTick = context.camera().getCameraEntityPartialTicks(context.deltaTracker());
            context.levelState().setData(MARKERS,
                List.copyOf(M7DragonFeature.INSTANCE.renderMarkers(partialTick)));
            context.levelState().setData(TRAILS, List.copyOf(M7DragonFeature.INSTANCE.renderTrails()));
        });
        LevelRenderEvents.END_MAIN.register(context -> {
            List<Marker> markers = context.levelState().getDataOrDefault(MARKERS, List.of());
            List<Trail> trails = context.levelState().getDataOrDefault(TRAILS, List.of());
            if (markers.isEmpty() && trails.isEmpty()) return;
            Vec3 camera = context.levelState().cameraRenderState.pos;
            var lineType = RenderTypes.lines();
            var lines = context.bufferSource().getBuffer(lineType);
            var pose = context.poseStack().last();
            for (Marker marker : markers) {
                if (!marker.filled()) LineBoxes.box(lines, pose, marker.bounds(), camera.x, camera.y, camera.z,
                    marker.color(), marker.width());
            }
            // A polyline: a cube per sample point cost a VoxelShape and 12 edges each, every frame.
            for (Trail trail : trails) {
                List<Vec3> points = trail.points();
                for (int index = 1; index < points.size(); index++) {
                    Vec3 from = points.get(index - 1);
                    Vec3 to = points.get(index);
                    LineBoxes.segment(lines, pose, (float) (from.x - camera.x), (float) (from.y - camera.y),
                        (float) (from.z - camera.z), (float) (to.x - camera.x), (float) (to.y - camera.y),
                        (float) (to.z - camera.z), trail.color(), 1.5F);
                }
            }
            context.bufferSource().endBatch(lineType);
            drawFilled(context, markers, camera);
        });
    }

    /**
     * Filled cubes through debugQuads (POSITION_COLOR - position and colour, nothing else), in their
     * own buffer AFTER the lines are flushed. A wrong vertex format once crashed the game from this
     * renderer; here a format surprise costs the fill, never the frame. It disables itself after the
     * first failure instead of throwing every frame.
     */
    private static void drawFilled(LevelRenderContext context, List<Marker> markers, Vec3 camera) {
        if (fillBroken) return;
        try {
            var quadType = RenderTypes.debugQuads();
            var quads = context.bufferSource().getBuffer(quadType);
            var pose = context.poseStack().last();
            for (Marker marker : markers) {
                if (!marker.filled()) continue;
                AABB box = marker.bounds().move(-camera.x, -camera.y, -camera.z);
                for (int[] face : FACES) {
                    for (int corner : face) {
                        quads.addVertex(pose,
                            (float) ((corner & 1) == 0 ? box.minX : box.maxX),
                            (float) ((corner & 2) == 0 ? box.minY : box.maxY),
                            (float) ((corner & 4) == 0 ? box.minZ : box.maxZ))
                            .setColor(marker.color());
                    }
                }
            }
            context.bufferSource().endBatch(quadType);
        } catch (RuntimeException exception) {
            fillBroken = true;
            com.github.beng420.kung.KungMod.LOGGER.warn("Dragon marker fill disabled: {}", exception.toString());
        }
    }

    record Marker(AABB bounds, int color, float width, boolean filled) {
        Marker(AABB bounds, int color, float width) { this(bounds, color, width, false); }
    }

    /** Flight path as a polyline; RenderTypes.lines draws it at its own thin width. */
    record Trail(List<Vec3> points, int color) { }
}
