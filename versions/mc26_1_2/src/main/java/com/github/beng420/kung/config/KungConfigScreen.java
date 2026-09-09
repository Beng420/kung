package com.github.beng420.kung.config;

import static com.github.beng420.kung.util.GuiDraw.fill;

import com.github.beng420.kung.feature.dungeon.DungeonKnownRoomCatalog;
import com.github.beng420.kung.feature.dungeon.DungeonRoomDataSyncClient;
import com.github.beng420.kung.feature.dungeon.DungeonRoomClassifier;
import com.github.beng420.kung.feature.misc.CustomSoundsFeature;
import com.github.beng420.kung.feature.misc.LoadoutsAutoCloseFeature;
import com.github.beng420.kung.ui.UiBounds;
import com.github.beng420.kung.ui.UiTextField;
import com.github.beng420.kung.ui.UiTheme;
import com.github.beng420.kung.ui.UiScrollList;
import com.github.beng420.kung.ui.UiSpacing;
import com.github.beng420.kung.ui.UiTextStyle;
import com.github.beng420.kung.ui.UiTooltip;
import com.github.beng420.kung.update.KungUpdater;
import com.github.beng420.kung.util.HypixelSkyBlockProfileClient;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class KungConfigScreen extends Screen {
    private static final UiTheme THEME = UiTheme.settings();
    private static final UiTextStyle TEXT_STYLE = UiTextStyle.normal(THEME);
    private static final UiTextStyle MUTED_STYLE = UiTextStyle.muted(THEME);

    private static final int LEFT = UiSpacing.MD;
    private static final int COLUMN_WIDTH = 190;
    private static final int ROW_HEIGHT = 18;
    private static final int SETTING_HEIGHT = 16;
    private static final int GAP = UiSpacing.MD;
    private static final int TOP = 22;
    private static final int HEADER_HEIGHT = 18;
    private static final int SETTING_CONTROL_WIDTH = 58;
    private static final int TEXT_SETTING_CONTROL_WIDTH = 112;
    private static final int SETTING_ARROW_HITBOX_WIDTH = 12;
    private static final int SCROLL_STEP = 42;
    private static final int CATEGORY_SCROLL_STEP = SETTING_HEIGHT * 3;

    private final List<CategoryEntry> categories;
    private final List<ClickRegion> clickRegions = new ArrayList<>();
    private final Map<String, Integer> categoryScrolls = new HashMap<>();
    private final Set<FeatureEntry> expandedFeatures = Collections.newSetFromMap(new IdentityHashMap<>());
    private SettingEntry expandedSetting;
    private TextEditorOverlay textEditor;
    private SettingEntry capturingSetting;
    private SettingEntry draggingSlider;
    private int draggingSliderControlX;
    private final UiScrollList horizontalScroll = new UiScrollList();
    private String search = "";
    private boolean searchFocused;
    private TooltipRequest tooltip;

    public KungConfigScreen() {
        this(null);
    }

    public KungConfigScreen(String expandedFeatureName) {
        super(Component.literal("Kung Settings"));
        categories = createCategories();
        FeatureEntry initialFeature = findFeature(expandedFeatureName);
        if (initialFeature != null) {
            expandedFeatures.add(initialFeature);
        }
        KungUpdater.INSTANCE.checkForUpdatesAsync();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        horizontalScroll.setOffsetWithinMax(horizontalScroll.offset(), maxHorizontalScroll());
        clickRegions.clear();
        tooltip = null;
        fill(graphics, 0, 0, width, height, THEME.backdrop());
        drawTitle(graphics);
        drawColumns(graphics, mouseX, mouseY);
        drawSearchBox(graphics);
        if (tooltip != null && textEditor == null) {
            UiTooltip.draw(graphics, font, List.of(tooltip.text()), tooltip.x(), tooltip.y(), THEME);
        }
        if (textEditor != null) {
            textEditor.draw(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = toGuiX(event.x());
        int mouseY = toGuiY(event.y());
        int button = event.button();

        if (textEditor != null) {
            return textEditor.mouseClicked(event, mouseX, mouseY, doubleClick);
        }

        if (capturingSetting != null) {
            capturingSetting.setText(LoadoutsAutoCloseFeature.keybindFromMouseButton(button));
            capturingSetting = null;
            return true;
        }

        if (clickSearchBox(mouseX, mouseY, button)) {
            capturingSetting = null;
            return true;
        }

        searchFocused = false;
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
        if (textEditor != null) {
            return textEditor.mouseDragged(event, dragX, dragY);
        }
        if (draggingSlider == null) {
            return super.mouseDragged(event, dragX, dragY);
        }
        draggingSlider.click(toGuiX(event.x()), draggingSliderControlX, SETTING_CONTROL_WIDTH);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (textEditor != null) {
            return textEditor.mouseReleased(event);
        }
        draggingSlider = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        CategoryScrollArea categoryScrollArea = categoryScrollAreaAt(toGuiX(mouseX), toGuiY(mouseY));
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
        if (searchFocused) {
            if (event.key() == 259 && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                return true;
            }
            if (event.key() == 261 && !search.isEmpty()) {
                search = "";
                return true;
            }
        }
        if (event.key() == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (textEditor != null) {
            return textEditor.charTyped(event);
        }
        if (!searchFocused || !event.isAllowedChatCharacter()) {
            return super.charTyped(event);
        }
        search += event.codepointAsString();
        return true;
    }

    private void drawTitle(GuiGraphicsExtractor graphics) {
        String title = "Kung";
        graphics.text(font, title, width / 2 - font.width(title) / 2, UiSpacing.MD,
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
            if (x + COLUMN_WIDTH >= 0 && x <= width) {
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
        fill(graphics, x - 1, TOP - 1, x + COLUMN_WIDTH + 1, TOP + panelHeight + 1, THEME.border());
        fill(graphics, x, TOP, x + COLUMN_WIDTH, TOP + panelHeight, THEME.panel());

        drawCentered(graphics, trimToWidth(category.name(), COLUMN_WIDTH - 8), x + COLUMN_WIDTH / 2, TOP + 5, THEME.text(), true);

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
                    if (rowFullyVisible(rowY, SETTING_HEIGHT, viewportTop, panelBottom)) {
                        drawSettingRow(graphics, setting, x, rowY, mouseX, mouseY, 0);
                    }
                    rowY += SETTING_HEIGHT;
                    if (setting == expandedSetting) {
                        for (SettingEntry child : setting.children()) {
                            if (rowFullyVisible(rowY, SETTING_HEIGHT, viewportTop, panelBottom)) {
                                drawSettingRow(graphics, child, x, rowY, mouseX, mouseY, 12);
                            }
                            rowY += SETTING_HEIGHT;
                        }
                    }
                }
                if (rowY > viewportTop && rowY <= panelBottom) {
                    fill(graphics, x, rowY - 1, x + COLUMN_WIDTH, rowY, THEME.accentDark());
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
        fill(graphics, x, rowY, x + COLUMN_WIDTH, rowY + ROW_HEIGHT, color);
        drawCentered(graphics, trimToWidth(feature.name(), COLUMN_WIDTH - 8), x + COLUMN_WIDTH / 2, rowY + 5, THEME.text(), true);
        addClickRegion(x, rowY, COLUMN_WIDTH, ROW_HEIGHT, (clickX, clickY, button) -> clickFeature(feature, button));
    }

    private boolean isFeatureExpanded(FeatureEntry feature) {
        return expandedFeatures.contains(feature);
    }

    private boolean rowFullyVisible(int rowY, int rowHeight, int viewportTop, int viewportBottom) {
        return rowY >= viewportTop && rowY + rowHeight <= viewportBottom;
    }

    private void drawSettingRow(
        GuiGraphicsExtractor graphics,
        SettingEntry setting,
        int x,
        int rowY,
        int mouseX,
        int mouseY,
        int indent
    ) {
        boolean hovered = inside(mouseX, mouseY, x, rowY, COLUMN_WIDTH, SETTING_HEIGHT);
        fill(graphics, x, rowY, x + COLUMN_WIDTH, rowY + SETTING_HEIGHT, hovered ? THEME.panelSoft() : THEME.control());
        if (setting.expandable()) {
            graphics.text(font, setting == expandedSetting ? "v" : ">", x + 5 + indent, rowY + 4, THEME.text(), true);
        }
        int labelX = x + 5 + indent + (setting.expandable() ? 11 : 0);
        if (setting.kind() == SettingKind.LABEL) {
            graphics.text(font, trimToWidth(setting.label(), COLUMN_WIDTH - indent - 10), labelX, rowY + 4,
                MUTED_STYLE.color(), MUTED_STYLE.shadow());
            return;
        }
        int controlWidth = controlWidthFor(setting);
        int labelWidth = COLUMN_WIDTH - controlWidth - indent - 20;
        graphics.text(font, trimToWidth(setting.label(), labelWidth), labelX, rowY + 4, THEME.text(), true);
        if (hovered && font.width(setting.label()) > labelWidth) {
            tooltip = new TooltipRequest(setting.label(), mouseX + UiSpacing.MD, mouseY + UiSpacing.MD);
        }
        int controlX = x + COLUMN_WIDTH - controlWidth - 5;
        setting.draw(graphics, font, THEME, controlX, rowY, controlWidth, SETTING_HEIGHT, setting == capturingSetting);
        addClickRegion(x, rowY, COLUMN_WIDTH, SETTING_HEIGHT, (clickX, clickY, button) ->
            clickSetting(setting, clickX, button, controlX, controlWidth)
        );
        if (setting.expandable()) {
            addClickRegion(x + 2 + indent, rowY, SETTING_ARROW_HITBOX_WIDTH, SETTING_HEIGHT, (clickX, clickY, button) -> {
                if (button == 0) {
                    toggleSettingExpansion(setting);
                    return true;
                }
                return clickSetting(setting, clickX, button, controlX, controlWidth);
            });
        }
    }

    private int controlWidthFor(SettingEntry setting) {
        if (setting.kind() == SettingKind.GROUP) {
            return 16;
        }
        if (setting.kind() == SettingKind.TEXT || setting.kind() == SettingKind.KEYBIND) {
            return TEXT_SETTING_CONTROL_WIDTH;
        }
        return SETTING_CONTROL_WIDTH;
    }

    private String trimToWidth(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        for (int end = value.length(); end > 0; end--) {
            String candidate = value.substring(0, end) + suffix;
            if (font.width(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return suffix;
    }

    private void drawSearchBox(GuiGraphicsExtractor graphics) {
        int boxWidth = Math.min(260, Math.max(120, width - 24));
        int boxHeight = 20;
        int x = width / 2 - boxWidth / 2;
        int y = height - 30;
        fill(graphics, x - 2, y - 2, x + boxWidth + 2, y + boxHeight + 2, searchFocused ? THEME.text() : THEME.accent());
        fill(graphics, x, y, x + boxWidth, y + boxHeight, THEME.panel());
        String label = search.isEmpty() ? "Search here..." : search;
        drawCentered(graphics, label, x + boxWidth / 2, y + 6, search.isEmpty() ? THEME.muted() : THEME.text(), true);
    }

    private boolean clickSearchBox(int mouseX, int mouseY, int button) {
        int boxWidth = Math.min(260, Math.max(120, width - 24));
        int boxHeight = 20;
        int x = width / 2 - boxWidth / 2;
        int y = height - 30;
        if (button == 0 && inside(mouseX, mouseY, x - 2, y - 2, boxWidth + 4, boxHeight + 4)) {
            searchFocused = true;
            return true;
        }
        return false;
    }

    private boolean clickFeature(FeatureEntry feature, int button) {
        if (button == 1) {
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
        searchFocused = false;
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
        return Math.max(0, contentWidth() - Math.max(0, width - LEFT * 2));
    }

    private int panelHeight(CategoryEntry category) {
        return Math.min(height - 52, HEADER_HEIGHT + categoryContentHeight(category));
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
                && x <= width
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
                total += feature.settings().size() * SETTING_HEIGHT;
                for (SettingEntry setting : feature.settings()) {
                    if (setting == expandedSetting) {
                        total += setting.children().size() * SETTING_HEIGHT;
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

    private int toGuiX(double eventX) {
        return toGuiCoordinate(eventX, width, true);
    }

    private int toGuiY(double eventY) {
        return toGuiCoordinate(eventY, height, false);
    }

    private static int toGuiCoordinate(double value, int currentGuiSize, boolean horizontal) {
        int rounded = (int) Math.round(value);
        if (rounded >= 0 && rounded <= currentGuiSize) {
            return rounded;
        }

        Window window = Minecraft.getInstance().getWindow();
        int physicalSize = horizontal ? window.getWidth() : window.getHeight();
        int guiSize = horizontal ? window.getGuiScaledWidth() : window.getGuiScaledHeight();
        if (physicalSize <= 0 || guiSize <= 0) {
            return rounded;
        }
        return (int) Math.round(value * guiSize / physicalSize);
    }

    private List<CategoryEntry> createCategories() {
        KungConfig config = KungConfig.get();
        List<CategoryEntry> result = new ArrayList<>();
        result.add(new CategoryEntry("Dungeon", List.of(
            new FeatureEntry(
                "Dungeon Map",
                config.dungeon::enabled,
                () -> config.dungeon.setEnabled(!config.dungeon.enabled()),
                List.of(
                    SettingEntry.stepper(
                        "Unopened Alpha",
                        config.dungeon::unopenedRoomAlpha,
                        config.dungeon::setUnopenedRoomAlpha,
                        0,
                        28,
                        2
                    ),
                    SettingEntry.toggle("Legend", config.dungeon::showLegend, () -> config.dungeon.setShowLegend(!config.dungeon.showLegend())),
                    SettingEntry.toggle("Boss Map", config.dungeon::showInBoss, () -> config.dungeon.setShowInBoss(!config.dungeon.showInBoss())),
                    SettingEntry.toggle(
                        "Prince Icons",
                        config.dungeon::princeIconsEnabled,
                        () -> config.dungeon.setPrinceIconsEnabled(!config.dungeon.princeIconsEnabled())
                    ),
                    SettingEntry.toggle(
                        "Force Paul",
                        config.dungeon::forcePaulScoreEnabled,
                        () -> config.dungeon.setForcePaulScoreEnabled(!config.dungeon.forcePaulScoreEnabled())
                    ),
                    SettingEntry.toggle(
                        "Room Debug",
                        config.dungeon::debugRoomMatches,
                        () -> config.dungeon.setDebugRoomMatches(!config.dungeon.debugRoomMatches())
                    ),
                    SettingEntry.toggle(
                        "Local Data",
                        config.dungeon::localRoomDataEnabled,
                        () -> toggleLocalRoomData(config)
                    )
                )
            ),
            new FeatureEntry(
                "Player Stats",
                config.dungeon::playerTrackingEnabled,
                () -> config.dungeon.setPlayerTrackingEnabled(!config.dungeon.playerTrackingEnabled()),
                List.of()
            ),
            new FeatureEntry(
                "Death Messages",
                config.dungeon::deathMessagesEnabled,
                () -> config.dungeon.setDeathMessagesEnabled(!config.dungeon.deathMessagesEnabled()),
                List.of(
                    SettingEntry.group("Announce To Party").withChildren(List.of(
                        SettingEntry.toggle(
                            "Share Total",
                            config.dungeon::deathMessagesShareTotalEnabled,
                            () -> config.dungeon.setDeathMessagesShareTotalEnabled(!config.dungeon.deathMessagesShareTotalEnabled())
                        ),
                        SettingEntry.toggle(
                            "Share Individual",
                            config.dungeon::deathMessagesShareIndividualEnabled,
                            () -> config.dungeon.setDeathMessagesShareIndividualEnabled(!config.dungeon.deathMessagesShareIndividualEnabled())
                        )
                    ))
                )
            ),
            new FeatureEntry(
                "Crypts",
                () -> config.dungeon.debugRoomCrypts()
                    || config.dungeon.cryptProgressPartyMessageEnabled()
                    || config.dungeon.fiveCryptTitleEnabled()
                    || config.dungeon.fiveCryptPartyMessageEnabled(),
                null,
                List.of(
                    SettingEntry.toggle(
                        "Room Crypts",
                        config.dungeon::debugRoomCrypts,
                        () -> config.dungeon.setDebugRoomCrypts(!config.dungeon.debugRoomCrypts())
                    ),
                    SettingEntry.toggle(
                        "Announce Progress",
                        config.dungeon::cryptProgressPartyMessageEnabled,
                        () -> config.dungeon.setCryptProgressPartyMessageEnabled(!config.dungeon.cryptProgressPartyMessageEnabled())
                    ),
                    SettingEntry.toggle(
                        "5 Crypt Title",
                        config.dungeon::fiveCryptTitleEnabled,
                        () -> config.dungeon.setFiveCryptTitleEnabled(!config.dungeon.fiveCryptTitleEnabled())
                    ),
                    SettingEntry.toggle(
                        "5 Crypts Msg",
                        config.dungeon::fiveCryptPartyMessageEnabled,
                        () -> config.dungeon.setFiveCryptPartyMessageEnabled(!config.dungeon.fiveCryptPartyMessageEnabled())
                    ),
                    SettingEntry.text(
                        "Crypt Msg",
                        config.dungeon::fiveCryptPartyMessage,
                        config.dungeon::setFiveCryptPartyMessage
                    )
                )
            ),
            new FeatureEntry(
                "Blood rush helper",
                config.bloodRush::enabled,
                () -> config.bloodRush.setEnabled(!config.bloodRush.enabled()),
                List.of(
                    SettingEntry.slider(
                        "Title Time",
                        config.bloodRush::titleDurationTenths,
                        config.bloodRush::setTitleDurationTenths,
                        1,
                        50,
                        1
                    )
                )
            ),
            new FeatureEntry(
                "Dungeon Chat Filter",
                config.chatFilter::enabled,
                () -> config.chatFilter.setEnabled(!config.chatFilter.enabled()),
                List.of(
                    SettingEntry.toggle(
                        "Blessings",
                        config.chatFilter::blessings,
                        () -> config.chatFilter.setBlessings(!config.chatFilter.blessings())
                    ),
                    SettingEntry.toggle(
                        "Loot Spam",
                        config.chatFilter::lootSpam,
                        () -> config.chatFilter.setLootSpam(!config.chatFilter.lootSpam())
                    ),
                    SettingEntry.toggle(
                        "Watcher",
                        config.chatFilter::watcher,
                        () -> config.chatFilter.setWatcher(!config.chatFilter.watcher())
                    ),
                    SettingEntry.toggle(
                        "Boss Messages",
                        config.chatFilter::bossMessages,
                        () -> config.chatFilter.setBossMessages(!config.chatFilter.bossMessages())
                    ).withChildren(List.of(
                        SettingEntry.toggle(
                            "Bonzo",
                            config.chatFilter::bonzo,
                            () -> config.chatFilter.setBonzo(!config.chatFilter.bonzo())
                        ),
                        SettingEntry.toggle(
                            "Scarf",
                            config.chatFilter::scarf,
                            () -> config.chatFilter.setScarf(!config.chatFilter.scarf())
                        ),
                        SettingEntry.toggle(
                            "Professor",
                            config.chatFilter::professor,
                            () -> config.chatFilter.setProfessor(!config.chatFilter.professor())
                        ),
                        SettingEntry.toggle(
                            "Thorn",
                            config.chatFilter::thorn,
                            () -> config.chatFilter.setThorn(!config.chatFilter.thorn())
                        ),
                        SettingEntry.toggle(
                            "Livid",
                            config.chatFilter::livid,
                            () -> config.chatFilter.setLivid(!config.chatFilter.livid())
                        ),
                        SettingEntry.toggle(
                            "Sadan",
                            config.chatFilter::sadan,
                            () -> config.chatFilter.setSadan(!config.chatFilter.sadan())
                        ),
                        SettingEntry.toggle(
                            "Maxor",
                            config.chatFilter::maxor,
                            () -> config.chatFilter.setMaxor(!config.chatFilter.maxor())
                        ),
                        SettingEntry.toggle(
                            "Storm",
                            config.chatFilter::storm,
                            () -> config.chatFilter.setStorm(!config.chatFilter.storm())
                        ),
                        SettingEntry.toggle(
                            "Goldor",
                            config.chatFilter::goldor,
                            () -> config.chatFilter.setGoldor(!config.chatFilter.goldor())
                        ),
                        SettingEntry.toggle(
                            "Necron",
                            config.chatFilter::necron,
                            () -> config.chatFilter.setNecron(!config.chatFilter.necron())
                        ),
                        SettingEntry.toggle(
                            "Wither King",
                            config.chatFilter::witherKing,
                            () -> config.chatFilter.setWitherKing(!config.chatFilter.witherKing())
                        )
                    ))
                )
            ),
            new FeatureEntry(
                "Splits Overlay",
                config.splits::enabled,
                () -> config.splits.setEnabled(!config.splits.enabled()),
                List.of()
            )
        )));
        result.add(new CategoryEntry("Slayer", List.of(
            new FeatureEntry(
                "Tarantula Helper",
                config.slayer::tarantulaHelperEnabled,
                () -> config.slayer.setTarantulaHelperEnabled(!config.slayer.tarantulaHelperEnabled()),
                List.of(
                    SettingEntry.toggle(
                        "Where Is My Boss",
                        config.slayer::eggSacPredictionRendererEnabled,
                        () -> config.slayer.setEggSacPredictionRendererEnabled(!config.slayer.eggSacPredictionRendererEnabled())
                    ),
                    SettingEntry.toggle(
                        "Egg Sac Prediction",
                        config.slayer::eggSacPredictionEnabled,
                        () -> config.slayer.setEggSacPredictionEnabled(!config.slayer.eggSacPredictionEnabled())
                    ),
                    SettingEntry.choice(
                        "Prediction Mode",
                        config.slayer::eggSacPredictionRenderModeLabel,
                        config.slayer::cycleEggSacPredictionRenderMode
                    )
                )
            )
        ))); 
        result.add(new CategoryEntry("Util", List.of(
            new FeatureEntry(
                "Lobby Hop Helper",
                config.misc::lobbyHopHelperEnabled,
                () -> config.misc.setLobbyHopHelperEnabled(!config.misc.lobbyHopHelperEnabled()),
                List.of()
            ),
            new FeatureEntry(
                "Hypixel API",
                config.misc::hypixelApiEnabled,
                () -> config.misc.setHypixelApiEnabled(!config.misc.hypixelApiEnabled()),
                List.of(
                    SettingEntry.dynamicLabel(HypixelSkyBlockProfileClient.INSTANCE::statusMessage),
                    SettingEntry.text("API Key", config.misc::hypixelApiKey, config.misc::setHypixelApiKey)
                )
            ),
            new FeatureEntry(
                "Chat Commands",
                config.misc::chatCommandsEnabled,
                () -> config.misc.setChatCommandsEnabled(!config.misc.chatCommandsEnabled()),
                List.of(
                    SettingEntry.toggle(
                        "!c50",
                        config.misc::c50ChatCommandEnabled,
                        () -> config.misc.setC50ChatCommandEnabled(!config.misc.c50ChatCommandEnabled())
                    ).withChildren(List.of(
                        SettingEntry.toggle(
                            "Party",
                            config.misc::c50PartyCommandsEnabled,
                            () -> config.misc.setC50PartyCommandsEnabled(!config.misc.c50PartyCommandsEnabled())
                        ),
                        SettingEntry.toggle(
                            "Guild",
                            config.misc::c50GuildCommandsEnabled,
                            () -> config.misc.setC50GuildCommandsEnabled(!config.misc.c50GuildCommandsEnabled())
                        ),
                        SettingEntry.toggle(
                            "All Chat",
                            config.misc::c50AllChatCommandsEnabled,
                            () -> config.misc.setC50AllChatCommandsEnabled(!config.misc.c50AllChatCommandsEnabled())
                        ),
                        SettingEntry.toggle(
                            "DMs",
                            config.misc::c50PrivateCommandsEnabled,
                            () -> config.misc.setC50PrivateCommandsEnabled(!config.misc.c50PrivateCommandsEnabled())
                        )
                    )),
                    SettingEntry.toggle(
                        "!ca50",
                        config.misc::ca50ChatCommandEnabled,
                        () -> config.misc.setCa50ChatCommandEnabled(!config.misc.ca50ChatCommandEnabled())
                    ).withChildren(List.of(
                        SettingEntry.toggle(
                            "Party",
                            config.misc::ca50PartyCommandsEnabled,
                            () -> config.misc.setCa50PartyCommandsEnabled(!config.misc.ca50PartyCommandsEnabled())
                        ),
                        SettingEntry.toggle(
                            "Guild",
                            config.misc::ca50GuildCommandsEnabled,
                            () -> config.misc.setCa50GuildCommandsEnabled(!config.misc.ca50GuildCommandsEnabled())
                        ),
                        SettingEntry.toggle(
                            "All Chat",
                            config.misc::ca50AllChatCommandsEnabled,
                            () -> config.misc.setCa50AllChatCommandsEnabled(!config.misc.ca50AllChatCommandsEnabled())
                        ),
                        SettingEntry.toggle(
                            "DMs",
                            config.misc::ca50PrivateCommandsEnabled,
                            () -> config.misc.setCa50PrivateCommandsEnabled(!config.misc.ca50PrivateCommandsEnabled())
                        )
                    )),
                    SettingEntry.toggle(
                        "!tps",
                        config.misc::tpsChatCommandEnabled,
                        () -> config.misc.setTpsChatCommandEnabled(!config.misc.tpsChatCommandEnabled())
                    ).withChildren(List.of(
                        SettingEntry.toggle(
                            "Party",
                            config.misc::tpsPartyCommandsEnabled,
                            () -> config.misc.setTpsPartyCommandsEnabled(!config.misc.tpsPartyCommandsEnabled())
                        ),
                        SettingEntry.toggle(
                            "Guild",
                            config.misc::tpsGuildCommandsEnabled,
                            () -> config.misc.setTpsGuildCommandsEnabled(!config.misc.tpsGuildCommandsEnabled())
                        ),
                        SettingEntry.toggle(
                            "All Chat",
                            config.misc::tpsAllChatCommandsEnabled,
                            () -> config.misc.setTpsAllChatCommandsEnabled(!config.misc.tpsAllChatCommandsEnabled())
                        ),
                        SettingEntry.toggle(
                            "DMs",
                            config.misc::tpsPrivateCommandsEnabled,
                            () -> config.misc.setTpsPrivateCommandsEnabled(!config.misc.tpsPrivateCommandsEnabled())
                        )
                    ))
                )
            ),
            new FeatureEntry(
                "Custom Sounds",
                config.misc::customSoundsEnabled,
                () -> config.misc.setCustomSoundsEnabled(!config.misc.customSoundsEnabled()),
                customSoundSettings(config)
            ),
            new FeatureEntry(
                "Loadouts Auto Close",
                config.misc::loadoutsAutoCloseEnabled,
                () -> config.misc.setLoadoutsAutoCloseEnabled(!config.misc.loadoutsAutoCloseEnabled()),
                loadoutAutoCloseSettings(config)
            ),
            new FeatureEntry(
                "Superpairs Helper",
                config.misc::superpairsHelperEnabled,
                () -> config.misc.setSuperpairsHelperEnabled(!config.misc.superpairsHelperEnabled()),
                List.of(
                    SettingEntry.toggle(
                        "Debug",
                        config.misc::superpairsHelperDebugEnabled,
                        () -> config.misc.setSuperpairsHelperDebugEnabled(!config.misc.superpairsHelperDebugEnabled())
                    )
                )
            )
        )));
        result.add(new CategoryEntry("Debug", List.of(
            new FeatureEntry(
                KungUpdater.INSTANCE::buttonLabel,
                KungUpdater.INSTANCE::isUpdateAvailable,
                KungUpdater.INSTANCE::canInstallUpdate,
                () -> KungUpdater.INSTANCE.installLatestAsync(Minecraft.getInstance()),
                List.of(SettingEntry.dynamicLabel(KungUpdater.INSTANCE::statusMessage))
            ),
            new FeatureEntry(
                "Debug Messages",
                config.debug::enabled,
                () -> config.debug.setEnabled(!config.debug.enabled()),
                List.of(
                    SettingEntry.toggle(
                        "Context",
                        config.debug::contextMessages,
                        () -> config.debug.setContextMessages(!config.debug.contextMessages())
                    ),
                    SettingEntry.toggle(
                        "Dungeon",
                        config.debug::dungeonMessages,
                        () -> config.debug.setDungeonMessages(!config.debug.dungeonMessages())
                    ),
                    SettingEntry.toggle(
                        "Interface",
                        config.debug::interfaceMessages,
                        () -> config.debug.setInterfaceMessages(!config.debug.interfaceMessages())
                    ),
                    SettingEntry.toggle(
                        "Tarantula",
                        config.debug::tarantulaMessages,
                        () -> config.debug.setTarantulaMessages(!config.debug.tarantulaMessages())
                    ).withChildren(List.of(
                        SettingEntry.toggle(
                            "Slayer Spawned",
                            config.slayer::tarantulaDebugSlayerSpawned,
                            () -> config.slayer.setTarantulaDebugSlayerSpawned(!config.slayer.tarantulaDebugSlayerSpawned())
                        ),
                        SettingEntry.toggle(
                            "Slayer Pos",
                            config.slayer::tarantulaDebugSlayerPosition,
                            () -> config.slayer.setTarantulaDebugSlayerPosition(!config.slayer.tarantulaDebugSlayerPosition())
                        ),
                        SettingEntry.toggle(
                            "Phase Change",
                            config.slayer::tarantulaDebugSlayerPhaseChange,
                            () -> config.slayer.setTarantulaDebugSlayerPhaseChange(!config.slayer.tarantulaDebugSlayerPhaseChange())
                        ),
                        SettingEntry.toggle(
                            "Slayer Dead",
                            config.slayer::tarantulaDebugSlayerDead,
                            () -> config.slayer.setTarantulaDebugSlayerDead(!config.slayer.tarantulaDebugSlayerDead())
                        ),
                        SettingEntry.toggle(
                            "Egg Sac Start",
                            config.slayer::tarantulaDebugEggSacPhaseStart,
                            () -> config.slayer.setTarantulaDebugEggSacPhaseStart(!config.slayer.tarantulaDebugEggSacPhaseStart())
                        ),
                        SettingEntry.toggle(
                            "Egg Sac Done",
                            config.slayer::tarantulaDebugEggSacPhaseDone,
                            () -> config.slayer.setTarantulaDebugEggSacPhaseDone(!config.slayer.tarantulaDebugEggSacPhaseDone())
                        )
                    ))
                )
            ),
            new FeatureEntry(
                "Room Sync",
                config.dungeon::roomSyncEnabled,
                () -> toggleRoomSync(config),
                List.of(
                    SettingEntry.dynamicLabel(() -> "Status: " + DungeonRoomDataSyncClient.INSTANCE.menuStatus()),
                    SettingEntry.text(
                        "Server",
                        config.dungeon::roomSyncServerUrl,
                        config.dungeon::setRoomSyncServerUrl
                    ),
                    SettingEntry.text(
                        "Token",
                        config.dungeon::roomSyncToken,
                        config.dungeon::setRoomSyncToken
                    ),
                    SettingEntry.toggle(
                        "Upload",
                        config.dungeon::roomSyncUploadEnabled,
                        () -> config.dungeon.setRoomSyncUploadEnabled(!config.dungeon.roomSyncUploadEnabled())
                    ),
                    SettingEntry.button(
                        "Pull",
                        "Rooms",
                        () -> DungeonRoomDataSyncClient.INSTANCE.pullAsync(Minecraft.getInstance())
                    ),
                    SettingEntry.button(
                        "Ping",
                        "Check",
                        () -> DungeonRoomDataSyncClient.INSTANCE.pingAsync(Minecraft.getInstance())
                    )
                )
            )
        )));
        return result;
    }

    private static void toggleRoomSync(KungConfig config) {
        config.dungeon.setRoomSyncEnabled(!config.dungeon.roomSyncEnabled());
        DungeonKnownRoomCatalog.reload();
    }

    private static List<SettingEntry> customSoundSettings(KungConfig config) {
        List<SettingEntry> settings = new ArrayList<>();
        settings.add(SettingEntry.dynamicLabel(CustomSoundsFeature::soundsFolderStatus));
        settings.add(SettingEntry.button("Scan Folder", "Refresh", CustomSoundsFeature::refreshSoundIndex));
        settings.add(SettingEntry.text("Arrow Hit Files", config.misc::customArrowHitSounds, config.misc::setCustomArrowHitSounds));
        settings.add(SettingEntry.slider(
            "Arrow Default Volume",
            config.misc::customArrowHitVolumeTenths,
            config.misc::setCustomArrowHitVolumeTenths,
            0,
            50,
            1
        ));
        settings.add(SettingEntry.slider(
            "Arrow Default Pitch",
            config.misc::customArrowHitPitchHundredths,
            config.misc::setCustomArrowHitPitchHundredths,
            25,
            300,
            5
        ));
        settings.add(SettingEntry.button("Test Arrow", "Play", CustomSoundsFeature::playArrowHit));
        addArrowSoundSettings(settings, config);
        settings.add(SettingEntry.text(
            "Wither End Files",
            config.misc::customWitherShieldExpireSounds,
            config.misc::setCustomWitherShieldExpireSounds
        ));
        settings.add(SettingEntry.slider(
            "Wither Default Volume",
            config.misc::customWitherShieldExpireVolumeTenths,
            config.misc::setCustomWitherShieldExpireVolumeTenths,
            0,
            50,
            1
        ));
        settings.add(SettingEntry.slider(
            "Wither Default Pitch",
            config.misc::customWitherShieldExpirePitchHundredths,
            config.misc::setCustomWitherShieldExpirePitchHundredths,
            25,
            300,
            5
        ));
        settings.add(SettingEntry.button("Test Wither", "Play", CustomSoundsFeature::playWitherShieldExpire));
        addWitherSoundSettings(settings, config);
        return List.copyOf(settings);
    }

    private static void addArrowSoundSettings(List<SettingEntry> settings, KungConfig config) {
        for (String soundName : CustomSoundsFeature.configuredSoundNames(config.misc.customArrowHitSounds())) {
            settings.add(SettingEntry.group("Arrow: " + soundName).withChildren(List.of(
                SettingEntry.slider(
                    "Volume",
                    () -> config.misc.customArrowHitSoundVolumeTenths(soundName),
                    value -> config.misc.setCustomArrowHitSoundVolumeTenths(soundName, value),
                    0,
                    50,
                    1
                ),
                SettingEntry.slider(
                    "Pitch",
                    () -> config.misc.customArrowHitSoundPitchHundredths(soundName),
                    value -> config.misc.setCustomArrowHitSoundPitchHundredths(soundName, value),
                    25,
                    300,
                    5
                ),
                SettingEntry.button("Test", "Play", () -> CustomSoundsFeature.playArrowHitSound(soundName))
            )));
        }
    }

    private static void addWitherSoundSettings(List<SettingEntry> settings, KungConfig config) {
        for (String soundName : CustomSoundsFeature.configuredSoundNames(config.misc.customWitherShieldExpireSounds())) {
            settings.add(SettingEntry.group("Wither: " + soundName).withChildren(List.of(
                SettingEntry.slider(
                    "Volume",
                    () -> config.misc.customWitherShieldExpireSoundVolumeTenths(soundName),
                    value -> config.misc.setCustomWitherShieldExpireSoundVolumeTenths(soundName, value),
                    0,
                    50,
                    1
                ),
                SettingEntry.slider(
                    "Pitch",
                    () -> config.misc.customWitherShieldExpireSoundPitchHundredths(soundName),
                    value -> config.misc.setCustomWitherShieldExpireSoundPitchHundredths(soundName, value),
                    25,
                    300,
                    5
                ),
                SettingEntry.button("Test", "Play", () -> CustomSoundsFeature.playWitherShieldExpireSound(soundName))
            )));
        }
    }

    private static List<SettingEntry> loadoutAutoCloseSettings(KungConfig config) {
        List<SettingEntry> settings = new ArrayList<>();
        settings.add(SettingEntry.toggle(
            "Close Only On Change",
            config.misc::loadoutsCloseOnlyOnChange,
            () -> config.misc.setLoadoutsCloseOnlyOnChange(!config.misc.loadoutsCloseOnlyOnChange())
        ));
        for (int index = 0; index < 12; index++) {
            int loadoutIndex = index;
            settings.add(SettingEntry.keybind(
                "Loadout " + (index + 1),
                () -> LoadoutsAutoCloseFeature.keybindDisplay(config.misc.loadoutKeybind(loadoutIndex)),
                keybind -> config.misc.setLoadoutKeybind(loadoutIndex, keybind)
            ));
        }
        return List.copyOf(settings);
    }

    private static void toggleLocalRoomData(KungConfig config) {
        config.dungeon.setLocalRoomDataEnabled(!config.dungeon.localRoomDataEnabled());
        DungeonRoomClassifier.reload();
        DungeonKnownRoomCatalog.reload();
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int centerX, int y, int color, boolean shadow) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, color, shadow);
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private final class TextEditorOverlay {
        private static final int MODAL_BACKDROP = 0x99000000;
        private static final int MODAL_PANEL = 0xF01A1D22;
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
            this.editBox = new UiTextField(font, Component.literal(setting.label()))
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
            fill(graphics, 0, 0, width, height, MODAL_BACKDROP);
            fill(graphics, panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, MODAL_BORDER);
            fill(graphics, panelX, panelY, panelX + panelWidth, panelY + panelHeight, MODAL_PANEL);
            drawCentered(graphics, trimToWidth(setting.label(), panelWidth - 18), width / 2, panelY + 10, THEME.text(), true);
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
            fill(graphics, x, y, x + buttonWidth, y + buttonHeight, hovered ? THEME.accent() : THEME.accentDark());
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
            MouseButtonEvent guiEvent = new MouseButtonEvent(toGuiX(event.x()), toGuiY(event.y()), event.buttonInfo());
            return editBox.mouseDragged(guiEvent, dragX, dragY);
        }

        private boolean mouseReleased(MouseButtonEvent event) {
            MouseButtonEvent guiEvent = new MouseButtonEvent(toGuiX(event.x()), toGuiY(event.y()), event.buttonInfo());
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
            panelWidth = Math.min(Math.max(260, width - 80), 420);
            panelHeight = 86;
            panelX = width / 2 - panelWidth / 2;
            panelY = height / 2 - panelHeight / 2;
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

    private record TooltipRequest(String text, int x, int y) {
    }

    private record CategoryScrollArea(CategoryEntry category, int maxScroll) {
    }

    private record CategoryEntry(String name, List<FeatureEntry> features) {
    }

    private record FeatureEntry(
        Supplier<String> nameSupplier,
        BooleanSupplier enabledSupplier,
        BooleanSupplier clickableSupplier,
        Runnable toggle,
        List<SettingEntry> settings
    ) {
        FeatureEntry(
            String name,
            BooleanSupplier enabledSupplier,
            Runnable toggle,
            List<SettingEntry> settings
        ) {
            this(() -> name, enabledSupplier, () -> toggle != null, toggle, settings);
        }

        String name() {
            return nameSupplier.get();
        }

        boolean enabled() {
            return enabledSupplier != null && enabledSupplier.getAsBoolean();
        }

        boolean clickable() {
            return toggle != null && clickableSupplier != null && clickableSupplier.getAsBoolean();
        }

    }

}
