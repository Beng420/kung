package com.github.beng420.kung.mixin;

import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void kung$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo callbackInfo) {
        if (packet.getEventId() != EntityEvent.DEATH) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        Entity entity = packet.getEntity(client.level);
        DungeonStateTracker.observeEntityDeath(entity);
    }
}
