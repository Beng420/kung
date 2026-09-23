package com.github.beng420.kung.mixin;

import com.github.beng420.kung.feature.misc.HitboxesFeature;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reports where a held item is actually drawn, so Hitboxes can box the item rather than its holder. */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void kung$onItemSubmitted(ArmedEntityRenderState state, ItemStackRenderState itemState, ItemStack stack,
                                      HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int light,
                                      CallbackInfo callbackInfo) {
        HitboxesFeature.observeHeldItem(state.entityType, stack, poseStack.last().pose());
    }
}
