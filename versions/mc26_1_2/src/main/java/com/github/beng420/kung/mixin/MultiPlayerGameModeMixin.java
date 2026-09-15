package com.github.beng420.kung.mixin;

import com.github.beng420.kung.feature.misc.LoadoutsAutoCloseFeature;
import com.github.beng420.kung.feature.garden.VisitorAlarmFeature;
import com.github.beng420.kung.feature.garden.FeastOverlayFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = {"startDestroyBlock", "continueDestroyBlock"}, at = @At("HEAD"))
    private void kung$observeCropClick(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> callbackInfo) {
        VisitorAlarmFeature.observeCropClick(pos);
        FeastOverlayFeature.observeCropClick(pos);
    }

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

    @Inject(method = "handleContainerInput", at = @At("HEAD"))
    private void kung$beforeContainerInput(
        int containerId,
        int slotId,
        int button,
        ContainerInput input,
        Player player,
        CallbackInfo callbackInfo
    ) {
        // Read the button before local inventory prediction can change its stack.
        VisitorAlarmFeature.observeContainerInput(containerId, slotId, button, input);
        FeastOverlayFeature.observeContainerInput(containerId, slotId, button, input);
    }
}
