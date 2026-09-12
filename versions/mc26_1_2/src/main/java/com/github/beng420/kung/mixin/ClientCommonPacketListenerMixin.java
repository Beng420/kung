package com.github.beng420.kung.mixin;

import com.github.beng420.kung.feature.dungeon.DungeonServerTickEvents;
import com.github.beng420.kung.util.ServerTickSequence;
import com.github.beng420.kung.util.ServerTickPacketContext;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerMixin {
    @Unique
    private final ServerTickSequence kung$serverTicks = new ServerTickSequence();

    // TAIL keeps standalone tick pings in applied order with chat boundaries.
    // The bundle scope preserves provenance that this flattened handler would lose.
    @Inject(method = "handlePing", at = @At("TAIL"))
    private void kung$onPing(ClientboundPingPacket packet, CallbackInfo callbackInfo) {
        if (kung$serverTicks.accept(packet.getId(), ServerTickPacketContext.isApplyingBundle())) {
            DungeonServerTickEvents.post();
        }
    }
}
