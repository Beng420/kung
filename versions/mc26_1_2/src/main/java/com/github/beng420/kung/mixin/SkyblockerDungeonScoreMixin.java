package com.github.beng420.kung.mixin;

import com.github.beng420.kung.feature.dungeon.DungeonBonusContribution;
import com.github.beng420.kung.feature.dungeon.DungeonEventRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Optional, event-only bridge: no dependency, polling or stale flags from a previous run.
@Pseudo
@Mixin(targets = "de.hysky.skyblocker.skyblock.dungeon.DungeonScore", remap = false)
public abstract class SkyblockerDungeonScoreMixin {
    @Inject(method = "onMimicKill()V", at = @At("TAIL"), require = 0)
    private static void kung$onMimicKill(CallbackInfo ci) {
        DungeonEventRouter.observeSkyblockerBonus(DungeonBonusContribution.MIMIC);
    }

    @Inject(method = "onPrinceKill(Z)V", at = @At("TAIL"), require = 0)
    private static void kung$onPrinceKill(boolean announce, CallbackInfo ci) {
        DungeonEventRouter.observeSkyblockerBonus(DungeonBonusContribution.PRINCE);
    }

    @Inject(method = "onBatKill(Z)V", at = @At("TAIL"), require = 0)
    private static void kung$onBatKill(boolean announce, CallbackInfo ci) {
        DungeonEventRouter.observeSkyblockerBonus(DungeonBonusContribution.BAT);
    }
}
