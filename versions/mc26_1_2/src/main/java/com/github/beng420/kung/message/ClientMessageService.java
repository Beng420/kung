package com.github.beng420.kung.message;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ClientMessageService {
    public Component component(KungMessages.Type type, String area, String message) {
        return KungMessages.component(type, area, message);
    }

    public void send(Minecraft client, KungMessages.Type type, String area, String message) {
        KungMessages.send(client, type, area, message);
    }
}
