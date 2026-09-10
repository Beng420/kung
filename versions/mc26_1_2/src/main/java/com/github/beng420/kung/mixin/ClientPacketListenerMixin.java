package com.github.beng420.kung.mixin;

import com.github.beng420.kung.feature.dungeon.DungeonEventRouter;
import com.github.beng420.kung.skyblock.HypixelInstanceTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    // TAIL runs after vanilla applies the packet on the client thread, never on Netty's read thread.
    @Inject(method = {"handleLogin", "handleRespawn", "handleConfigurationStart"}, at = @At("TAIL"))
    private void kung$onWorldChanged(CallbackInfo callbackInfo) {
        HypixelInstanceTracker.INSTANCE.observeWorldChangePacket();
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void kung$onPlayerPosition(CallbackInfo callbackInfo) {
        HypixelInstanceTracker.INSTANCE.observePlayerPosition();
    }

    @Inject(method = "handleSetScore", at = @At("TAIL"))
    private void kung$onScore(ClientboundSetScorePacket packet, CallbackInfo callbackInfo) {
        HypixelInstanceTracker.INSTANCE.observeSidebarScore(packet.owner());
    }

    @Inject(method = "handleSetPlayerTeamPacket", at = @At("TAIL"))
    private void kung$onTeam(ClientboundSetPlayerTeamPacket packet, CallbackInfo callbackInfo) {
        HypixelInstanceTracker.INSTANCE.observeSidebarTeam(packet);
    }

    @Inject(method = {"handleAddObjective", "handleSetDisplayObjective", "handleResetScore"}, at = @At("TAIL"))
    private void kung$onSidebar(CallbackInfo callbackInfo) {
        HypixelInstanceTracker.INSTANCE.observeSidebar();
    }

    @Inject(method = "handleTabListCustomisation", at = @At("TAIL"))
    private void kung$onTab(ClientboundTabListPacket packet, CallbackInfo callbackInfo) {
        HypixelInstanceTracker.INSTANCE.observeTabList(packet.header(), packet.footer());
    }

    @Inject(method = "handlePlayerInfoUpdate", at = @At("TAIL"))
    private void kung$onPlayerInfo(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo callbackInfo) {
        HypixelInstanceTracker.INSTANCE.observePlayerInfo(packet);
    }

    @Inject(method = "handlePlayerInfoRemove", at = @At("TAIL"))
    private void kung$onPlayerInfoRemove(ClientboundPlayerInfoRemovePacket packet, CallbackInfo callbackInfo) {
        HypixelInstanceTracker.INSTANCE.observePlayerInfoRemoved(packet.profileIds());
    }

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void kung$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo callbackInfo) {
        if (packet.getEventId() != EntityEvent.DEATH) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        Entity entity = packet.getEntity(client.level);
        DungeonEventRouter.observeEntityDeath(entity);
    }
}
