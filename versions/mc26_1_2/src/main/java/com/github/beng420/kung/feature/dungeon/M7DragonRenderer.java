package com.github.beng420.kung.feature.dungeon;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

final class M7DragonRenderer {
    private static final RenderStateDataKey<List<Marker>> MARKERS = RenderStateDataKey.create(() -> "kung:m7-dragons");
    private static final int[][] FACES = {{0, 4, 6, 2}, {5, 1, 3, 7}, {0, 1, 5, 4},
        {6, 7, 3, 2}, {1, 0, 2, 3}, {4, 5, 7, 6}};

    private M7DragonRenderer() { }

    static void initialize() {
        LevelRenderEvents.END_EXTRACTION.register(context -> {
            float partialTick = context.camera().getCameraEntityPartialTicks(context.deltaTracker());
            context.levelState().setData(MARKERS,
                List.copyOf(M7DragonFeature.INSTANCE.renderMarkers(partialTick)));
        });
        LevelRenderEvents.END_MAIN.register(context -> {
            List<Marker> markers = context.levelState().getDataOrDefault(MARKERS, List.of());
            if (markers.isEmpty()) return;
            Vec3 camera = context.levelState().cameraRenderState.pos;
            // Vanilla's untextured, no-depth quads keep the tiny point visible inside the dragon model.
            var fillType = RenderTypes.textBackgroundSeeThrough();
            var fill = context.bufferSource().getBuffer(fillType);
            var pose = context.poseStack().last();
            for (Marker marker : markers) {
                if (!marker.filled()) continue;
                AABB relative = marker.bounds().move(-camera.x, -camera.y, -camera.z);
                for (int[] face : FACES) {
                    for (int vertex : face) {
                        fill.addVertex(pose,
                            (float) ((vertex & 1) == 0 ? relative.minX : relative.maxX),
                            (float) ((vertex & 2) == 0 ? relative.minY : relative.maxY),
                            (float) ((vertex & 4) == 0 ? relative.minZ : relative.maxZ))
                            .setColor(marker.color()).setLight(LightCoordsUtil.FULL_BRIGHT);
                    }
                }
            }
            context.bufferSource().endBatch(fillType);
            var lineType = RenderTypes.lines();
            var lines = context.bufferSource().getBuffer(lineType);
            for (Marker marker : markers) {
                if (marker.filled()) continue;
                ShapeRenderer.renderShape(context.poseStack(), lines, Shapes.create(marker.bounds()),
                    -camera.x, -camera.y, -camera.z, marker.color(), 2.0F);
            }
            context.bufferSource().endBatch(lineType);
        });
    }

    record Marker(AABB bounds, int color, boolean filled) { }
}
