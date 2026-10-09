package com.github.beng420.kung.mixin;

import com.github.beng420.kung.feature.misc.HitboxesFeature;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every nametag, ours or not, is decided here: a stand with no name to show prints "Armor Stand", which says nothing. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void kung$hideTypeName(Entity entity, EntityRenderState state, float partialTick, CallbackInfo callbackInfo) {
        if (entity instanceof ArmorStand stand && HitboxesFeature.drawsTypeName(stand)) state.nameTag = null;
    }
}
