package com.github.beng420.kung.mixin;

import com.github.beng420.kung.feature.misc.LoadoutsAutoCloseFeature;
import com.github.beng420.kung.util.ServerTpsTracker;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Inject(
        method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void kung$onChannelRead0(ChannelHandlerContext context, Packet<?> packet, CallbackInfo callbackInfo) {
        if (packet instanceof ClientboundOpenScreenPacket openScreenPacket
            && LoadoutsAutoCloseFeature.shouldSuppressLoadoutsOpen(
                openScreenPacket.getContainerId(),
                openScreenPacket.getTitle()
            )) {
            callbackInfo.cancel();
            return;
        }
        ServerTpsTracker.INSTANCE.observePacket(packet);
        if (packet instanceof ClientboundContainerSetSlotPacket slotPacket) {
            LoadoutsAutoCloseFeature.observeSlotUpdate(
                slotPacket.getContainerId(),
                slotPacket.getSlot(),
                slotPacket.getItem()
            );
        } else if (packet instanceof ClientboundContainerSetContentPacket contentPacket) {
            for (int slot = 0; slot < contentPacket.items().size(); slot++) {
                LoadoutsAutoCloseFeature.observeSlotUpdate(
                    contentPacket.containerId(),
                    slot,
                    contentPacket.items().get(slot)
                );
            }
        }
    }
}
