package com.github.beng420.kung.feature.misc;

import com.github.beng420.kung.config.category.HitboxesConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public final class HitboxesFeature extends ConfigurableFeature<HitboxesConfig> {
    private static final RenderStateDataKey<List<Box>> BOXES = RenderStateDataKey.create(() -> "kung:hitboxes");

    public HitboxesFeature() { super(config -> config.hitboxes); }

    @Override
    protected void onInitialize() {
        LevelRenderEvents.END_EXTRACTION.register(this::extract);
        LevelRenderEvents.END_MAIN.register(HitboxesFeature::render);
    }

    @Override
    public boolean isEnabled() { return config().enabled(); }

    private void extract(LevelExtractionContext context) {
        List<Box> boxes = new ArrayList<>();
        var selected = config().entities();
        if (isEnabled() && !selected.isEmpty()) {
            var camera = context.camera();
            Vec3 position = camera.position();
            float partialTick = camera.getCameraEntityPartialTicks(context.deltaTracker());
            for (Entity entity : context.level().entitiesForRendering()) {
                Integer color = selected.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
                if (color == null || entity.isRemoved() || !entity.shouldRender(position.x, position.y, position.z)
                    || entity == camera.entity() && !camera.isDetached()) continue;
                if (!(entity instanceof EnderDragon) || config().dragonOverallBox()) {
                    boxes.add(new Box(interpolatedBounds(entity, partialTick), color));
                }
                if (entity instanceof EnderDragon dragon && config().dragonPartBoxes()) {
                    for (var part : dragon.getSubEntities()) boxes.add(new Box(interpolatedBounds(part, partialTick), color));
                }
            }
        }
        // Each frame owns immutable geometry, so world changes and disabling cannot leave old entities behind.
        context.levelState().setData(BOXES, List.copyOf(boxes));
    }

    private static AABB interpolatedBounds(Entity entity, float partialTick) {
        return entity.getBoundingBox().move(entity.getPosition(partialTick).subtract(entity.position()));
    }

    private static void render(LevelRenderContext context) {
        List<Box> boxes = context.levelState().getDataOrDefault(BOXES, List.of());
        if (boxes.isEmpty()) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        var lines = RenderTypes.lines();
        var vertices = context.bufferSource().getBuffer(lines);
        for (Box box : boxes) {
            ShapeRenderer.renderShape(context.poseStack(), vertices, Shapes.create(box.bounds()),
                -camera.x, -camera.y, -camera.z, box.color(), 2.0F);
        }
        context.bufferSource().endBatch(lines);
    }

    private record Box(AABB bounds, int color) { }
}
