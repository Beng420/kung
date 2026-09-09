package com.github.beng420.kung.message;

import net.minecraft.client.Minecraft;

public final class HypixelChatSender {
    public static final HypixelChatSender INSTANCE = new HypixelChatSender();
    public static final String PREFIX = "[Kung] ";

    private HypixelChatSender() {
    }

    public String prefixedMessage(String message) {
        String body = message == null ? "" : message.trim();
        return body.startsWith(PREFIX) ? body : PREFIX + body;
    }

    public String command(String commandPrefix, String message) {
        String prefix = commandPrefix == null ? "" : commandPrefix.trim();
        while (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }
        if (prefix.isBlank()) {
            throw new IllegalArgumentException("A Hypixel chat command prefix is required.");
        }
        return prefix + " " + prefixedMessage(message);
    }

    public void send(Minecraft client, String commandPrefix, String message) {
        if (client == null || client.player == null || client.player.connection == null) {
            return;
        }
        client.player.connection.sendCommand(command(commandPrefix, message));
    }

    public void sendParty(Minecraft client, String message) {
        send(client, "pc", message);
    }
}
