package com.github.beng420.kung.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class KungChat {
    private KungChat() {
    }

    public static Component message(String message) {
        return message("", message);
    }

    public static Component message(String area, String message) {
        MutableComponent component = Component.literal("[")
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("Kung").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        if (area != null && !area.isBlank()) {
            component.append(Component.literal(" " + area).withStyle(ChatFormatting.GRAY));
        }
        return component
            .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
    }
}
