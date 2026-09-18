package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.util.KungDebugRecorder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

final class IceSprayHighlightRenderer {
    private static final RenderStateDataKey<List<AABB>> BOXES = RenderStateDataKey.create(() -> "kung:ice-spray");
    private static final int[][] FACES = {{0, 4, 6, 2}, {5, 1, 3, 7}, {0, 1, 5, 4},
        {6, 7, 3, 2}, {1, 0, 2, 3}, {4, 5, 7, 6}};
    private static final int OUTLINE = 0xFF99DDFF;
    private static final int FILL = 0x3399DDFF; // 51 / 255 = 20% alpha per face.
    private static Set<Integer> lastExtractedTargets = Set.of();
    private static int lastDrawnCount;

    private IceSprayHighlightRenderer() { }

    static AABB scaledBounds(AABB bounds, int percent) {
        double extra = (Math.clamp(percent, 100, 200) / 100.0 - 1) / 2;
        // Retain the existing two-centimetre clearance at normal size to avoid coincident outlines.
        return bounds.inflate(Math.max(0.02, bounds.getXsize() * extra),
            Math.max(0.02, bounds.getYsize() * extra), Math.max(0.02, bounds.getZsize() * extra));
    }

    static void initialize() {
        LevelRenderEvents.END_EXTRACTION.register(context -> {
            List<AABB> boxes = new ArrayList<>();
            Set<Integer> targets = new HashSet<>();
            var camera = context.camera();
            Vec3 position = camera.position();
            float partialTick = camera.getCameraEntityPartialTicks(context.deltaTracker());
            int size = KungConfig.get().dungeon.iceSprayBoxSize();
            for (Entity entity : DungeonDebuffFeature.INSTANCE.highlightedEntities()) {
                if (!entity.shouldRender(position.x, position.y, position.z)) continue;
                boxes.add(scaledBounds(entity.getBoundingBox(), size)
                    .move(entity.getPosition(partialTick).subtract(entity.position())));
                targets.add(entity.getId());
            }
            if (!targets.equals(lastExtractedTargets)) {
                KungDebugRecorder.event("dungeon-debuff", "ice-boxes extracted=" + boxes.size()
                    + " entityIds=" + targets + " size=" + size + " camera=" + position + " bounds=" + boxes);
                lastExtractedTargets = Set.copyOf(targets);
            }
            context.levelState().setData(BOXES, List.copyOf(boxes));
        });
        LevelRenderEvents.END_MAIN.register(context -> {
            List<AABB> boxes = context.levelState().getDataOrDefault(BOXES, List.of());
            if (boxes.size() != lastDrawnCount) {
                KungDebugRecorder.event("dungeon-debuff", "ice-boxes draw=" + boxes.size());
                lastDrawnCount = boxes.size();
            }
            if (boxes.isEmpty()) return;
            Vec3 camera = context.levelState().cameraRenderState.pos;
            var fillType = RenderTypes.debugQuads();
            var fill = context.bufferSource().getBuffer(fillType);
            var pose = context.poseStack().last();
            for (AABB box : boxes) {
                AABB relative = box.move(-camera.x, -camera.y, -camera.z);
                for (int[] face : FACES) {
                    for (int vertex : face) {
                        fill.addVertex(pose,
                            (float) ((vertex & 1) == 0 ? relative.minX : relative.maxX),
                            (float) ((vertex & 2) == 0 ? relative.minY : relative.maxY),
                            (float) ((vertex & 4) == 0 ? relative.minZ : relative.maxZ)).setColor(FILL);
                    }
                }
            }
            context.bufferSource().endBatch(fillType);
            var lineType = RenderTypes.lines();
            var lines = context.bufferSource().getBuffer(lineType);
            for (AABB box : boxes) ShapeRenderer.renderShape(context.poseStack(), lines, Shapes.create(box),
                -camera.x, -camera.y, -camera.z, OUTLINE, 2.0F);
            context.bufferSource().endBatch(lineType);
        });
    }
}
