package com.github.beng420.kung.feature.misc;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.config.KungHudEditorState;
import com.github.beng420.kung.config.KungHudLayout;
import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

public final class FrozenBlazeHudFeature extends ConfigurableFeature<MiscConfig> {
    public static final FrozenBlazeHudFeature INSTANCE = new FrozenBlazeHudFeature();
    /** Wiki: the aura stops after 30 seconds idle; in game only walking resets it, turning does not. */
    static final int AFK_TICKS = 30 * 20;
    private static final int WIDTH = 136;
    private static final int HEIGHT = 26;
    private String set;
    private Vec3 lastPos;
    private int idleTicks;

    private FrozenBlazeHudFeature() { super(config -> config.misc); }

    @Override
    protected void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.PLAYER_LIST,
            Identifier.fromNamespaceAndPath(KungMod.MOD_ID, "frozen_blaze_hud"), (graphics, delta) -> render(graphics));
    }

    @Override
    public boolean isEnabled() { return initialized() && config().frozenBlazeHudEnabled(); }

    private void tick(Minecraft client) {
        var player = client.player;
        set = isEnabled() && player != null ? setName(Stream.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
            .map(slot -> player.getItemBySlot(slot).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("id", ""))
            .toList()) : null;
        if (set == null) {
            onReset();
            return;
        }
        // ponytail: client-side estimate of a server rule; read the aura's flame/snow particles if it drifts.
        idleTicks = player.position().equals(lastPos) ? Math.min(idleTicks + 1, AFK_TICKS) : 0;
        lastPos = player.position();
    }

    /** SkyBlock armor IDs, helmet to boots; only a full set grants the aura. */
    static String setName(List<String> ids) {
        if (ids.stream().allMatch(id -> id.startsWith("FROZEN_BLAZE_"))) return "Frozen Blaze";
        if (ids.stream().allMatch(id -> id.startsWith("BLAZE_"))) return "Blaze";
        return null;
    }

    @Override
    protected void onReset() {
        set = null;
        lastPos = null;
        idleTicks = 0;
    }

    private void render(GuiGraphicsExtractor graphics) {
        var client = Minecraft.getInstance();
        if (set == null || client.options.hideGui || client.screen != null || KungHudEditorState.externalEditing()) return;
        draw(graphics, config(), set, idleTicks);
    }

    private static void draw(GuiGraphicsExtractor graphics, MiscConfig config, String set, int idleTicks) {
        var font = Minecraft.getInstance().font;
        boolean on = idleTicks < AFK_TICKS;
        var status = Component.literal(set + " ").withColor(set.equals("Blaze") ? 0xFFAA00 : 0x55FFFF)
            .append(on ? Component.literal("on").withColor(0x55FF55) : Component.literal("off").withColor(0xFF5555));
        if (on) status.append(Component.literal(" " + (AFK_TICKS - idleTicks + 19) / 20 + "s").withColor(0xAAAAAA));
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(config.frozenBlazeHudX(), config.frozenBlazeHudY());
            float scale = config.frozenBlazeHudScale() / 100F;
            graphics.pose().scale(scale, scale);
            graphics.text(font, status, 4, 4, 0xFFFFFFFF, true);
            if (!on) graphics.text(font, "Walk a step to reactivate", 4, 15, 0xFFAAAAAA, true);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static void drawPreview(GuiGraphicsExtractor graphics, MiscConfig config) {
        draw(graphics, config, "Frozen Blaze", AFK_TICKS);
    }

    public static KungHudLayout.Bounds overlayBounds(MiscConfig config) {
        float scale = config.frozenBlazeHudScale() / 100F;
        return new KungHudLayout.Bounds(config.frozenBlazeHudX(), config.frozenBlazeHudY(),
            Math.round(WIDTH * scale), Math.round(HEIGHT * scale));
    }
}
