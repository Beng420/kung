package com.github.beng420.kung.mixin;

import com.github.beng420.kung.feature.misc.BowDrawIndicatorFeature;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "startUsingItem", at = @At("TAIL"))
    private void kung$onUseStarted(InteractionHand hand, CallbackInfo callbackInfo) {
        BowDrawIndicatorFeature.INSTANCE.observeUse((LocalPlayer) (Object) this, hand);
    }

    @Inject(method = "stopUsingItem", at = @At("TAIL"))
    private void kung$onUseStopped(CallbackInfo callbackInfo) {
        BowDrawIndicatorFeature.INSTANCE.observeStop((LocalPlayer) (Object) this);
    }
}
