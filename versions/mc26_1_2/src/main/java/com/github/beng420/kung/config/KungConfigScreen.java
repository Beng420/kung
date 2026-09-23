package com.github.beng420.kung.config;

import com.github.beng420.kung.config.KungSettings.CategoryEntry;
import com.github.beng420.kung.config.KungSettings.FeatureEntry;
import com.github.beng420.kung.feature.misc.LoadoutsAutoCloseFeature;
import com.github.beng420.kung.ui.UiBounds;
import com.github.beng420.kung.ui.UiHoverDelay;
import com.github.beng420.kung.ui.UiTextField;
import com.github.beng420.kung.ui.UiTheme;
import com.github.beng420.kung.ui.UiScrollList;
import com.github.beng420.kung.ui.UiSpacing;
import com.github.beng420.kung.ui.UiTextStyle;
import com.github.beng420.kung.ui.UiTooltip;
import com.github.beng420.kung.ui.UiMenuFont;
import com.github.beng420.kung.ui.UiShapes;
import com.github.beng420.kung.update.KungUpdater;
import com.github.beng420.kung.update.KungReleaseNotesPopup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class KungConfigScreen extends Screen {
    private static final UiTheme THEME = UiTheme.menu();
    private static final UiTextStyle TEXT_STYLE = UiTextStyle.normal(THEME);
    private static final UiTextStyle MUTED_STYLE = UiTextStyle.muted(THEME);

    private static final double MENU_SCALE = 0.8;
    private static final int LEFT = UiSpacing.MD;
    private static final int COLUMN_WIDTH = 171;
    private static final int ROW_HEIGHT = 18;
    private static final int SETTING_HEIGHT = 16;
    /** Two lines: label (and a slider's value), then the full-width track or text box. */
    private static final int TWO_LINE_HEIGHT = 26;
    private static final int GAP = 12;
    private static final int TOP = 22;
    private static final int HEADER_HEIGHT = 18;
    private static final int PANEL_RADIUS = 4;
    private static final int SEARCH_BOTTOM = 80;
    private static final int SETTING_CONTROL_WIDTH = 52;
    private static final int SETTING_ARROW_HITBOX_WIDTH = 12;
    private static final int SCROLL_STEP = 42;
    private static final int CATEGORY_SCROLL_STEP = SETTING_HEIGHT * 3;

    private final Screen parent;
    private final Font menuFont;
    private final List<CategoryEntry> categories;
    private final List<ClickRegion> clickRegions = new ArrayList<>();
    private final Map<String, Integer> categoryScrolls = new HashMap<>();
    private final Set<FeatureEntry> expandedFeatures = Collections.newSetFromMap(new IdentityHashMap<>());
    private SettingEntry expandedSetting;
    private TextEditorOverlay textEditor;
    private SettingEntry capturingSetting;
    private SettingEntry draggingSlider;
    private int draggingSliderControlX;
    private int draggingSliderControlWidth;
    private final UiScrollList horizontalScroll = new UiScrollList();
    private String search = "";
    /** Vanilla edit box: blinking cursor, selection, Ctrl+A, arrows, Ctrl+Backspace; right click clears. */
    private final UiTextField searchField;
    private TooltipRequest tooltip;
    private final UiHoverDelay tooltipDelay = new UiHoverDelay();
    private KungReleaseNotesPopup releaseNotesPopup;
    private boolean releaseNotesChecked;
    private boolean forceReleaseNotes;

    public KungConfigScreen() {
        this(null);
    }

    public KungConfigScreen(String expandedFeatureName) {
        this(null, expandedFeatureName);
    }

    private KungConfigScreen(Screen parent, String expandedFeatureName) {
        super(Component.literal("Kung - v" + KungUpdater.currentVersion()));
        this.parent = parent;
        menuFont = UiMenuFont.wrap(super.font);
        searchField = new UiTextField(menuFont, Component.literal("Search")).maxLength(60).bordered(false)
            .textShadow(false).hint("Search here...").responder(value -> search = value);
        categories = KungSettings.categories(this::openChangelog);
        FeatureEntry initialFeature = findFeature(expandedFeatureName);
        if (initialFeature != null) {
            expandedFeatures.add(initialFeature);
        }
        KungUpdater.INSTANCE.checkForUpdatesAsync();
    }

    public static KungConfigScreen fromParent(Screen parent) {
        return new KungConfigScreen(parent, null);
    }

    public static KungConfigScreen fromParent(Screen parent, String expandedFeature) {
        KungConfigScreen screen = new KungConfigScreen(parent, expandedFeature);
        screen.searchField.setValue(expandedFeature);
        return screen;
    }

    public static KungConfigScreen updates() {
        KungConfigScreen screen = new KungConfigScreen();
        screen.searchField.setValue("Updates:");
        FeatureEntry updater = screen.findFeature(KungUpdater.INSTANCE.buttonLabel());
        if (updater != null) screen.expandedFeatures.add(updater);
        return screen;
    }

    public static KungConfigScreen changelog() {
        return changelog(null);
    }

    public static KungConfigScreen changelog(Screen parent) {
        KungConfigScreen screen = new KungConfigScreen(parent, null);
        screen.forceReleaseNotes = true;
        return screen;
    }

    @Override
    protected void init() {
        tooltipDelay.reset();
        if (!releaseNotesChecked) {
            releaseNotesChecked = true;
            var notes = KungUpdater.INSTANCE.releaseNotes();
            if (notes.openMenu(forceReleaseNotes)) releaseNotesPopup = new KungReleaseNotesPopup(notes);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    public boolean hasReleaseNotesPopup() { return releaseNotesPopup != null; }

    private void openChangelog() {
        var notes = KungUpdater.INSTANCE.releaseNotes();
        notes.openMenu(true);
        releaseNotesPopup = new KungReleaseNotesPopup(notes);
        draggingSlider = null;
        capturingSetting = null;
        searchField.setFocused(false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        mouseX = toMenuCoordinate(mouseX);
        mouseY = toMenuCoordinate(mouseY);
        horizontalScroll.setOffsetWithinMax(horizontalScroll.offset(), maxHorizontalScroll());
        clickRegions.clear();
        tooltip = null;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().scale((float) MENU_SCALE, (float) MENU_SCALE);
            graphics.fill(0, 0, menuWidth(), menuHeight(), THEME.backdrop());
            drawTitle(graphics);
            drawColumns(graphics, releaseNotesPopup == null ? mouseX : -1, releaseNotesPopup == null ? mouseY : -1);
            drawSearchBox(graphics, mouseX, mouseY, partialTick);
            boolean showTooltip = tooltip != null && textEditor == null && releaseNotesPopup == null
                && draggingSlider == null && capturingSetting == null;
            if (tooltipDelay.ready(showTooltip ? tooltip.owner() : null, System.nanoTime() / 1_000_000)) {
                UiTooltip.drawBeside(graphics, menuFont, tooltip.lines(), tooltip.x(), tooltip.x() + COLUMN_WIDTH,
                    tooltip.y(), menuWidth(), menuHeight(), THEME);
            }
            if (textEditor != null) {
                textEditor.draw(graphics, mouseX, mouseY, partialTick);
            }
            if (releaseNotesPopup != null) {
                releaseNotesPopup.draw(graphics, menuFont, menuWidth(), menuHeight(), mouseX, mouseY);
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        tooltipDelay.reset();
        int mouseX = toMenuCoordinate(event.x());
        int mouseY = toMenuCoordinate(event.y());
        int button = event.button();

        if (releaseNotesPopup != null) {
            if (releaseNotesPopup.click(this, mouseX, mouseY, button)) releaseNotesPopup = null;
            return true;
        }

        if (textEditor != null) {
            return textEditor.mouseClicked(event, mouseX, mouseY, doubleClick);
        }

        if (capturingSetting != null) {
            capturingSetting.setText(LoadoutsAutoCloseFeature.keybindFromMouseButton(button));
            capturingSetting = null;
            return true;
        }

        if (clickSearchBox(event, mouseX, mouseY, doubleClick)) {
            capturingSetting = null;
            return true;
        }

        searchField.setFocused(false);
        for (int index = clickRegions.size() - 1; index >= 0; index--) {
            ClickRegion region = clickRegions.get(index);
            if (region.bounds().contains(mouseX, mouseY)) {
                return region.action().click(mouseX, mouseY, button);
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (releaseNotesPopup != null) return true;
        if (textEditor != null) {
            return textEditor.mouseDragged(event, dragX, dragY);
        }
        if (searchField.isFocused()) {
            return searchField.mouseDragged(menuEvent(event), dragX / MENU_SCALE, dragY / MENU_SCALE);
        }
        if (draggingSlider == null) {
            return super.mouseDragged(event, dragX, dragY);
        }
        draggingSlider.click(toMenuCoordinate(event.x()), draggingSliderControlX, draggingSliderControlWidth);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (releaseNotesPopup != null) return true;
        if (textEditor != null) {
            return textEditor.mouseReleased(event);
        }
        draggingSlider = null;
        if (searchField.isFocused()) searchField.mouseReleased(menuEvent(event));
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        tooltipDelay.reset();
        if (releaseNotesPopup != null) {
            releaseNotesPopup.scroll(toMenuCoordinate(mouseX), toMenuCoordinate(mouseY), scrollY);
            return true;
        }
        if (scrollY == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        CategoryScrollArea categoryScrollArea = categoryScrollAreaAt(toMenuCoordinate(mouseX), toMenuCoordinate(mouseY));
        if (categoryScrollArea != null) {
            int direction = scrollY < 0 ? 1 : -1;
            setCategoryScroll(
                categoryScrollArea.category(),
                categoryScroll(categoryScrollArea.category()) + direction * CATEGORY_SCROLL_STEP
            );
            return true;
        }

        int maxScroll = maxHorizontalScroll();
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        int direction = scrollY < 0 ? 1 : -1;
        horizontalScroll.scrollByWithinMax(direction * SCROLL_STEP, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (releaseNotesPopup != null) {
            if (releaseNotesPopup.key(event.key())) releaseNotesPopup = null;
            return true;
        }
        if (textEditor != null) {
            return textEditor.keyPressed(event);
        }
        if (capturingSetting != null) {
            if (event.key() == 256) {
                capturingSetting.setText("");
                capturingSetting = null;
                return true;
            }
            if (event.key() == 259 || event.key() == 261) {
                capturingSetting.setText("");
                capturingSetting = null;
                return true;
            }
            capturingSetting.setText(LoadoutsAutoCloseFeature.keybindFromKeyEvent(event));
            capturingSetting = null;
            return true;
        }
        if (searchField.isFocused() && event.key() != 256 && searchField.keyPressed(event)) {
            return true;
        }
        if (event.key() == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (releaseNotesPopup != null) return true;
        if (textEditor != null) {
            return textEditor.charTyped(event);
        }
        if (!searchField.isFocused()) {
            return super.charTyped(event);
        }
        return searchField.charTyped(event);
    }

    private void drawTitle(GuiGraphicsExtractor graphics) {
        String title = getTitle().getString();
        graphics.text(menuFont, title, menuWidth() / 2 - menuFont.width(title) / 2, UiSpacing.MD,
            TEXT_STYLE.color(), TEXT_STYLE.shadow());
    }

    private void drawColumns(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int startX = LEFT - horizontalScroll.offset();
        int visibleIndex = 0;
        for (CategoryEntry category : categories) {
            if (!categoryMatches(category)) {
                continue;
            }
            int x = startX + visibleIndex * (COLUMN_WIDTH + GAP);
            if (x + COLUMN_WIDTH >= 0 && x <= menuWidth()) {
                drawCategoryColumn(graphics, category, x, mouseX, mouseY);
            }
            visibleIndex++;
        }
    }

    private void drawCategoryColumn(
        GuiGraphicsExtractor graphics,
        CategoryEntry category,
        int x,
        int mouseX,
        int mouseY
    ) {
        int panelHeight = panelHeight(category);
        setCategoryScroll(category, categoryScroll(category));
        UiShapes.shadow(graphics, x, TOP, COLUMN_WIDTH, panelHeight, PANEL_RADIUS);
        UiShapes.rounded(graphics, x, TOP, COLUMN_WIDTH, panelHeight, PANEL_RADIUS, THEME.panel());
        var heading = Component.literal(trimToWidth(category.name(), COLUMN_WIDTH - 8)).withStyle(ChatFormatting.BOLD);
        graphics.text(menuFont, heading, x + COLUMN_WIDTH / 2 - menuFont.width(heading) / 2, TOP + 4, THEME.accent(), false);

        int viewportTop = TOP + HEADER_HEIGHT;
        int panelBottom = TOP + panelHeight;
        int rowY = viewportTop - categoryScroll(category);
        for (FeatureEntry feature : category.features()) {
            if (!featureMatches(category, feature)) {
                continue;
            }
            drawFeatureRow(graphics, feature, x, rowY, viewportTop, panelBottom, mouseX, mouseY);
            rowY += ROW_HEIGHT;

            if (isFeatureExpanded(feature)) {
                for (SettingEntry setting : feature.settings()) {
                    if (!setting.visible()) continue;
                    if (rowFullyVisible(rowY, rowHeight(setting), viewportTop, panelBottom)) {
                        drawSettingRow(graphics, setting, x, rowY, panelBottom, mouseX, mouseY, 0);
                    }
                    rowY += rowHeight(setting);
                    if (setting == expandedSetting) {
                        for (SettingEntry child : setting.children()) {
                            if (!child.visible()) continue;
                            if (rowFullyVisible(rowY, rowHeight(child), viewportTop, panelBottom)) {
                                drawSettingRow(graphics, child, x, rowY, panelBottom, mouseX, mouseY, 12);
                            }
                            rowY += rowHeight(child);
                        }
                    }
                }
                if (rowY > viewportTop && rowY < panelBottom) {
                    graphics.fill(x + 5, rowY - 1, x + COLUMN_WIDTH - 5, rowY, THEME.border());
                }
            }
        }
    }

    private void drawFeatureRow(
        GuiGraphicsExtractor graphics,
        FeatureEntry feature,
        int x,
        int rowY,
        int viewportTop,
        int viewportBottom,
        int mouseX,
        int mouseY
    ) {
        if (!rowFullyVisible(rowY, ROW_HEIGHT, viewportTop, viewportBottom)) {
            return;
        }
        boolean hovered = inside(mouseX, mouseY, x, rowY, COLUMN_WIDTH, ROW_HEIGHT);
        boolean clickable = feature.clickable() || !feature.settings().isEmpty();
        int color = feature.enabled() ? THEME.accent() : hovered && clickable ? THEME.panelSoft() : THEME.panelDark();
        UiShapes.rounded(graphics, x, rowY, COLUMN_WIDTH, ROW_HEIGHT, 0,
            rowY + ROW_HEIGHT == viewportBottom ? PANEL_RADIUS : 0, color);
        drawCentered(graphics, trimToWidth(feature.name(), COLUMN_WIDTH - 8), x + COLUMN_WIDTH / 2, rowY + 5, THEME.text(), true);
        if (hovered && (!feature.tooltip().isEmpty() || menuFont.width(feature.name()) > COLUMN_WIDTH - 8)) {
            requestTooltip(feature, feature.name(), feature.tooltip(), x, rowY);
        }
        addClickRegion(x, rowY, COLUMN_WIDTH, ROW_HEIGHT, (clickX, clickY, button) -> clickFeature(feature, button));
        if (feature.developerOnly()) drawDeveloperStar(graphics, x + COLUMN_WIDTH, rowY);
    }

    /** A tiny yellow star over the top-right corner of developer-only rows: drawn on top, takes no room. */
    private void drawDeveloperStar(GuiGraphicsExtractor graphics, int rowRight, int rowY) {
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(rowRight - 6, rowY + 1);
            graphics.pose().scale(0.5F, 0.5F);
            graphics.text(menuFont, "\u2605", 0, 0, 0xFFFFD84A, false);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private boolean isFeatureExpanded(FeatureEntry feature) {
        return feature.alwaysExpanded() || expandedFeatures.contains(feature);
    }

    private boolean rowFullyVisible(int rowY, int rowHeight, int viewportTop, int viewportBottom) {
        return rowY >= viewportTop && rowY + rowHeight <= viewportBottom;
    }

    private void drawSettingRow(
        GuiGraphicsExtractor graphics,
        SettingEntry setting,
        int x,
        int rowY,
        int panelBottom,
        int mouseX,
        int mouseY,
        int indent
    ) {
        int height = rowHeight(setting);
        boolean hovered = inside(mouseX, mouseY, x, rowY, COLUMN_WIDTH, height);
        UiShapes.rounded(graphics, x, rowY, COLUMN_WIDTH, height, 0,
            rowY + height == panelBottom ? PANEL_RADIUS : 0, hovered ? THEME.panelSoft() : THEME.control());
        if (setting.expandable()) {
            graphics.text(menuFont, setting == expandedSetting ? "v" : ">", x + 5 + indent, rowY + 4, THEME.text(), true);
        }
        int labelX = x + 5 + indent + (setting.expandable() ? 11 : 0);
        if (setting.kind() == SettingKind.LABEL) {
            graphics.text(menuFont, trimToWidth(setting.label(), COLUMN_WIDTH - indent - 10), labelX, rowY + 4,
                MUTED_STYLE.color(), MUTED_STYLE.shadow());
            return;
        }
        // Odin-style two-line rows: sliders and text fields put the label (and a slider's value) on the
        // first line and the control across the whole row below, so neither cuts the label short.
        boolean twoLine = height > SETTING_HEIGHT;
        String value = setting.kind() == SettingKind.SLIDER ? setting.sliderValue() : "";
        int controlWidth = twoLine ? COLUMN_WIDTH - 10 - indent : controlWidthFor(setting);
        int labelWidth = twoLine ? COLUMN_WIDTH - indent - 16 - menuFont.width(value) : COLUMN_WIDTH - controlWidth - indent - 20;
        graphics.text(menuFont, trimToWidth(setting.label(), labelWidth), labelX, rowY + 4, THEME.muted(), true);
        if (!value.isEmpty()) graphics.text(menuFont, value, x + COLUMN_WIDTH - 5 - menuFont.width(value), rowY + 4, THEME.text(), true);
        if (hovered && (!setting.tooltip().isEmpty() || menuFont.width(setting.label()) > labelWidth)) {
            requestTooltip(setting, setting.label(), setting.tooltip(), x, rowY);
        }
        int controlX = twoLine ? x + 5 + indent : x + COLUMN_WIDTH - controlWidth - 5;
        int controlY = rowY + height - SETTING_HEIGHT;
        setting.draw(graphics, menuFont, THEME, controlX, controlY, controlWidth, SETTING_HEIGHT,
            setting == capturingSetting);
        if (setting.developerOnly()) drawDeveloperStar(graphics, x + COLUMN_WIDTH, rowY);
        addClickRegion(x, rowY, COLUMN_WIDTH, height, (clickX, clickY, button) ->
            clickSetting(setting, clickX, button, controlX, controlWidth)
        );
        if (setting.expandable()) {
            addClickRegion(x + 2 + indent, rowY, SETTING_ARROW_HITBOX_WIDTH, height, (clickX, clickY, button) -> {
                if (button == 0) {
                    toggleSettingExpansion(setting);
                    return true;
                }
                return clickSetting(setting, clickX, button, controlX, controlWidth);
            });
        }
    }

    /** Anchored to the row, not the cursor; the help alone, or the full label when it was cut short. */
    private void requestTooltip(Object owner, String title, List<String> help, int rowX, int rowY) {
        tooltip = new TooltipRequest(owner, help.isEmpty() ? List.of(title) : List.copyOf(help), rowX, rowY);
    }

    private static int rowHeight(SettingEntry setting) {
        return setting.kind() == SettingKind.SLIDER || setting.kind() == SettingKind.TEXT ? TWO_LINE_HEIGHT : SETTING_HEIGHT;
    }

    /** Controls take the room they need, so the label keeps the rest of the row. */
    private int controlWidthFor(SettingEntry setting) {
        return switch (setting.kind()) {
            case GROUP -> 16;
            case TOGGLE -> 24;
            case CHOICE -> Math.clamp(Math.max(menuFont.width(setting.choiceSupplier().get()),
                setting.choices().stream().mapToInt(menuFont::width).max().orElse(0)) + 10, 24, 96);
            case BUTTON -> Math.clamp(menuFont.width(setting.choiceSupplier().get()) + 10, 16, 64);
            case KEYBIND -> Math.clamp(Math.max(menuFont.width(setting.textValue()), menuFont.width("Press...")) + 10, 24, 72);
            default -> SETTING_CONTROL_WIDTH;
        };
    }

    private String trimToWidth(String value, int maxWidth) {
        if (menuFont.width(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        for (int end = value.length(); end > 0; end--) {
            String candidate = value.substring(0, end) + suffix;
            if (menuFont.width(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return suffix;
    }

    private void drawSearchBox(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int boxWidth = Math.min(220, Math.max(120, menuWidth() - 24));
        int boxHeight = 20;
        int x = menuWidth() / 2 - boxWidth / 2;
        int y = menuHeight() - SEARCH_BOTTOM;
        UiShapes.shadow(graphics, x, y, boxWidth, boxHeight, 5);
        UiShapes.rounded(graphics, x - 1, y - 1, boxWidth + 2, boxHeight + 2, 6,
            searchField.isFocused() ? THEME.text() : THEME.accent());
        UiShapes.rounded(graphics, x, y, boxWidth, boxHeight, 5, THEME.control());
        searchField.setBounds(x + 6, y + 6, boxWidth - 12, boxHeight - 8);
        searchField.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private boolean clickSearchBox(MouseButtonEvent event, int mouseX, int mouseY, boolean doubleClick) {
        int boxWidth = Math.min(220, Math.max(120, menuWidth() - 24));
        int boxHeight = 20;
        int x = menuWidth() / 2 - boxWidth / 2;
        int y = menuHeight() - SEARCH_BOTTOM;
        if (!inside(mouseX, mouseY, x - 2, y - 2, boxWidth + 4, boxHeight + 4)) return false;
        // Clicks on the frame land in the field too, so the whole box places the cursor or clears it.
        int fieldX = Math.clamp(mouseX, searchField.x(), searchField.x() + searchField.width() - 1);
        int fieldY = searchField.bounds().y() + 1;
        searchField.mouseClicked(new MouseButtonEvent(fieldX, fieldY, event.buttonInfo()), doubleClick);
        searchField.setFocused(true);
        return true;
    }

    private MouseButtonEvent menuEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(toMenuCoordinate(event.x()), toMenuCoordinate(event.y()), event.buttonInfo());
    }

    private boolean clickFeature(FeatureEntry feature, int button) {
        if (button == 1) {
            if (feature.alwaysExpanded()) return true;
            if (!expandedFeatures.remove(feature)) {
                expandedFeatures.add(feature);
            }
            return true;
        }
        if (button == 0 && feature.clickable() && feature.toggle() != null) {
            feature.toggle().run();
            return true;
        }
        return true;
    }

    private boolean clickSetting(SettingEntry setting, int mouseX, int button, int controlX, int controlWidth) {
        if (button == 1 && setting.expandable()) {
            toggleSettingExpansion(setting);
            return true;
        }
        if (button == 0) {
            if (setting.kind() == SettingKind.GROUP && setting.expandable()) {
                toggleSettingExpansion(setting);
                return true;
            }
            if (setting.kind() == SettingKind.TEXT) {
                openTextEditor(setting);
                capturingSetting = null;
                return true;
            }
            if (setting.kind() == SettingKind.KEYBIND) {
                capturingSetting = setting;
                return true;
            }
            capturingSetting = null;
            setting.click(mouseX, controlX, controlWidth);
            if (setting.kind() == SettingKind.SLIDER) {
                draggingSlider = setting;
                draggingSliderControlX = controlX;
                draggingSliderControlWidth = controlWidth;
            }
            return true;
        }
        return true;
    }

    private void toggleSettingExpansion(SettingEntry setting) {
        expandedSetting = expandedSetting == setting ? null : setting;
    }

    private void openTextEditor(SettingEntry setting) {
        if (setting == null || setting.kind() != SettingKind.TEXT) {
            return;
        }
        textEditor = new TextEditorOverlay(setting);
        searchField.setFocused(false);
    }

    private void addClickRegion(int x, int y, int width, int height, ClickAction action) {
        clickRegions.add(new ClickRegion(new UiBounds(x, y, width, height), action));
    }

    private int contentWidth() {
        int columnCount = 0;
        for (CategoryEntry category : categories) {
            if (categoryMatches(category)) {
                columnCount++;
            }
        }
        return columnCount == 0 ? 0 : columnCount * COLUMN_WIDTH + (columnCount - 1) * GAP;
    }

    private int maxHorizontalScroll() {
        return Math.max(0, contentWidth() - Math.max(0, menuWidth() - LEFT * 2));
    }

    private int panelHeight(CategoryEntry category) {
        return Math.min(Math.max(HEADER_HEIGHT, menuHeight() - SEARCH_BOTTOM - TOP - 12),
            HEADER_HEIGHT + categoryContentHeight(category));
    }

    private void setCategoryScroll(CategoryEntry category, int scroll) {
        int clamped = Math.clamp(scroll, 0, maxCategoryScroll(category, panelHeight(category)));
        if (clamped <= 0) {
            categoryScrolls.remove(categoryScrollKey(category));
        } else {
            categoryScrolls.put(categoryScrollKey(category), clamped);
        }
    }

    private int categoryScroll(CategoryEntry category) {
        return categoryScrolls.getOrDefault(categoryScrollKey(category), 0);
    }

    private String categoryScrollKey(CategoryEntry category) {
        return category.name().toLowerCase(Locale.ROOT);
    }

    private int maxCategoryScroll(CategoryEntry category, int panelHeight) {
        int viewportHeight = Math.max(0, panelHeight - HEADER_HEIGHT);
        return Math.max(0, categoryContentHeight(category) - viewportHeight);
    }

    private CategoryScrollArea categoryScrollAreaAt(int mouseX, int mouseY) {
        int startX = LEFT - horizontalScroll.offset();
        int visibleIndex = 0;
        for (CategoryEntry category : categories) {
            if (!categoryMatches(category)) {
                continue;
            }
            int x = startX + visibleIndex * (COLUMN_WIDTH + GAP);
            int panelHeight = panelHeight(category);
            if (x + COLUMN_WIDTH >= 0
                && x <= menuWidth()
                && inside(mouseX, mouseY, x, TOP, COLUMN_WIDTH, panelHeight)) {
                return new CategoryScrollArea(category, maxCategoryScroll(category, panelHeight));
            }
            visibleIndex++;
        }
        return null;
    }

    private int categoryContentHeight(CategoryEntry category) {
        int total = 0;
        for (FeatureEntry feature : category.features()) {
            if (!featureMatches(category, feature)) {
                continue;
            }
            total += ROW_HEIGHT;
            if (isFeatureExpanded(feature)) {
                for (SettingEntry setting : feature.settings()) {
                    if (!setting.visible()) continue;
                    total += rowHeight(setting);
                    if (setting == expandedSetting) {
                        for (SettingEntry child : setting.children()) if (child.visible()) total += rowHeight(child);
                    }
                }
            }
        }
        return total;
    }

    private boolean categoryMatches(CategoryEntry category) {
        if (search.isBlank()) {
            return true;
        }
        if (containsIgnoreCase(category.name(), search)) {
            return true;
        }
        for (FeatureEntry feature : category.features()) {
            if (featureMatches(category, feature)) {
                return true;
            }
        }
        return false;
    }

    private boolean featureMatches(CategoryEntry category, FeatureEntry feature) {
        if (search.isBlank() || containsIgnoreCase(category.name(), search) || containsIgnoreCase(feature.name(), search)) {
            return true;
        }
        for (SettingEntry setting : feature.settings()) {
            if (settingMatches(setting)) {
                return true;
            }
        }
        return false;
    }

    private boolean settingMatches(SettingEntry setting) {
        if (containsIgnoreCase(setting.label(), search)) {
            return true;
        }
        for (SettingEntry child : setting.children()) {
            if (settingMatches(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(String value, String search) {
        return value.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    private FeatureEntry findFeature(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        for (CategoryEntry category : categories) {
            for (FeatureEntry feature : category.features()) {
                if (feature.name().equalsIgnoreCase(name)) {
                    return feature;
                }
            }
        }
        return null;
    }

    // Layout and input share menu coordinates; Screen's dimensions remain in Minecraft GUI units.
    private int menuWidth() { return (int) Math.ceil(width / MENU_SCALE); }

    private int menuHeight() { return (int) Math.ceil(height / MENU_SCALE); }

    private static int toMenuCoordinate(double value) {
        return (int) Math.floor(value / MENU_SCALE);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int centerX, int y, int color, boolean shadow) {
        graphics.text(menuFont, text, centerX - menuFont.width(text) / 2, y, color, shadow);
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private final class TextEditorOverlay {
        private static final int MODAL_BACKDROP = 0x99000000;
        private static final int MODAL_BORDER = 0xFF3498DB;
        private static final int BUTTON_WIDTH = 58;
        private static final int BUTTON_HEIGHT = 18;

        private final SettingEntry setting;
        private final UiTextField editBox;
        private int panelX;
        private int panelY;
        private int panelWidth;
        private int panelHeight;
        private int saveX;
        private int cancelX;
        private int buttonsY;

        private TextEditorOverlay(SettingEntry setting) {
            this.setting = setting;
            this.editBox = new UiTextField(menuFont, Component.literal(setting.label()))
                .maxLength(240)
                .canLoseFocus(false)
                .textShadow(true);
            this.editBox.setFocused(true);
            layout();
            this.editBox.setValue(setting.textValue());
            this.editBox.moveCursorToEnd();
        }

        private void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            layout();
            graphics.nextStratum();
            graphics.fill(0, 0, menuWidth(), menuHeight(), MODAL_BACKDROP);
            UiShapes.shadow(graphics, panelX, panelY, panelWidth, panelHeight, 5);
            UiShapes.rounded(graphics, panelX - 1, panelY - 1, panelWidth + 2, panelHeight + 2, 6, MODAL_BORDER);
            UiShapes.rounded(graphics, panelX, panelY, panelWidth, panelHeight, 5, THEME.panel());
            drawCentered(graphics, trimToWidth(setting.label(), panelWidth - 18), menuWidth() / 2, panelY + 10, THEME.text(), true);
            editBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
            drawButton(graphics, saveX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT, "Save", mouseX, mouseY);
            drawButton(graphics, cancelX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT, "Cancel", mouseX, mouseY);
        }

        private void drawButton(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int buttonWidth,
            int buttonHeight,
            String label,
            int mouseX,
            int mouseY
        ) {
            boolean hovered = inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
            UiShapes.rounded(graphics, x, y, buttonWidth, buttonHeight, 3, hovered ? THEME.accent() : THEME.accentDark());
            drawCentered(graphics, label, x + buttonWidth / 2, y + 5, THEME.text(), true);
        }

        private boolean mouseClicked(MouseButtonEvent event, int mouseX, int mouseY, boolean doubleClick) {
            layout();
            if (event.button() == 0 && inside(mouseX, mouseY, saveX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                save();
                return true;
            }
            if (event.button() == 0 && inside(mouseX, mouseY, cancelX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                close();
                return true;
            }
            MouseButtonEvent guiEvent = new MouseButtonEvent(mouseX, mouseY, event.buttonInfo());
            editBox.mouseClicked(guiEvent, doubleClick);
            editBox.setFocused(true);
            return true;
        }

        private boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            MouseButtonEvent guiEvent = new MouseButtonEvent(toMenuCoordinate(event.x()), toMenuCoordinate(event.y()), event.buttonInfo());
            return editBox.mouseDragged(guiEvent, dragX / MENU_SCALE, dragY / MENU_SCALE);
        }

        private boolean mouseReleased(MouseButtonEvent event) {
            MouseButtonEvent guiEvent = new MouseButtonEvent(toMenuCoordinate(event.x()), toMenuCoordinate(event.y()), event.buttonInfo());
            return editBox.mouseReleased(guiEvent);
        }

        private boolean keyPressed(KeyEvent event) {
            if (event.key() == 256) {
                close();
                return true;
            }
            if (event.key() == 257 || event.key() == 335) {
                save();
                return true;
            }
            return editBox.keyPressed(event);
        }

        private boolean charTyped(CharacterEvent event) {
            return editBox.charTyped(event);
        }

        private void save() {
            setting.setText(editBox.value());
            textEditor = null;
        }

        private void close() {
            textEditor = null;
        }

        private void layout() {
            panelWidth = Math.min(Math.max(260, menuWidth() - 80), 420);
            panelHeight = 86;
            panelX = menuWidth() / 2 - panelWidth / 2;
            panelY = menuHeight() / 2 - panelHeight / 2;
            editBox.setBounds(panelX + 14, panelY + 31, panelWidth - 28, 20);
            buttonsY = panelY + panelHeight - BUTTON_HEIGHT - 10;
            saveX = panelX + panelWidth - BUTTON_WIDTH * 2 - 22;
            cancelX = panelX + panelWidth - BUTTON_WIDTH - 14;
        }
    }

    @FunctionalInterface
    private interface ClickAction {
        boolean click(int mouseX, int mouseY, int button);
    }

    private record ClickRegion(UiBounds bounds, ClickAction action) {
    }

    private record TooltipRequest(Object owner, List<String> lines, int x, int y) {
    }

    private record CategoryScrollArea(CategoryEntry category, int maxScroll) {
    }

}
