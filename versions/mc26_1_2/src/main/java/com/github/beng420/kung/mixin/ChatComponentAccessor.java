package com.github.beng420.kung.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("allMessages")
    List<GuiMessage> kung$getAllMessages();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> kung$getTrimmedMessages();

    @Accessor("chatScrollbarPos")
    int kung$getChatScrollbarPos();
}
