package com.github.beng420.kung.config;

import static com.github.beng420.kung.util.GuiDraw.fill;

import com.github.beng420.kung.feature.dungeon.DungeonMapFeature;
import com.github.beng420.kung.feature.dungeon.DungeonSplitsOverlayFeature;
import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import com.github.beng420.kung.feature.misc.SuperpairsHelperFeature;
import com.github.beng420.kung.ui.UiTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class KungHudEditorScreen extends Screen {
    private static final int BACKDROP = 0x33000000;
    private static final int HUD_BOX = 0x669AA4B2;
    private static final int HUD_BOX_HOVER = 0x889AA4B2;
    private static final int HUD_BOX_DRAG = 0xAA3498DB;
    private static final int BORDER = 0xCCFFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFD4DAE3;
    private static final int TOOLTIP_BG = 0xEE111318;
    private static final int MAP_CONTROLS_WIDTH = 214;
    private static final int MAP_CONTROLS_HEIGHT = 70;
    private static final int SLIDER_WIDTH = 100;
    private static final int SLIDER_HEIGHT = 18;

    private final DungeonStateTracker dungeonStateTracker;
    private HudEntry dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private SettingEntry draggingSlider;

    public KungHudEditorScreen(DungeonStateTracker dungeonStateTracker) {
        super(Component.literal("Kung HUD Editor"));
        this.dungeonStateTracker = dungeonStateTracker;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        fill(graphics, 0, 0, width, height, BACKDROP);
        graphics.text(font, "Kung HUD Editor", 10, 10, TEXT, true);
        graphics.text(font, "Drag to move. Scroll to resize. Right click for settings.", 10, 22, MUTED, true);

        List<HudEntry> entries = hudEntries();

        HudEntry hovered = inMapControls(mouseX, mouseY) ? null : hoveredEntry(entries, mouseX, mouseY);
        HudEntry currentDragging = currentEntryFor(dragging, entries);
        for (HudEntry entry : entries) {
            boolean isHovered = hovered != null && hovered.featureName().equals(entry.featureName());
            drawHudBox(
                graphics,
                entry,
                isHovered,
                currentDragging != null && currentDragging.featureName().equals(entry.featureName())
            );
        }

        if (currentDragging != null) {
            String coords = "x " + currentDragging.configX().getAsInt()
                + "  y " + currentDragging.configY().getAsInt();
            graphics.text(
                font,
                coords,
                currentDragging.x() + 4,
                currentDragging.y() + currentDragging.height() + 5,
                TEXT,
                true
            );
        } else if (hovered != null) {
            drawTooltip(graphics, hovered, mouseX, mouseY);
        }
        drawMapControls(graphics);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int button = event.button();

        if (inMapControls(mouseX, mouseY)) {
            if (button == 0) {
                List<SettingEntry> sliders = mapSliders();
                for (int index = 0; index < sliders.size(); index++) {
                    int y = mapControlsY() + 21 + index * 22;
                    if (mouseX >= sliderX() && mouseY >= y && mouseY < y + SLIDER_HEIGHT) {
                        draggingSlider = sliders.get(index);
                        draggingSlider.click(mouseX, sliderX(), SLIDER_WIDTH);
                        return true;
                    }
                }
            }
            return true;
        }

        HudEntry entry = hoveredEntry(hudEntries(), mouseX, mouseY);
        if (entry != null) {
            if (button == 0) {
                dragging = entry;
                dragOffsetX = mouseX - entry.x();
                dragOffsetY = mouseY - entry.y();
                return true;
            }
            if (button == 1) {
                Minecraft.getInstance().setScreen(new KungConfigScreen(entry.featureName()));
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingSlider != null) {
            draggingSlider.click((int) event.x(), sliderX(), SLIDER_WIDTH);
            return true;
        }
        if (dragging == null) {
            return super.mouseDragged(event, dragX, dragY);
        }

        int nextX = (int) event.x() - dragOffsetX - dragging.configOffsetX();
        int nextY = (int) event.y() - dragOffsetY - dragging.configOffsetY();
        dragging.setX().accept(nextX);
        dragging.setY().accept(nextY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0 || inMapControls((int) mouseX, (int) mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        HudEntry hovered = hoveredEntry(hudEntries(), (int) mouseX, (int) mouseY);
        if (hovered == null || hovered.setScale() == null || hovered.scale() == null) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        int step = scrollY > 0 ? 5 : -5;
        hovered.setScale().accept(Math.clamp(hovered.scale().getAsInt() + step, 25, 300));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSlider != null) {
            draggingSlider = null;
            return true;
        }
        if (dragging != null) {
            dragging = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private List<HudEntry> hudEntries() {
        KungConfig config = KungConfig.get();
        List<HudEntry> entries = new ArrayList<>();

        DungeonMapFeature.OverlayBounds dungeonMapBounds = DungeonMapFeature.overlayBounds(config.dungeon);
        entries.add(new HudEntry(
            "Dungeon Map",
            dungeonMapBounds.x(),
            dungeonMapBounds.y(),
            dungeonMapBounds.width(),
            dungeonMapBounds.height(),
            dungeonMapBounds.x() - config.dungeon.x(),
            dungeonMapBounds.y() - config.dungeon.y(),
            config.dungeon::x,
            config.dungeon::y,
            config.dungeon::setX,
            config.dungeon::setY,
            config.dungeon::scale,
            config.dungeon::setScale
        ));

        DungeonSplitsOverlayFeature.OverlayBounds splitsBounds =
            DungeonSplitsOverlayFeature.overlayBounds(config.splits, dungeonStateTracker.splitTracker());
        entries.add(new HudEntry(
            "Splits Overlay",
            splitsBounds.x(),
            splitsBounds.y(),
            splitsBounds.width(),
            splitsBounds.height(),
            splitsBounds.x() - config.splits.x(),
            splitsBounds.y() - config.splits.y(),
            config.splits::x,
            config.splits::y,
            config.splits::setX,
            config.splits::setY,
            config.splits::scale,
            config.splits::setScale
        ));

        SuperpairsHelperFeature.OverlayBounds superpairsBounds = SuperpairsHelperFeature.overlayBounds(config.misc);
        entries.add(new HudEntry(
            "Superpairs Helper",
            superpairsBounds.x(),
            superpairsBounds.y(),
            superpairsBounds.width(),
            superpairsBounds.height(),
            superpairsBounds.x() - config.misc.superpairsHelperX(),
            superpairsBounds.y() - config.misc.superpairsHelperY(),
            config.misc::superpairsHelperX,
            config.misc::superpairsHelperY,
            config.misc::setSuperpairsHelperX,
            config.misc::setSuperpairsHelperY,
            config.misc::superpairsHelperScale,
            config.misc::setSuperpairsHelperScale
        ));

        return entries;
    }

    private void drawHudBox(GuiGraphicsExtractor graphics, HudEntry entry, boolean hovered, boolean draggingEntry) {
        int color = draggingEntry ? HUD_BOX_DRAG : hovered ? HUD_BOX_HOVER : HUD_BOX;
        fill(graphics, entry.x(), entry.y(), entry.x() + entry.width(), entry.y() + entry.height(), color);
        fill(graphics, entry.x(), entry.y(), entry.x() + entry.width(), entry.y() + 1, BORDER);
        fill(graphics, entry.x(), entry.y() + entry.height() - 1, entry.x() + entry.width(), entry.y() + entry.height(), BORDER);
        fill(graphics, entry.x(), entry.y(), entry.x() + 1, entry.y() + entry.height(), BORDER);
        fill(graphics, entry.x() + entry.width() - 1, entry.y(), entry.x() + entry.width(), entry.y() + entry.height(), BORDER);
        drawCentered(graphics, entry.featureName(), entry.x() + entry.width() / 2, entry.y() + entry.height() / 2 - 4);
    }

    private void drawTooltip(GuiGraphicsExtractor graphics, HudEntry entry, int mouseX, int mouseY) {
        String lineOne = entry.featureName() + "  " + entry.scale().getAsInt() + "%";
        String lineTwo = "Drag to move, scroll to resize, right click for settings";
        int tooltipWidth = Math.max(font.width(lineOne), font.width(lineTwo)) + 10;
        int x = Math.min(mouseX + 12, width - tooltipWidth - 4);
        int y = Math.min(mouseY + 12, height - 30);
        fill(graphics, x, y, x + tooltipWidth, y + 28, TOOLTIP_BG);
        graphics.text(font, lineOne, x + 5, y + 5, TEXT, true);
        graphics.text(font, lineTwo, x + 5, y + 16, MUTED, true);
    }

    private List<SettingEntry> mapSliders() {
        var config = KungConfig.get().dungeon;
        return List.of(
            SettingEntry.slider("Map Scale", config::scale, config::setScale, 25, 300, 5),
            SettingEntry.slider("Text Scale", config::textScale, config::setTextScale, 50, 200, 5)
        );
    }

    private void drawMapControls(GuiGraphicsExtractor graphics) {
        int x = mapControlsX();
        int y = mapControlsY();
        fill(graphics, x, y, x + MAP_CONTROLS_WIDTH, y + MAP_CONTROLS_HEIGHT, TOOLTIP_BG);
        graphics.text(font, "Dungeon Map", x + 8, y + 7, TEXT, true);
        List<SettingEntry> sliders = mapSliders();
        for (int index = 0; index < sliders.size(); index++) {
            SettingEntry slider = sliders.get(index);
            int rowY = y + 21 + index * 22;
            graphics.text(font, slider.label(), x + 8, rowY + 4, MUTED, true);
            slider.draw(graphics, font, UiTheme.settings(), sliderX(), rowY, SLIDER_WIDTH, SLIDER_HEIGHT, false);
        }
    }

    private int mapControlsX() {
        return Math.max(4, width - MAP_CONTROLS_WIDTH - 8);
    }

    private int mapControlsY() {
        return Math.max(36, height - MAP_CONTROLS_HEIGHT - 8);
    }

    private int sliderX() {
        return mapControlsX() + MAP_CONTROLS_WIDTH - SLIDER_WIDTH - 8;
    }

    private boolean inMapControls(int mouseX, int mouseY) {
        return mouseX >= mapControlsX() && mouseX < mapControlsX() + MAP_CONTROLS_WIDTH
            && mouseY >= mapControlsY() && mouseY < mapControlsY() + MAP_CONTROLS_HEIGHT;
    }

    private static HudEntry currentEntryFor(HudEntry target, List<HudEntry> entries) {
        if (target == null) {
            return null;
        }

        for (HudEntry entry : entries) {
            if (entry.featureName().equals(target.featureName())) {
                return entry;
            }
        }
        return null;
    }

    private static HudEntry hoveredEntry(List<HudEntry> entries, int mouseX, int mouseY) {
        for (int index = entries.size() - 1; index >= 0; index--) {
            HudEntry entry = entries.get(index);
            if (contains(entry, mouseX, mouseY)) {
                return entry;
            }
        }
        return null;
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int centerX, int y) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, TEXT, true);
    }

    private static boolean contains(HudEntry entry, int mouseX, int mouseY) {
        return mouseX >= entry.x()
            && mouseX < entry.x() + entry.width()
            && mouseY >= entry.y()
            && mouseY < entry.y() + entry.height();
    }

    private record HudEntry(
        String featureName,
        int x,
        int y,
        int width,
        int height,
        int configOffsetX,
        int configOffsetY,
        IntSupplier configX,
        IntSupplier configY,
        IntConsumer setX,
        IntConsumer setY,
        IntSupplier scale,
        IntConsumer setScale
    ) {
    }
}
