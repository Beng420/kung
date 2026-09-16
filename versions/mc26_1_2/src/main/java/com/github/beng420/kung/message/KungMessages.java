package com.github.beng420.kung.message;

import com.github.beng420.kung.mixin.ChatComponentAccessor;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
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
        send(client, component(type, area, message));
    }

    public static void send(Minecraft client, Component message) {
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(message);
            }
        });
    }

    /** Client-thread only. Retain the returned component to replace this local message next time. */
    public static Component replaceLocal(Minecraft client, Component previous, Component message) {
        var chat = client.gui.getChat();
        var access = (ChatComponentAccessor) chat;
        var lines = access.kung$getTrimmedMessages();
        int scroll = access.kung$getChatScrollbarPos();
        var anchor = scroll > 0 && scroll < lines.size() ? lines.get(scroll) : null;
        removeLocal(access.kung$getAllMessages(), lines, previous);
        chat.addClientSystemMessage(message);
        // Keep a scrolled reader on the same surviving line rather than rebuilding all chat wrapping.
        int anchorIndex = anchor == null ? -1 : lines.indexOf(anchor);
        chat.scrollChat(anchorIndex < 0 ? 0 : anchorIndex - access.kung$getChatScrollbarPos());
        return message;
    }

    static void removeLocal(List<GuiMessage> history, List<GuiMessage.Line> lines, Component previous) {
        if (previous == null) return;
        // Identity preserves unrelated messages with identical text, including server/player copies.
        history.removeIf(entry -> ownsLocal(entry, previous));
        lines.removeIf(line -> ownsLocal(line.parent(), previous));
    }

    private static boolean ownsLocal(GuiMessage entry, Component previous) {
        return entry.source() == GuiMessageSource.SYSTEM_CLIENT && entry.content() == previous;
    }
}
