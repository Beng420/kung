package com.github.beng420.kung.message;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class KungMessages {
    public enum Type {
        INFO(ChatFormatting.WHITE),
        SUCCESS(ChatFormatting.GREEN),
        WARNING(ChatFormatting.YELLOW),
        ERROR(ChatFormatting.RED),
        DEBUG(ChatFormatting.GRAY);

        private final ChatFormatting bodyColor;

        Type(ChatFormatting bodyColor) {
            this.bodyColor = bodyColor;
        }
    }

    private KungMessages() {
    }

    public static Component info(String message) {
        return component(Type.INFO, "", message);
    }

    public static Component info(String area, String message) {
        return component(Type.INFO, area, message);
    }

    public static Component success(String area, String message) {
        return component(Type.SUCCESS, area, message);
    }

    public static Component warning(String area, String message) {
        return component(Type.WARNING, area, message);
    }

    public static Component error(String area, String message) {
        return component(Type.ERROR, area, message);
    }

    public static Component debug(String area, String message) {
        return component(Type.DEBUG, area, message);
    }

    public static Component detail(String message) {
        return Component.literal(message == null ? "" : message).withStyle(ChatFormatting.WHITE);
    }

    public static Component component(Type type, String area, String message) {
        Type resolvedType = type == null ? Type.INFO : type;
        MutableComponent component = Component.literal("[")
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("Kung").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        if (area != null && !area.isBlank()) {
            component.append(Component.literal(" " + area.trim()).withStyle(ChatFormatting.GRAY));
        }
        return component
            .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(message == null ? "" : message).withStyle(resolvedType.bodyColor));
    }

    public static void send(Minecraft client, Type type, String area, String message) {
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(component(type, area, message));
            }
        });
    }
}
