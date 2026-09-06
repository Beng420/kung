package com.github.beng420.kung.config;

import static com.github.beng420.kung.util.GuiDraw.fill;

import com.github.beng420.kung.feature.dungeon.DungeonMapOverlayConfig;
import com.github.beng420.kung.feature.dungeon.DungeonKnownRoomCatalog;
import com.github.beng420.kung.feature.dungeon.DungeonRoomDataSyncClient;
import com.github.beng420.kung.feature.dungeon.DungeonRoomClassifier;
import com.github.beng420.kung.update.KungUpdater;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
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
    private static final int BACKDROP = 0x66000000;
    private static final int PANEL = 0xEE151719;
    private static final int PANEL_SOFT = 0xCC243044;
    private static final int SETTING_ROW = 0xCC2C384C;
    private static final int BLUE = 0xFF3498DB;
    private static final int BLUE_DARK = 0xFF2077AE;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFBBC4D0;
    private static final int DARK_ROW = 0xEE17191B;
    private static final int BORDER = 0xAA000000;

    private static final int LEFT = 8;
    private static final int COLUMN_WIDTH = 150;
    private static final int ROW_HEIGHT = 18;
    private static final int SETTING_HEIGHT = 16;
    private static final int GAP = 8;
    private static final int TOP = 22;
    private static final int HEADER_HEIGHT = 18;
    private static final int SETTING_CONTROL_WIDTH = 58;
    private static final int SCROLL_STEP = 42;

    private final List<CategoryEntry> categories;
    private final List<ClickRegion> clickRegions = new ArrayList<>();
    private FeatureEntry expandedFeature;
    private SettingEntry expandedSetting;
    private int horizontalScroll;
    private String search = "";
    private boolean searchFocused;

    public KungConfigScreen() {
        this(null);
    }

    public KungConfigScreen(String expandedFeatureName) {
        super(Component.literal("Kung Settings"));
        categories = createCategories();
        expandedFeature = findFeature(expandedFeatureName);
        KungUpdater.INSTANCE.checkForUpdatesAsync();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        horizontalScroll = Math.clamp(horizontalScroll, 0, maxHorizontalScroll());
        clickRegions.clear();
        fill(graphics, 0, 0, width, height, BACKDROP);
        drawTitle(graphics);
        drawColumns(graphics, mouseX, mouseY);
        drawSearchBox(graphics);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = toGuiX(event.x());
        int mouseY = toGuiY(event.y());
        int button = event.button();

        if (clickSearchBox(mouseX, mouseY, button)) {
            return true;
        }

        searchFocused = false;
        for (int index = clickRegions.size() - 1; index >= 0; index--) {
            ClickRegion region = clickRegions.get(index);
            if (inside(mouseX, mouseY, region.x(), region.y(), region.width(), region.height())) {
                return region.action().click(mouseX, mouseY, button);
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = maxHorizontalScroll();
        if (maxScroll <= 0 || scrollY == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        int direction = scrollY < 0 ? 1 : -1;
        horizontalScroll = Math.clamp(horizontalScroll + direction * SCROLL_STEP, 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
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
        if (!searchFocused || !event.isAllowedChatCharacter()) {
            return super.charTyped(event);
        }
        search += event.codepointAsString();
        return true;
    }

    private void drawTitle(GuiGraphicsExtractor graphics) {
        String title = "Kung";
        graphics.text(font, title, width / 2 - font.width(title) / 2, 8, TEXT, true);
    }

    private void drawColumns(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int startX = LEFT - horizontalScroll;
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
        int panelHeight = Math.min(height - 52, HEADER_HEIGHT + categoryContentHeight(category));
        fill(graphics, x - 1, TOP - 1, x + COLUMN_WIDTH + 1, TOP + panelHeight + 1, BORDER);
        fill(graphics, x, TOP, x + COLUMN_WIDTH, TOP + panelHeight, PANEL);

        drawCentered(graphics, trimToWidth(category.name(), COLUMN_WIDTH - 8), x + COLUMN_WIDTH / 2, TOP + 5, TEXT, true);

        int rowY = TOP + HEADER_HEIGHT;
        int panelBottom = TOP + panelHeight;
        for (FeatureEntry feature : category.features()) {
            if (!featureMatches(category, feature)) {
                continue;
            }
            if (rowY + ROW_HEIGHT > panelBottom) {
                return;
            }
            boolean hovered = inside(mouseX, mouseY, x, rowY, COLUMN_WIDTH, ROW_HEIGHT);
            boolean clickable = feature.clickable() || !feature.settings().isEmpty();
            int color = feature.enabled() ? BLUE : hovered && clickable ? PANEL_SOFT : DARK_ROW;
            fill(graphics, x, rowY, x + COLUMN_WIDTH, rowY + ROW_HEIGHT, color);
            drawCentered(graphics, trimToWidth(feature.name(), COLUMN_WIDTH - 8), x + COLUMN_WIDTH / 2, rowY + 5, TEXT, true);
            addClickRegion(x, rowY, COLUMN_WIDTH, ROW_HEIGHT, (clickX, clickY, button) -> clickFeature(feature, button));
            rowY += ROW_HEIGHT;

            if (feature == expandedFeature) {
                for (SettingEntry setting : feature.settings()) {
                    if (rowY + SETTING_HEIGHT > panelBottom) {
                        return;
                    }
                    drawSettingRow(graphics, setting, x, rowY, mouseX, mouseY, 0);
                    rowY += SETTING_HEIGHT;
                    if (setting == expandedSetting) {
                        for (SettingEntry child : setting.children()) {
                            if (rowY + SETTING_HEIGHT > panelBottom) {
                                return;
                            }
                            drawSettingRow(graphics, child, x, rowY, mouseX, mouseY, 12);
                            rowY += SETTING_HEIGHT;
                        }
                    }
                }
                fill(graphics, x, rowY - 1, x + COLUMN_WIDTH, rowY, BLUE_DARK);
            }
        }
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
        fill(graphics, x, rowY, x + COLUMN_WIDTH, rowY + SETTING_HEIGHT, hovered ? PANEL_SOFT : SETTING_ROW);
        if (setting.expandable()) {
            graphics.text(font, setting == expandedSetting ? "v" : ">", x + 5 + indent, rowY + 4, TEXT, true);
        }
        int labelX = x + 5 + indent + (setting.expandable() ? 11 : 0);
        graphics.text(font, trimToWidth(setting.label(), COLUMN_WIDTH - SETTING_CONTROL_WIDTH - indent - 20), labelX, rowY + 4, TEXT, true);
        int controlX = x + COLUMN_WIDTH - SETTING_CONTROL_WIDTH - 5;
        setting.draw(graphics, controlX, rowY, SETTING_CONTROL_WIDTH, SETTING_HEIGHT);
        addClickRegion(x, rowY, COLUMN_WIDTH, SETTING_HEIGHT, (clickX, clickY, button) ->
            clickSetting(setting, clickX, button, controlX)
        );
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
        fill(graphics, x - 2, y - 2, x + boxWidth + 2, y + boxHeight + 2, searchFocused ? TEXT : BLUE);
        fill(graphics, x, y, x + boxWidth, y + boxHeight, PANEL);
        String label = search.isEmpty() ? "Search here..." : search;
        drawCentered(graphics, label, x + boxWidth / 2, y + 6, search.isEmpty() ? MUTED : TEXT, true);
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
            expandedFeature = expandedFeature == feature ? null : feature;
            expandedSetting = null;
            return true;
        }
        if (button == 0 && feature.clickable() && feature.toggle() != null) {
            feature.toggle().run();
            return true;
        }
        return true;
    }

    private boolean clickSetting(SettingEntry setting, int mouseX, int button, int controlX) {
        if (button == 1 && setting.expandable()) {
            expandedSetting = expandedSetting == setting ? null : setting;
            return true;
        }
        if (button == 0) {
            setting.click(mouseX, controlX, SETTING_CONTROL_WIDTH);
            return true;
        }
        return true;
    }

    private void addClickRegion(int x, int y, int width, int height, ClickAction action) {
        clickRegions.add(new ClickRegion(x, y, width, height, action));
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

    private int categoryContentHeight(CategoryEntry category) {
        int total = 0;
        for (FeatureEntry feature : category.features()) {
            if (!featureMatches(category, feature)) {
                continue;
            }
            total += ROW_HEIGHT;
            if (feature == expandedFeature) {
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
        DungeonMapOverlayConfig config = DungeonMapOverlayConfig.INSTANCE;
        List<CategoryEntry> result = new ArrayList<>();
        result.add(new CategoryEntry("Dungeon", List.of(
            new FeatureEntry(
                "Dungeon Map",
                config::enabled,
                () -> config.setEnabled(!config.enabled()),
                List.of(
                    SettingEntry.toggle("Enabled", config::enabled, () -> config.setEnabled(!config.enabled())),
                    SettingEntry.stepper("X Position", config::x, config::setX, -300, 1000, 4),
                    SettingEntry.stepper("Y Position", config::y, config::setY, -300, 700, 4),
                    SettingEntry.stepper("Scale", config::scale, config::setScale, 25, 150, 5),
                    SettingEntry.stepper(
                        "Unopened Alpha",
                        config::unopenedRoomAlpha,
                        config::setUnopenedRoomAlpha,
                        0,
                        28,
                        2
                    ),
                    SettingEntry.toggle("Legend", config::showLegend, () -> config.setShowLegend(!config.showLegend())),
                    SettingEntry.toggle(
                        "Player Tracking",
                        config::playerTrackingEnabled,
                        () -> config.setPlayerTrackingEnabled(!config.playerTrackingEnabled())
                    ),
                    SettingEntry.toggle(
                        "Debug Messages",
                        config::debugMessages,
                        () -> config.setDebugMessages(!config.debugMessages())
                    ),
                    SettingEntry.toggle(
                        "Room Crypts",
                        config::debugRoomCrypts,
                        () -> config.setDebugRoomCrypts(!config.debugRoomCrypts())
                    ),
                    SettingEntry.toggle(
                        "Room Debug",
                        config::debugRoomMatches,
                        () -> config.setDebugRoomMatches(!config.debugRoomMatches())
                    ),
                    SettingEntry.toggle(
                        "Local Data",
                        config::localRoomDataEnabled,
                        () -> toggleLocalRoomData(config)
                    )
                )
            ),
            new FeatureEntry(
                "Splits Overlay",
                config::splitsEnabled,
                () -> config.setSplitsEnabled(!config.splitsEnabled()),
                List.of(
                    SettingEntry.toggle(
                        "Enabled",
                        config::splitsEnabled,
                        () -> config.setSplitsEnabled(!config.splitsEnabled())
                    ),
                    SettingEntry.stepper("X Position", config::splitsX, config::setSplitsX, -300, 1000, 4),
                    SettingEntry.stepper("Y Position", config::splitsY, config::setSplitsY, -300, 700, 4),
                    SettingEntry.stepper("Scale", config::splitsScale, config::setSplitsScale, 25, 300, 5)
                )
            ),
            FeatureEntry.placeholder("Room Learning"),
            FeatureEntry.placeholder("Door Detection"),
            FeatureEntry.placeholder("Run Stats")
        )));
        result.add(new CategoryEntry("Mining", List.of(
            FeatureEntry.placeholder("Gemstone Overlay"),
            FeatureEntry.placeholder("Powder Tracker"),
            FeatureEntry.placeholder("Route Helper")
        )));
        result.add(new CategoryEntry("Slayer", List.of(
            new FeatureEntry(
                "Tarantula Helper",
                config::tarantulaHelperEnabled,
                () -> config.setTarantulaHelperEnabled(!config.tarantulaHelperEnabled()),
                List.of(
                    SettingEntry.toggle(
                        "Where Is My Boss",
                        config::eggSacPredictionRendererEnabled,
                        () -> config.setEggSacPredictionRendererEnabled(!config.eggSacPredictionRendererEnabled())
                    ),
                    SettingEntry.toggle(
                        "Egg Sac Prediction",
                        config::eggSacPredictionEnabled,
                        () -> config.setEggSacPredictionEnabled(!config.eggSacPredictionEnabled())
                    ),
                    SettingEntry.choice(
                        "Prediction Mode",
                        config::eggSacPredictionRenderModeLabel,
                        config::cycleEggSacPredictionRenderMode
                    ),
                    SettingEntry.toggle(
                        "Debug Messages",
                        config::tarantulaHelperDebugMessages,
                        () -> config.setTarantulaHelperDebugMessages(!config.tarantulaHelperDebugMessages())
                    ).withChildren(List.of(
                        SettingEntry.toggle(
                            "Slayer Spawned",
                            config::tarantulaDebugSlayerSpawned,
                            () -> config.setTarantulaDebugSlayerSpawned(!config.tarantulaDebugSlayerSpawned())
                        ),
                        SettingEntry.toggle(
                            "Slayer Pos",
                            config::tarantulaDebugSlayerPosition,
                            () -> config.setTarantulaDebugSlayerPosition(!config.tarantulaDebugSlayerPosition())
                        ),
                        SettingEntry.toggle(
                            "Phase Change",
                            config::tarantulaDebugSlayerPhaseChange,
                            () -> config.setTarantulaDebugSlayerPhaseChange(!config.tarantulaDebugSlayerPhaseChange())
                        ),
                        SettingEntry.toggle(
                            "Slayer Dead",
                            config::tarantulaDebugSlayerDead,
                            () -> config.setTarantulaDebugSlayerDead(!config.tarantulaDebugSlayerDead())
                        ),
                        SettingEntry.toggle(
                            "Egg Sac Start",
                            config::tarantulaDebugEggSacPhaseStart,
                            () -> config.setTarantulaDebugEggSacPhaseStart(!config.tarantulaDebugEggSacPhaseStart())
                        ),
                        SettingEntry.toggle(
                            "Egg Sac Done",
                            config::tarantulaDebugEggSacPhaseDone,
                            () -> config.setTarantulaDebugEggSacPhaseDone(!config.tarantulaDebugEggSacPhaseDone())
                        )
                    ))
                )
            )
        ))); 
        result.add(new CategoryEntry("Render", List.of(
            FeatureEntry.placeholder("Map Info"),
            FeatureEntry.placeholder("Secret Counter"),
            FeatureEntry.placeholder("Score Info")
        )));
        result.add(new CategoryEntry("Misc", List.of(
            new FeatureEntry(
                "Lobby Hop Helper",
                config::lobbyHopHelperEnabled,
                () -> config.setLobbyHopHelperEnabled(!config.lobbyHopHelperEnabled()),
                List.of(SettingEntry.toggle(
                    "Enabled",
                    config::lobbyHopHelperEnabled,
                    () -> config.setLobbyHopHelperEnabled(!config.lobbyHopHelperEnabled())
                ))
            ),
            new FeatureEntry(
                "Superpairs Helper",
                config::superpairsHelperEnabled,
                () -> config.setSuperpairsHelperEnabled(!config.superpairsHelperEnabled()),
                List.of(
                    SettingEntry.toggle(
                        "Enabled",
                        config::superpairsHelperEnabled,
                        () -> config.setSuperpairsHelperEnabled(!config.superpairsHelperEnabled())
                    ),
                    SettingEntry.toggle(
                        "Debug",
                        config::superpairsHelperDebugEnabled,
                        () -> config.setSuperpairsHelperDebugEnabled(!config.superpairsHelperDebugEnabled())
                    ),
                    SettingEntry.stepper("X Position", config::superpairsHelperX, config::setSuperpairsHelperX, -300, 1000, 4),
                    SettingEntry.stepper("Y Position", config::superpairsHelperY, config::setSuperpairsHelperY, -300, 700, 4),
                    SettingEntry.stepper("Scale", config::superpairsHelperScale, config::setSuperpairsHelperScale, 25, 300, 5)
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
                "Interface Debug",
                config::debugInterfaceMessages,
                () -> config.setDebugInterfaceMessages(!config.debugInterfaceMessages()),
                List.of(SettingEntry.toggle(
                    "Chat Messages",
                    config::debugInterfaceMessages,
                    () -> config.setDebugInterfaceMessages(!config.debugInterfaceMessages())
                ))
            ),
            new FeatureEntry(
                "Room Sync",
                config::roomSyncEnabled,
                () -> toggleRoomSync(config),
                List.of(
                    SettingEntry.toggle(
                        "Enabled",
                        config::roomSyncEnabled,
                        () -> toggleRoomSync(config)
                    ),
                    SettingEntry.toggle(
                        "Upload",
                        config::roomSyncUploadEnabled,
                        () -> config.setRoomSyncUploadEnabled(!config.roomSyncUploadEnabled())
                    ),
                    SettingEntry.dynamicLabel(DungeonRoomDataSyncClient.INSTANCE::statusMessage)
                )
            ),
            FeatureEntry.placeholder("Scan Recorder"),
            FeatureEntry.placeholder("Known Rooms"),
            FeatureEntry.placeholder("Door Probe")
        )));
        return result;
    }

    private static void toggleRoomSync(DungeonMapOverlayConfig config) {
        config.setRoomSyncEnabled(!config.roomSyncEnabled());
        DungeonKnownRoomCatalog.reload();
    }

    private static void toggleLocalRoomData(DungeonMapOverlayConfig config) {
        config.setLocalRoomDataEnabled(!config.localRoomDataEnabled());
        DungeonRoomClassifier.reload();
        DungeonKnownRoomCatalog.reload();
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int centerX, int y, int color, boolean shadow) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, color, shadow);
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @FunctionalInterface
    private interface ClickAction {
        boolean click(int mouseX, int mouseY, int button);
    }

    private record ClickRegion(int x, int y, int width, int height, ClickAction action) {
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

        static FeatureEntry placeholder(String name) {
            return new FeatureEntry(() -> name, () -> false, () -> false, null, List.of(
                SettingEntry.label("Not wired yet")
            ));
        }
    }

    private record SettingEntry(
        Supplier<String> labelSupplier,
        Kind kind,
        BooleanSupplier booleanSupplier,
        Runnable toggle,
        IntSupplier intSupplier,
        IntConsumer intConsumer,
        int min,
        int max,
        int step,
        Supplier<String> choiceSupplier,
        Runnable cycleChoice,
        List<SettingEntry> children
    ) {
        String label() {
            return labelSupplier.get();
        }

        static SettingEntry toggle(String label, BooleanSupplier supplier, Runnable toggle) {
            return new SettingEntry(() -> label, Kind.TOGGLE, supplier, toggle, null, null, 0, 0, 0, null, null, List.of());
        }

        static SettingEntry stepper(
            String label,
            IntSupplier supplier,
            IntConsumer consumer,
            int min,
            int max,
            int step
        ) {
            return new SettingEntry(() -> label, Kind.STEPPER, null, null, supplier, consumer, min, max, step, null, null, List.of());
        }

        static SettingEntry choice(String label, Supplier<String> supplier, Runnable cycle) {
            return new SettingEntry(() -> label, Kind.CHOICE, null, null, null, null, 0, 0, 0, supplier, cycle, List.of());
        }

        static SettingEntry label(String label) {
            return dynamicLabel(() -> label);
        }

        static SettingEntry dynamicLabel(Supplier<String> labelSupplier) {
            return new SettingEntry(labelSupplier, Kind.LABEL, null, null, null, null, 0, 0, 0, null, null, List.of());
        }

        SettingEntry withChildren(List<SettingEntry> children) {
            return new SettingEntry(labelSupplier, kind, booleanSupplier, toggle, intSupplier, intConsumer, min, max, step, choiceSupplier, cycleChoice, children);
        }

        boolean expandable() {
            return !children.isEmpty();
        }

        void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
            Minecraft client = Minecraft.getInstance();
            switch (kind) {
                case TOGGLE -> {
                    boolean enabled = booleanSupplier.getAsBoolean();
                    int toggleX = x + width - 22;
                    fill(graphics, toggleX, y + 3, toggleX + 20, y + height - 3, enabled ? BLUE : 0xFF111318);
                    fill(graphics, toggleX + (enabled ? 11 : 3), y + 5, toggleX + (enabled ? 17 : 9), y + height - 5, TEXT);
                }
                case STEPPER -> {
                    int value = intSupplier.getAsInt();
                    fill(graphics, x + 1, y + 3, x + 13, y + height - 3, BLUE_DARK);
                    fill(graphics, x + width - 13, y + 3, x + width - 1, y + height - 3, BLUE_DARK);
                    graphics.text(client.font, "-", x + 5, y + 4, TEXT, true);
                    graphics.text(client.font, "+", x + width - 10, y + 4, TEXT, true);
                    String lowerLabel = label().toLowerCase(Locale.ROOT);
                    String valueText = value + (lowerLabel.contains("scale") || lowerLabel.contains("alpha") ? "%" : "");
                    graphics.text(client.font, valueText, x + width / 2 - client.font.width(valueText) / 2, y + 4, TEXT, true);
                }
                case CHOICE -> {
                    String text = choiceSupplier.get();
                    fill(graphics, x + 1, y + 3, x + width - 1, y + height - 3, BLUE_DARK);
                    graphics.text(client.font, text, x + width / 2 - client.font.width(text) / 2, y + 4, TEXT, true);
                }
                case LABEL -> {
                    String text = label();
                    graphics.text(client.font, text, x + width - client.font.width(text), y + 4, MUTED, true);
                }
            }
        }

        void click(int mouseX, int x, int width) {
            switch (kind) {
                case TOGGLE -> toggle.run();
                case STEPPER -> {
                    int value = intSupplier.getAsInt();
                    int next = mouseX < x + width / 2
                        ? value - step
                        : value + step;
                    intConsumer.accept(Math.clamp(next, min, max));
                }
                case CHOICE -> cycleChoice.run();
                case LABEL -> {
                }
            }
        }
    }

    private enum Kind {
        TOGGLE,
        STEPPER,
        CHOICE,
        LABEL
    }
}
