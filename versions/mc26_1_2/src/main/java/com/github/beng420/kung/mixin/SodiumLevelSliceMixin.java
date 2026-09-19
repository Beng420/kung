package com.github.beng420.kung.mixin;

import com.github.beng420.kung.config.category.DungeonConfig.PillarMaterial;
import com.github.beng420.kung.feature.dungeon.ColoredPillarsFeature;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumLevelSliceMixin {
    @Shadow @Final private ClientLevel level;
    @Unique private PillarMaterial kung$pillarMaterial;

    @Inject(method = "copyData", at = @At("HEAD"), require = 0)
    private void kung$capturePillarMaterial(CallbackInfo ci) {
        kung$pillarMaterial = ColoredPillarsFeature.materialFor(level);
    }

    // Both meshing and lazy lighting/occlusion queries use this render-only getter.
    @ModifyReturnValue(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), require = 0)
    private BlockState kung$colorPillar(BlockState original, int x, int y, int z) {
        return ColoredPillarsFeature.replace(x, y, z, original, kung$pillarMaterial);
    }
}
