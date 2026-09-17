package com.github.beng420.kung.feature.misc;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.config.KungHudEditorState;
import com.github.beng420.kung.config.KungHudLayout;
import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.feature.dungeon.DungeonServerTickEvents;
import com.github.beng420.kung.skyblock.HypixelLocation;
import com.github.beng420.kung.util.KungDebugRecorder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

import static com.github.beng420.kung.feature.misc.BowDrawProgress.*;

public final class BowDrawIndicatorFeature extends ConfigurableFeature<MiscConfig> {
    public static final BowDrawIndicatorFeature INSTANCE = new BowDrawIndicatorFeature();
    private static final int WIDTH = 200;
    private static final int HEIGHT = 41;
    private static final int BAR_X = 6;
    private static final int BAR_WIDTH = WIDTH - BAR_X * 2;
    private final BowDrawProgress progress = new BowDrawProgress();
    private ItemStack bow = ItemStack.EMPTY;
    private InteractionHand hand;
    private Player owner;
    private ClientLevel level;
    private int slot;
    private long startedAtNanos;

    private BowDrawIndicatorFeature() { super(config -> config.misc); }

    @Override
    protected void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(this::validateDraw);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onReset());
        DungeonServerTickEvents.register(() -> {
            validateDraw(Minecraft.getInstance());
            progress.serverTick();
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.PLAYER_LIST,
            Identifier.fromNamespaceAndPath(KungMod.MOD_ID, "bow_draw_indicator"), (graphics, delta) -> render(graphics));
    }

    @Override
    public boolean isEnabled() { return initialized() && config().bowDrawIndicatorEnabled(); }

    /** Observe the actual local use transition, including server/compatibility-driven starts. */
    public void observeUse(Player player, InteractionHand usedHand) {
        var client = Minecraft.getInstance();
        if (!isEnabled() || player != client.player || client.level == null
            || client.getCurrentServer() == null || !HypixelLocation.isHypixelAddress(client.getCurrentServer().ip)
            || !player.isUsingItem() || player.getUsedItemHand() != usedHand
            || !chargeableBow(player.getUseItem())) return;
        owner = player;
        level = client.level;
        bow = player.getUseItem();
        hand = usedHand;
        slot = player.getInventory().getSelectedSlot();
        startedAtNanos = System.nanoTime();
        progress.start();
        KungDebugRecorder.event("bow-draw", "start hand=" + hand + " slot=" + slot);
    }

    public void observeStop(Player player) {
        if (player != owner) return;
        validateDraw(Minecraft.getInstance());
        stopDraw();
    }

    private void stopDraw() {
        if (!progress.active()) return;
        long now = System.nanoTime();
        KungDebugRecorder.event("bow-draw", "stop ticks=" + progress.ticks()
            + " heldMs=" + (now - startedAtNanos) / 1_000_000L);
        progress.stop(now);
    }

    static boolean chargeableBow(ItemStack stack) {
        if (!(stack.getItem() instanceof BowItem)) return false;
        var lore = stack.get(DataComponents.LORE);
        return lore == null || lore.lines().stream().noneMatch(line ->
            HypixelLocation.clean(line.getString()).matches("(?i)(?:.*\\s)?shortbow(?:\\s.*|:.*)?"));
    }

    private void validateDraw(Minecraft client) {
        if (owner == null) return;
        if (!isEnabled() || client.player != owner || client.level != level || !owner.isAlive()
            || hand == InteractionHand.MAIN_HAND && slot != owner.getInventory().getSelectedSlot()
            || !sameDrawBow(bow, owner.getItemInHand(hand))) {
            onReset();
            return;
        }
        // Vanilla replaces useItem with the current same-type stack after inventory updates.
        bow = owner.getItemInHand(hand);
        if (progress.active()) {
            if (!owner.isUsingItem()) stopDraw();
            else if (owner.getUsedItemHand() != hand || !ItemStack.isSameItem(bow, owner.getUseItem())) onReset();
        }
        if (!progress.visible(System.nanoTime())) onReset();
    }

    static boolean sameDrawBow(ItemStack previous, ItemStack current) {
        return !current.isEmpty() && (previous == current || ItemStack.isSameItem(previous, current) && chargeableBow(current));
    }

    @Override
    protected void onReset() {
        if (progress.active()) KungDebugRecorder.event("bow-draw", "reset ticks=" + progress.ticks() + " reason=context-or-item");
        progress.reset();
        bow = ItemStack.EMPTY;
        hand = null;
        owner = null;
        level = null;
    }

    @Override
    protected void onShutdown() { onReset(); }

    private void render(GuiGraphicsExtractor graphics) {
        var client = Minecraft.getInstance();
        validateDraw(client);
        if (!progress.visible(System.nanoTime()) || client.options.hideGui || client.screen != null || KungHudEditorState.externalEditing()) return;
        draw(graphics, config(), progress.ticks(), progress.active());
    }

    private static void draw(GuiGraphicsExtractor graphics, MiscConfig config, int ticks, boolean drawing) {
        var font = Minecraft.getInstance().font;
        int color = ticks < MIN_SHOT_TICKS ? 0xFFEF6868 : ticks < FULL_DRAW_TICKS ? 0xFFFFDA55 : 0xFF55DD88;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(config.bowDrawIndicatorX(), config.bowDrawIndicatorY());
            float scale = config.bowDrawIndicatorScale() / 100F;
            graphics.pose().scale(scale, scale);
            graphics.fill(0, 0, WIDTH, HEIGHT, 0x99111318);
            graphics.text(font, drawing ? "Bow Draw" : "Last Draw", BAR_X, 4, 0xFFFFFFFF, true);
            String power = "~" + Math.round(BowDrawProgress.power(ticks) * 100) + "% power";
            graphics.text(font, power, WIDTH - BAR_X - font.width(power), 4, color, true);
            graphics.fill(BAR_X - 1, 16, WIDTH - BAR_X + 1, 26, 0xFF101318);
            graphics.fill(BAR_X, 17, WIDTH - BAR_X, 25, 0xFF343A44);
            graphics.fill(BAR_X, 17, BAR_X + Math.round(BAR_WIDTH * BowDrawProgress.power(ticks)), 25, color);
            int previousLabelEnd = 0;
            for (int marker : config.bowDrawThresholds().stream().mapToInt(MiscConfig.BowDrawThreshold::ticks).distinct().sorted().toArray()) {
                int x = BAR_X + Math.round(BAR_WIDTH * BowDrawProgress.power(marker));
                int markerColor = ticks >= marker ? 0xFFFFFFFF : 0xFF939BA7;
                graphics.fill(x - 1, 15, x, 27, markerColor);
                String label = marker + "t";
                int labelX = Math.min(x - font.width(label) / 2, WIDTH - 2 - font.width(label));
                // Keep every tick mark; omit only labels that would overlap their neighbor.
                if (labelX >= previousLabelEnd + 2) {
                    graphics.text(font, label, labelX, 30, markerColor, true);
                    previousLabelEnd = labelX + font.width(label);
                }
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static void drawPreview(GuiGraphicsExtractor graphics, MiscConfig config) {
        draw(graphics, config, 13, true);
    }

    public static KungHudLayout.Bounds overlayBounds(MiscConfig config) {
        float scale = config.bowDrawIndicatorScale() / 100F;
        return new KungHudLayout.Bounds(config.bowDrawIndicatorX(), config.bowDrawIndicatorY(),
            Math.round(WIDTH * scale), Math.round(HEIGHT * scale));
    }
}
