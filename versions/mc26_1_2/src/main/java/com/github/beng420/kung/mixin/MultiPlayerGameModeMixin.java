package com.github.beng420.kung.mixin;

import com.github.beng420.kung.feature.misc.LoadoutsAutoCloseFeature;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "handleContainerInput", at = @At("TAIL"))
    private void kung$afterContainerInput(
        int containerId,
        int slotId,
        int button,
        ContainerInput input,
        Player player,
        CallbackInfo callbackInfo
    ) {
        LoadoutsAutoCloseFeature.observeContainerInput(containerId, slotId, button, input);
    }
}
