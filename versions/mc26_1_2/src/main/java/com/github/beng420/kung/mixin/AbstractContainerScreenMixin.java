package com.github.beng420.kung.mixin;

import com.github.beng420.kung.feature.misc.LoadoutsAutoCloseFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Unique
    private Slot kung$extractingSlot;

    @Inject(method = "slotClicked", at = @At("TAIL"))
    private void kung$afterSlotClicked(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo callbackInfo) {
        LoadoutsAutoCloseFeature.observeSlotClick(
            (AbstractContainerScreen<?>) (Object) this,
            slot,
            slotId,
            button,
            input
        );
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void kung$beforeKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (LoadoutsAutoCloseFeature.handleLoadoutHotkey((AbstractContainerScreen<?>) (Object) this, event)) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void kung$beforeMouseClicked(
        MouseButtonEvent event,
        boolean doubleClick,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (LoadoutsAutoCloseFeature.handleLoadoutMouseHotkey((AbstractContainerScreen<?>) (Object) this, event)) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void kung$beforeExtractSlot(
        GuiGraphicsExtractor graphics,
        Slot slot,
        int mouseX,
        int mouseY,
        CallbackInfo callbackInfo
    ) {
        kung$extractingSlot = slot;
    }

    @Inject(method = "extractSlot", at = @At("RETURN"))
    private void kung$afterExtractSlot(
        GuiGraphicsExtractor graphics,
        Slot slot,
        int mouseX,
        int mouseY,
        CallbackInfo callbackInfo
    ) {
        kung$extractingSlot = null;
    }

    @ModifyArg(
        method = "extractSlot",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"
        ),
        index = 4
    )
    private String kung$loadoutStackSizeLabel(String original) {
        if (original != null) {
            return original;
        }
        return LoadoutsAutoCloseFeature.loadoutStackSizeLabel(
            (AbstractContainerScreen<?>) (Object) this,
            kung$extractingSlot
        );
    }
}
