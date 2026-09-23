package com.github.beng420.kung.feature.misc;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.config.KungHudEditorState;
import com.github.beng420.kung.config.KungHudLayout;
import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.util.ServerTpsTracker;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * TPS, FPS and ping in one line, plus an optional 30 second graph per stat. TPS comes from the
 * packet-arrival tracker, so a bad ping cannot pass itself off as server lag.
 */
public final class PerformanceHudFeature extends ConfigurableFeature<MiscConfig> {
    public static final PerformanceHudFeature INSTANCE = new PerformanceHudFeature();
    static final int SECONDS = 30;
    private static final int GRAPH_WIDTH = 120;
    private static final int GRAPH_HEIGHT = 24;
    private static final int TPS_COLOR = 0x55FFFF;
    private static final int FPS_COLOR = 0x55FF55;
    private static final int PING_COLOR = 0xFFAA00;
    /** One sample per second; the graph is as long as the deque. */
    private final Deque<float[]> history = new ArrayDeque<>();
    private int ticksUntilSample;

    private PerformanceHudFeature() { super(config -> config.misc); }

    @Override
    protected void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.PLAYER_LIST,
            Identifier.fromNamespaceAndPath(KungMod.MOD_ID, "performance_hud"), (graphics, delta) -> render(graphics));
    }

    @Override
    public boolean isEnabled() { return initialized() && config().performanceHudEnabled(); }

    private void tick(Minecraft client) {
        if (--ticksUntilSample > 0) return;
        ticksUntilSample = 20;
        if (!isEnabled() || client.level == null) {
            history.clear();
            return;
        }
        history.addLast(new float[] {(float) tps(), client.getFps(), ping(client)});
        while (history.size() > SECONDS) history.removeFirst();
    }

    private static double tps() {
        var snapshot = ServerTpsTracker.INSTANCE.snapshot();
        return snapshot.available() ? snapshot.current() : 0;
    }

    private static int ping(Minecraft client) {
        var connection = client.getConnection();
        var info = connection == null || client.player == null ? null : connection.getPlayerInfo(client.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private void render(GuiGraphicsExtractor graphics) {
        var client = Minecraft.getInstance();
        if (!isEnabled() || client.level == null || client.options.hideGui || client.screen != null
            || KungHudEditorState.externalEditing()) return;
        draw(graphics, config(), List.copyOf(history), (float) tps(), client.getFps(), ping(client));
    }

    private static void draw(GuiGraphicsExtractor graphics, MiscConfig config, List<float[]> samples,
                             float tps, int fps, int ping) {
        var font = Minecraft.getInstance().font;
        var line = Component.literal(String.format(Locale.ROOT, "TPS: %.1f ", tps)).withColor(TPS_COLOR)
            .append(Component.literal("FPS: " + fps + " ").withColor(FPS_COLOR))
            .append(Component.literal("Ping: " + ping + "ms").withColor(PING_COLOR));
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(config.performanceHudX(), config.performanceHudY());
            float scale = config.performanceHudScale() / 100F;
            graphics.pose().scale(scale, scale);
            graphics.text(font, line, 4, 4, 0xFFFFFFFF, true);
            int top = 16;
            if (config.performanceGraphTps()) top = graph(graphics, samples, 0, 20F, TPS_COLOR, top);
            if (config.performanceGraphFps()) top = graph(graphics, samples, 1, 0F, FPS_COLOR, top);
            if (config.performanceGraphPing()) graph(graphics, samples, 2, 0F, PING_COLOR, top);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /** One stat over the last SECONDS seconds; returns the next free y. */
    private static int graph(GuiGraphicsExtractor graphics, List<float[]> samples, int stat, float fixedMax,
                             int color, int top) {
        graphics.fill(4, top, 4 + GRAPH_WIDTH, top + GRAPH_HEIGHT, 0x60000000);
        float max = fixedMax;
        for (float[] sample : samples) max = Math.max(max, sample[stat]);
        if (max <= 0) return top + GRAPH_HEIGHT + 2;
        for (int index = 0; index < samples.size(); index++) {
            int x = 4 + index * GRAPH_WIDTH / SECONDS;
            int height = Math.max(1, Math.round(samples.get(index)[stat] / max * GRAPH_HEIGHT));
            graphics.fill(x, top + GRAPH_HEIGHT - height, x + Math.max(1, GRAPH_WIDTH / SECONDS - 1),
                top + GRAPH_HEIGHT, 0xFF000000 | color);
        }
        return top + GRAPH_HEIGHT + 2;
    }

    /** Graph rows only exist while their stat is on; the HUD box has to match. */
    static int height(MiscConfig config) {
        int graphs = (config.performanceGraphTps() ? 1 : 0) + (config.performanceGraphFps() ? 1 : 0)
            + (config.performanceGraphPing() ? 1 : 0);
        return 16 + graphs * (GRAPH_HEIGHT + 2);
    }

    public static void drawPreview(GuiGraphicsExtractor graphics, MiscConfig config) {
        var samples = new ArrayDeque<float[]>();
        for (int second = 0; second < SECONDS; second++) {
            samples.addLast(new float[] {19 + (second % 3) * 0.5F, 110 + second % 17, 120 + second % 40});
        }
        draw(graphics, config, List.copyOf(samples), 19.9F, 118, 146);
    }

    public static KungHudLayout.Bounds overlayBounds(MiscConfig config) {
        float scale = config.performanceHudScale() / 100F;
        boolean graphs = config.performanceGraphTps() || config.performanceGraphFps() || config.performanceGraphPing();
        int width = graphs ? GRAPH_WIDTH + 8 : 120;
        return new KungHudLayout.Bounds(config.performanceHudX(), config.performanceHudY(),
            Math.round(width * scale), Math.round(height(config) * scale));
    }
}
