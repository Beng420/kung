package com.github.beng420.kung.feature.safari;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.config.KungHudEditorScreen;
import com.github.beng420.kung.config.category.SafariConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.feature.Feature;
import com.github.beng420.kung.skyblock.HypixelInstanceTracker;
import com.github.beng420.kung.util.KungDebugRecorder;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class SafariOverlayFeature extends ConfigurableFeature<SafariConfig> implements Feature {
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(KungMod.MOD_ID, "safari_uniques");
    private static final int COLUMN_WIDTH = 108;
    private static final int PADDING = 4;
    private static final int ROW_HEIGHT = 11;
    private static final int WIDTH = COLUMN_WIDTH * SafariCritters.Region.values().length + PADDING * 2;
    private static final int HEIGHT = 16 + ROW_HEIGHT * 10 + PADDING;
    private static final Set<String> EXAMPLE = Set.of("Foxtrot", "Honeybug", "Hideonfloor", "Flitter",
        "Driftling", "Tepid", "Mantis Shrimp", "Bloodbat");
    private final SafariSession session = new SafariSession();

    public SafariOverlayFeature() { super(config -> config.safari); }

    @Override
    protected void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        registerMessageObserver(this::observeMessage);
        HudElementRegistry.attachElementBefore(VanillaHudElements.PLAYER_LIST, HUD_ID,
            (graphics, delta) -> render(graphics));
    }

    static void registerMessageObserver(Consumer<String> observer) {
        // Observe before chat mods cancel or rewrite a catch; never change message visibility.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) observer.accept(message.getString());
            return true;
        });
    }

    @Override
    public boolean isEnabled() { return initialized() && config().enabled(); }

    @Override
    protected void onReset() {
        session.reset();
    }

    private boolean updateContext(Minecraft client) {
        if (!isEnabled()) {
            onReset();
            return false;
        }
        var server = client.getCurrentServer();
        if (server == null || !isHypixel(server.ip)) {
            onReset();
            return false;
        }
        // Synchronize on chat as well as ticks so a catch cannot be assigned to the old epoch.
        var tracker = HypixelInstanceTracker.INSTANCE;
        session.select(tracker.instanceEpoch(), tracker.instanceLine());
        return client.player != null && client.level != null;
    }

    private void tick(Minecraft client) {
        updateContext(client);
    }

    private void observeMessage(String message) {
        Minecraft client = Minecraft.getInstance();
        if (!updateContext(client)) return;
        if (session.observeMessage(message, client.player.getGameProfile().name())) {
            KungDebugRecorder.event("safari-uniques", "epoch=" + HypixelInstanceTracker.INSTANCE.instanceEpoch()
                + " unique=" + session.caught().size() + " message=" + message);
        }
    }

    private void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || client.player == null || client.level == null || client.options.hideGui
            || client.screen instanceof KungHudEditorScreen) return;
        if (updateContext(client) && session.active()) draw(graphics, config(), session.caught());
    }

    public static void drawPreview(GuiGraphicsExtractor graphics, SafariConfig config) {
        draw(graphics, config, EXAMPLE);
    }

    private static void draw(GuiGraphicsExtractor graphics, SafariConfig config, Set<String> caught) {
        var font = Minecraft.getInstance().font;
        float scale = config.scale() / 100.0F;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(config.x(), config.y());
            graphics.pose().scale(scale, scale);
            int column = 0;
            for (SafariCritters.Region region : SafariCritters.Region.values()) {
                int x = PADDING + column++ * COLUMN_WIDTH;
                int count = count(region, caught);
                String header = region.title() + " (" + count + "/" + region.critters().size() + ")";
                graphics.text(font, header, x, PADDING,
                    count == region.critters().size() ? 0xFF55FF55 : 0xFFFFFFFF, true);
                int row = 0;
                for (String name : region.critters()) {
                    graphics.text(font, critterText(name, caught.contains(name)), x,
                        16 + row++ * ROW_HEIGHT, 0xFFFFFFFF, true);
                }
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    static int count(SafariCritters.Region region, Set<String> caught) {
        return (int) region.critters().stream().filter(caught::contains).count();
    }

    static Component critterText(String name, boolean caught) {
        return caught ? Component.literal(name).withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH)
            : Component.literal(name).withStyle(ChatFormatting.RED);
    }

    public static OverlayBounds overlayBounds(SafariConfig config) {
        float scale = config.scale() / 100.0F;
        return new OverlayBounds(config.x(), config.y(), Math.round(WIDTH * scale), Math.round(HEIGHT * scale));
    }

    static boolean isHypixel(String address) {
        if (address == null || address.isBlank()) return false;
        String host = ServerAddress.parseString(address).getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }

    public record OverlayBounds(int x, int y, int width, int height) {}
}
