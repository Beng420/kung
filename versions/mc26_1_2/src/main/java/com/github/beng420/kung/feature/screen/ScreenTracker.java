package com.github.beng420.kung.feature.screen;

import com.github.beng420.kung.config.KungConfig;

import com.github.beng420.kung.feature.misc.LoadoutsAutoCloseFeature;
import com.github.beng420.kung.message.KungMessages;
import com.github.beng420.kung.util.KungDebugRecorder;
import java.util.Locale;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

public final class ScreenTracker {
    private static OpenScreen currentScreen;
    private static String lastScreenKey = "";

    private ScreenTracker() {
    }

    public static void initializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(ScreenTracker::tick);
    }

    public static OpenScreen currentScreen() {
        return currentScreen;
    }

    public static boolean hasContainerOpen() {
        return currentScreen != null && currentScreen.container();
    }

    public static boolean isContainerOpen(String title) {
        return isContainerOpen(openTitle -> openTitle.equalsIgnoreCase(title));
    }

    public static boolean isContainerOpen(Predicate<String> titlePredicate) {
        return currentScreen != null
            && currentScreen.container()
            && titlePredicate.test(currentScreen.title());
    }

    private static void tick(Minecraft client) {
        OpenScreen nextScreen = openScreen(client.screen);
        String nextKey = nextScreen == null ? "" : nextScreen.key();
        if (nextKey.equals(lastScreenKey)) {
            currentScreen = nextScreen;
            return;
        }

        currentScreen = nextScreen;
        lastScreenKey = nextKey;
        LoadoutsAutoCloseFeature.observeScreenChange(client.screen);
        KungDebugRecorder.event("screen", nextScreen == null
            ? "closed"
            : "opened title=\"" + nextScreen.title() + "\" class=" + nextScreen.simpleClassName()
                + " container=" + nextScreen.container());
        if (nextScreen != null
            && nextScreen.container()
            && KungConfig.get().debug.interfaceMessagesEnabled()
            && client.player != null) {
            client.player.sendSystemMessage(KungMessages.debug(
                "Debug",
                "Opened interface: "
                    + nextScreen.title()
                    + " ("
                    + nextScreen.simpleClassName()
                    + ")"
            ));
        }
    }

    private static OpenScreen openScreen(Screen screen) {
        if (screen == null) {
            return null;
        }

        Component titleComponent = screen.getTitle();
        String title = titleComponent == null ? "" : titleComponent.getString().trim();
        String className = screen.getClass().getName();
        String simpleClassName = screen.getClass().getSimpleName();
        boolean container = screen instanceof AbstractContainerScreen<?>;
        return new OpenScreen(title, className, simpleClassName, container);
    }

    public record OpenScreen(String title, String className, String simpleClassName, boolean container) {
        String key() {
            return className + "|" + container + "|" + title.toLowerCase(Locale.ROOT);
        }
    }
}
