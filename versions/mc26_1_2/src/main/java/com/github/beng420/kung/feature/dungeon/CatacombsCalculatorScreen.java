package com.github.beng420.kung.feature.dungeon;

import static com.github.beng420.kung.util.GuiDraw.fill;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.skyblock.SkyBlockMayorTracker;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class CatacombsCalculatorScreen extends Screen {
    private static final URI PIXELSTATS_DUNGEON_API =
        URI.create("https://www.pixelstats.net/api/calc/dungeon");
    private static final int BACKDROP = 0xFF262626;
    private static final int PANEL = 0xFF5A3020;
    private static final int PANEL_DARK = 0xFF472719;
    private static final int PANEL_SOFT = 0xFF70402D;
    private static final int CONTROL = 0xFF737373;
    private static final int CONTROL_HOVER = 0xFF929292;
    private static final int BORDER = 0xFF8B8B8B;
    private static final int TEXT = 0xFFEDEDED;
    private static final int MUTED = 0xFFB4B4B4;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF7CFF83;
    private static final int GOLD = 0xFFFFD166;
    private static final int BLUE = 0xFF79A8FF;

    private static final int TOP = 28;
    private static final int GAP = 10;
    private static final int ROW = 19;
    private static final int CONTROL_HEIGHT = 16;
    private static final int CONTENT_HEIGHT = 800;
    private static final int SCROLL_STEP = 36;
    private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.GERMANY);
    private static final DungeonClass[] CLASSES = DungeonClass.values();
    private static final double[] HECATOMB_BONUSES = {
        0.0, 0.0056, 0.0072, 0.0088, 0.0104, 0.012, 0.0136, 0.0152, 0.0168, 0.0184, 0.02
    };
    private static final long[] CATACOMBS_XP = {
        0L,
        50L,
        125L,
        235L,
        395L,
        625L,
        955L,
        1_425L,
        2_095L,
        3_045L,
        4_385L,
        6_275L,
        8_940L,
        12_700L,
        17_960L,
        25_340L,
        35_640L,
        50_040L,
        70_040L,
        97_640L,
        135_640L,
        188_140L,
        259_640L,
        356_640L,
        488_640L,
        668_640L,
        911_640L,
        1_239_640L,
        1_684_640L,
        2_284_640L,
        3_084_640L,
        4_149_640L,
        5_559_640L,
        7_459_640L,
        9_959_640L,
        13_259_640L,
        17_559_640L,
        23_159_640L,
        30_359_640L,
        39_559_640L,
        51_559_640L,
        66_559_640L,
        85_559_640L,
        109_559_640L,
        139_559_640L,
        177_559_640L,
        225_559_640L,
        285_559_640L,
        360_559_640L,
        453_559_640L,
        569_809_640L
    };

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private final List<ClickRegion> clickRegions = new ArrayList<>();
    private final String initialUsername;
    private final EnumMap<DungeonClass, Integer> classPerks = new EnumMap<>(DungeonClass.class);
    private EditBox usernameBox;
    private LoadState loadState = LoadState.IDLE;
    private String statusMessage = "Optional: load a player to autofill stats.";
    private PlayerData loadedPlayer;
    private int selectedProfileIndex;
    private FloorOption selectedFloor = FloorOption.M7;
    private boolean expertRing = true;
    private int hecatombLevel = 10;
    private ScarfAccessory scarfAccessory = ScarfAccessory.GRIMOIRE;
    private double globalBoost;
    private int graduateLevel = 10;
    private int targetCatacombsLevel = 50;
    private MayorBoost manualMayorBoost;
    private boolean autoLoadStarted;
    private boolean copiedClassAverage;
    private boolean copiedCatacombs;
    private int verticalScroll;

    public CatacombsCalculatorScreen(String username) {
        super(Component.literal("Kung Catacombs Calculator"));
        this.initialUsername = username == null ? "" : username.trim();
        for (DungeonClass dungeonClass : CLASSES) {
            classPerks.put(dungeonClass, 5);
        }
    }

    @Override
    protected void init() {
        String name = initialUsername;
        if (name.isBlank() && Minecraft.getInstance().player != null) {
            name = Minecraft.getInstance().player.getName().getString();
        }
        usernameBox = new EditBox(font, 0, 0, Component.literal("Username"));
        usernameBox.setMaxLength(16);
        usernameBox.setValue(name);
        usernameBox.setTextShadow(true);
        layoutUsernameBox();
        if (!autoLoadStarted && !name.isBlank()) {
            autoLoadStarted = true;
            loadPlayer(name);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        verticalScroll = Math.clamp(verticalScroll, 0, maxVerticalScroll());
        clickRegions.clear();
        layoutUsernameBox();
        fill(graphics, 0, 0, width, height, BACKDROP);
        drawTopBar(graphics, mouseX, mouseY, partialTick);

        int contentTop = 78 - verticalScroll;
        int leftWidth = Math.min(340, Math.max(260, width / 3));
        int rightX = GAP + leftWidth + GAP;
        int rightWidth = width - rightX - GAP;
        if (rightWidth < 300) {
            leftWidth = Math.max(240, width - 320 - GAP * 3);
            rightX = GAP + leftWidth + GAP;
            rightWidth = width - rightX - GAP;
        }
        drawLeftPanel(graphics, GAP, contentTop, leftWidth, mouseX, mouseY);
        drawRightPanel(graphics, rightX, contentTop, Math.max(260, rightWidth), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = toGuiX(event.x());
        int mouseY = toGuiY(event.y());
        if (usernameBox != null) {
            MouseButtonEvent guiEvent = new MouseButtonEvent(mouseX, mouseY, event.buttonInfo());
            if (inside(mouseX, mouseY, usernameBox.getX(), usernameBox.getY(), usernameBox.getWidth(), usernameBox.getHeight())) {
                usernameBox.mouseClicked(guiEvent, doubleClick);
                return true;
            }
            usernameBox.setFocused(false);
        }
        for (int index = clickRegions.size() - 1; index >= 0; index--) {
            ClickRegion region = clickRegions.get(index);
            if (inside(mouseX, mouseY, region.x(), region.y(), region.width(), region.height())) {
                region.action().click(mouseX, mouseY, event.button());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int direction = scrollY < 0 ? 1 : -1;
        verticalScroll = Math.clamp(verticalScroll + direction * SCROLL_STEP, 0, maxVerticalScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            onClose();
            return true;
        }
        if (usernameBox != null && usernameBox.isFocused()) {
            if (event.key() == 257 || event.key() == 335) {
                loadPlayer(usernameBox.getValue());
                return true;
            }
            return usernameBox.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (usernameBox != null && usernameBox.isFocused()) {
            return usernameBox.charTyped(event);
        }
        return super.charTyped(event);
    }

    private void drawTopBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        String title = "Kung Catacombs Calculator";
        graphics.text(font, title, width / 2 - font.width(title) / 2, 8, TEXT, true);
        graphics.text(font, "Username:", usernameBox.getX() - 58, TOP + 5, TEXT, true);
        usernameBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawButton(graphics, usernameBox.getX() + usernameBox.getWidth() + 8, TOP, 54, 20, "Load", mouseX, mouseY,
            () -> loadPlayer(usernameBox.getValue()));
        drawStatus(graphics, usernameBox.getX(), TOP + 24);
    }

    private void drawStatus(GuiGraphicsExtractor graphics, int x, int y) {
        int color = switch (loadState) {
            case LOADED -> GREEN;
            case ERROR -> RED;
            case LOADING -> GOLD;
            default -> MUTED;
        };
        graphics.text(font, trim(statusMessage, Math.min(420, width - x - GAP)), x, y, color, true);
    }

    private void drawLeftPanel(GuiGraphicsExtractor graphics, int x, int y, int panelWidth, int mouseX, int mouseY) {
        drawPanel(graphics, x, y, panelWidth, CONTENT_HEIGHT);
        int lineY = y + 18;
        drawHeading(graphics, "Dungeon levels", x + 12, lineY);
        lineY += 28;
        ProfileData profile = selectedProfile();
        if (profile == null) {
            drawMuted(graphics, "Load a player to fill this side.", x + 14, lineY);
            lineY += 34;
        } else {
            if (loadedPlayer != null && loadedPlayer.profiles().size() > 1) {
                lineY += drawProfileButtons(graphics, x + 14, lineY, panelWidth - 28, mouseX, mouseY) + 8;
            }
            drawLevelBar(graphics, x + 14, lineY, panelWidth - 28, "Catacombs", profile.cataXp(), true);
            lineY += 42;
            for (DungeonClass dungeonClass : CLASSES) {
                drawLevelBar(graphics, x + 14, lineY, panelWidth - 28, dungeonClass.label(), profile.classXp(dungeonClass), false);
                lineY += 42;
            }
            drawKV(graphics, x + 14, lineY, panelWidth - 28, "Class Average", decimal(classAverage(profile)), TEXT);
            lineY += ROW;
            drawKV(graphics, x + 14, lineY, panelWidth - 28, "Daily runs remaining", String.valueOf(Math.max(0, 5 - profile.dailyRuns())), TEXT);
            lineY += ROW + 13;
        }

        drawHeading(graphics, "Dungeon stats", x + 12, lineY);
        lineY += 24;
        if (profile != null) {
            drawKV(graphics, x + 14, lineY, panelWidth - 28, "Profile", selectedProfileName(), TEXT);
            lineY += ROW;
            drawKV(graphics, x + 14, lineY, panelWidth - 28, "Secrets found", format(profile.secrets()), TEXT);
            lineY += ROW;
            drawKV(graphics, x + 14, lineY, panelWidth - 28, "Journals completed", profile.journals() + " / 25", TEXT);
            lineY += ROW;
            drawKV(graphics, x + 14, lineY, panelWidth - 28, "Selected class", classLabel(profile.selectedClass()), TEXT);
            lineY += ROW;
            drawKV(graphics, x + 14, lineY, panelWidth - 28, "Total runs", format(profile.catacombs().totalCompletions()), TEXT);
            lineY += ROW + 13;
        } else {
            drawMuted(graphics, "...", x + 14, lineY);
            lineY += 38;
        }

        drawHeading(graphics, "Floor stats", x + 12, lineY);
        lineY += 24;
        FloorStats stats = profile == null ? null : selectedFloor.masterMode() ? profile.master() : profile.catacombs();
        String floorNumber = selectedFloor.floorNumber();
        drawKV(graphics, x + 14, lineY, panelWidth - 28, "Best score", stats == null ? "..." : floorStat(stats.bestScore(), floorNumber), TEXT);
        lineY += ROW;
        drawKV(graphics, x + 14, lineY, panelWidth - 28, "Personal best (S+)", stats == null ? "..." : duration(stats.sPlus(), floorNumber), TEXT);
        lineY += ROW;
        drawKV(graphics, x + 14, lineY, panelWidth - 28, "Personal best (S)", stats == null ? "..." : duration(stats.s(), floorNumber), TEXT);
        lineY += ROW;
        drawKV(graphics, x + 14, lineY, panelWidth - 28, "Completions", stats == null ? "..." : format((long) stats.value(stats.completions(), floorNumber)), TEXT);
    }

    private void drawRightPanel(GuiGraphicsExtractor graphics, int x, int y, int panelWidth, int mouseX, int mouseY) {
        drawPanel(graphics, x, y, panelWidth, CONTENT_HEIGHT);
        int lineY = y + 18;
        drawHeading(graphics, "Milestone calculations", x + 12, lineY);
        lineY += 24;
        drawMuted(graphics, "Values update instantly. Mayor boost follows the current election unless changed here.", x + 12, lineY);
        lineY += 28;

        int columnWidth = (panelWidth - 36) / 2;
        int leftX = x + 14;
        int rightX = leftX + columnWidth + 10;
        int rowY = lineY;
        drawControl(graphics, leftX, rowY, columnWidth, "Floor", selectedFloor.label(), mouseX, mouseY,
            () -> selectedFloor = selectedFloor.previous(),
            () -> selectedFloor = selectedFloor.next());
        drawControl(graphics, rightX, rowY, columnWidth, "Catacombs Expert Ring", expertRing ? "Yes" : "No", mouseX, mouseY,
            () -> expertRing = !expertRing,
            () -> expertRing = !expertRing);
        rowY += 38;
        drawControl(graphics, leftX, rowY, columnWidth, "Hecatomb", hecatombLabel(), mouseX, mouseY,
            () -> hecatombLevel = Math.clamp(hecatombLevel - 1, 0, 10),
            () -> hecatombLevel = Math.clamp(hecatombLevel + 1, 0, 10));
        drawControl(graphics, rightX, rowY, columnWidth, "Scarf accessory", scarfAccessory.label(), mouseX, mouseY,
            () -> scarfAccessory = scarfAccessory.previous(),
            () -> scarfAccessory = scarfAccessory.next());
        rowY += 38;
        drawControl(graphics, leftX, rowY, columnWidth, "Global boost", boostLabel(globalBoost), mouseX, mouseY,
            () -> globalBoost = cycleGlobalBoost(-1),
            () -> globalBoost = cycleGlobalBoost(1));
        MayorBoost mayorBoost = currentMayorBoost();
        drawControl(graphics, rightX, rowY, columnWidth, "Mayor boost", mayorLabel(mayorBoost), mouseX, mouseY,
            () -> manualMayorBoost = mayorBoost.previous(),
            () -> manualMayorBoost = mayorBoost.next());
        rowY += 38;
        drawControl(graphics, leftX, rowY, columnWidth, "Catacombs Graduate", romanLevel(graduateLevel) + " (" + (graduateLevel * 2) + "%)", mouseX, mouseY,
            () -> graduateLevel = Math.clamp(graduateLevel - 1, 0, 10),
            () -> graduateLevel = Math.clamp(graduateLevel + 1, 0, 10));
        drawControl(graphics, rightX, rowY, columnWidth, "Target Catacombs level", String.valueOf(targetCatacombsLevel), mouseX, mouseY,
            () -> targetCatacombsLevel = Math.clamp(targetCatacombsLevel - 1, 1, 50),
            () -> targetCatacombsLevel = Math.clamp(targetCatacombsLevel + 1, 1, 50));
        rowY += 44;

        drawMuted(graphics, "Class XP boost perks (Essence shop, each level +2%)", leftX, rowY);
        rowY += 18;
        for (DungeonClass dungeonClass : CLASSES) {
            int perkLevel = classPerks.getOrDefault(dungeonClass, 0);
            drawInlineControl(graphics, leftX, rowY, panelWidth - 28,
                dungeonClass.perkName() + " (" + dungeonClass.label() + " XP)",
                romanLevel(perkLevel) + " (" + (perkLevel * 2) + "%)",
                mouseX,
                mouseY,
                () -> classPerks.put(dungeonClass, Math.clamp(perkLevel - 1, 0, 5)),
                () -> classPerks.put(dungeonClass, Math.clamp(perkLevel + 1, 0, 5)));
            rowY += 29;
        }
        rowY += 10;

        Calculation calculation = calculate();
        int resultHeight = 178;
        fill(graphics, leftX, rowY, x + panelWidth - 14, rowY + resultHeight, PANEL_DARK);
        int resultY = rowY + 10;
        drawKV(graphics, leftX + 12, resultY, panelWidth - 52, "Catacombs XP per run", format(calculation.cataPerRun()), GOLD);
        resultY += ROW;
        drawKV(graphics, leftX + 12, resultY, panelWidth - 52, "Class XP per run (average)", format(Math.round(calculation.averageClassPerRun())), GOLD);
        resultY += ROW + 5;
        drawKV(graphics, leftX + 12, resultY, panelWidth - 52, "Runs to Catacombs " + calculation.targetLevel(), runsLabel(calculation.runsToCatacombs()), TEXT);
        resultY += ROW;
        Breakdown breakdown = calculation.breakdown();
        for (DungeonClass dungeonClass : CLASSES) {
            drawKV(graphics, leftX + 12, resultY, panelWidth - 52, "Runs to " + dungeonClass.label() + " 50",
                breakdown == null ? "..." : runsLabel(breakdown.perClass(dungeonClass)), TEXT);
            resultY += 15;
        }
        drawKV(graphics, leftX + 12, resultY, panelWidth - 52, "Runs to Class Average 50",
            breakdown == null ? "..." : runsLabel(breakdown.total()), TEXT);

        rowY += resultHeight + 14;
        String classAverageSummary = classAverageSummaryLine(calculation);
        rowY += drawWrappedText(graphics, classAverageSummary, leftX, rowY, panelWidth - 28, MUTED, 3) + 8;
        drawButton(graphics, x + panelWidth - 86, rowY, 72, 20, copiedClassAverage ? "Copied!" : "Copy", mouseX, mouseY,
            () -> copyClassAverageSummary(calculation));
        rowY += 28;
        String catacombsSummary = catacombsSummaryLine(calculation);
        rowY += drawWrappedText(graphics, catacombsSummary, leftX, rowY, panelWidth - 28, MUTED, 2) + 8;
        drawButton(graphics, x + panelWidth - 86, rowY, 72, 20, copiedCatacombs ? "Copied!" : "Copy", mouseX, mouseY,
            () -> copyCatacombsSummary(calculation));
        rowY += 28;
        if (manualMayorBoost != null) {
            drawButton(graphics, leftX, rowY, 78, 20, "Auto mayor", mouseX, mouseY, () -> manualMayorBoost = null);
        }
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int panelWidth, int panelHeight) {
        fill(graphics, x - 2, y - 2, x + panelWidth + 2, y + panelHeight + 2, BORDER);
        fill(graphics, x, y, x + panelWidth, y + panelHeight, PANEL);
    }

    private int drawProfileButtons(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int panelWidth,
        int mouseX,
        int mouseY
    ) {
        int cursorX = x;
        int cursorY = y;
        int buttonHeight = 18;
        for (int index = 0; index < loadedPlayer.profiles().size(); index++) {
            ProfileData profile = loadedPlayer.profiles().get(index);
            String label = profile.selected() ? "* " + profile.cuteName() : profile.cuteName();
            int buttonWidth = Math.min(panelWidth, Math.max(50, font.width(label) + 14));
            if (cursorX > x && cursorX + buttonWidth > x + panelWidth) {
                cursorX = x;
                cursorY += buttonHeight + 4;
            }
            boolean selected = index == selectedProfileIndex;
            boolean hovered = inside(mouseX, mouseY, cursorX, cursorY, buttonWidth, buttonHeight);
            fill(graphics, cursorX, cursorY, cursorX + buttonWidth, cursorY + buttonHeight,
                selected ? CONTROL_HOVER : hovered ? PANEL_SOFT : PANEL_DARK);
            String shown = trim(label, buttonWidth - 8);
            graphics.text(font, shown, cursorX + buttonWidth / 2 - font.width(shown) / 2, cursorY + 5,
                selected ? GOLD : TEXT, true);
            int profileIndex = index;
            addClickRegion(cursorX, cursorY, buttonWidth, buttonHeight, (clickX, clickY, button) -> {
                if (button == 0) {
                    selectedProfileIndex = profileIndex;
                    applyProfilePerks();
                    copiedClassAverage = false;
                    copiedCatacombs = false;
                }
            });
            cursorX += buttonWidth + 5;
        }
        return cursorY + buttonHeight - y;
    }

    private void drawHeading(GuiGraphicsExtractor graphics, String text, int x, int y) {
        graphics.text(font, text, x, y, TEXT, true);
    }

    private void drawMuted(GuiGraphicsExtractor graphics, String text, int x, int y) {
        graphics.text(font, trim(text, width - x - GAP), x, y, MUTED, true);
    }

    private void drawLevelBar(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int barWidth,
        String label,
        double xp,
        boolean highlight
    ) {
        double level = levelFromXp(xp);
        String value = decimal(level);
        graphics.text(font, label, x, y, highlight ? TEXT : classColor(label), true);
        graphics.text(font, value, x + barWidth - font.width(value), y, TEXT, true);
        int barY = y + 13;
        fill(graphics, x, barY, x + barWidth, barY + 5, PANEL_DARK);
        double progress = level >= 50.0 ? 1.0 : level - Math.floor(level);
        int fillWidth = (int) Math.round(barWidth * Math.clamp(progress, 0.0, 1.0));
        fill(graphics, x, barY, x + fillWidth, barY + 5, highlight ? GOLD : BLUE);
        String xpLabel = compactXp(xp) + " XP";
        graphics.text(font, xpLabel, x + barWidth - font.width(xpLabel), barY + 8, MUTED, true);
    }

    private void drawKV(GuiGraphicsExtractor graphics, int x, int y, int rowWidth, String label, String value, int valueColor) {
        graphics.text(font, trim(label, Math.max(20, rowWidth - 80)), x, y, MUTED, true);
        String shown = trim(value, Math.max(40, rowWidth / 2));
        graphics.text(font, shown, x + rowWidth - font.width(shown), y, valueColor, true);
    }

    private void drawControl(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int controlWidth,
        String label,
        String value,
        int mouseX,
        int mouseY,
        Runnable previous,
        Runnable next
    ) {
        graphics.text(font, trim(label, controlWidth - 8), x, y, TEXT, true);
        int boxY = y + 12;
        fill(graphics, x, boxY, x + controlWidth, boxY + CONTROL_HEIGHT, CONTROL);
        boolean leftHover = inside(mouseX, mouseY, x, boxY, 16, CONTROL_HEIGHT);
        boolean rightHover = inside(mouseX, mouseY, x + controlWidth - 16, boxY, 16, CONTROL_HEIGHT);
        fill(graphics, x, boxY, x + 16, boxY + CONTROL_HEIGHT, leftHover ? CONTROL_HOVER : CONTROL);
        fill(graphics, x + controlWidth - 16, boxY, x + controlWidth, boxY + CONTROL_HEIGHT, rightHover ? CONTROL_HOVER : CONTROL);
        graphics.text(font, "<", x + 5, boxY + 4, TEXT, true);
        graphics.text(font, ">", x + controlWidth - 11, boxY + 4, TEXT, true);
        String shown = trim(value, controlWidth - 38);
        graphics.text(font, shown, x + controlWidth / 2 - font.width(shown) / 2, boxY + 4, TEXT, true);
        addClickRegion(x, boxY, controlWidth / 2, CONTROL_HEIGHT, (clickX, clickY, button) -> previous.run());
        addClickRegion(x + controlWidth / 2, boxY, controlWidth - controlWidth / 2, CONTROL_HEIGHT, (clickX, clickY, button) -> next.run());
    }

    private void drawInlineControl(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int rowWidth,
        String label,
        String value,
        int mouseX,
        int mouseY,
        Runnable previous,
        Runnable next
    ) {
        int controlWidth = Math.min(150, Math.max(104, rowWidth / 4));
        int controlX = x + rowWidth - controlWidth;
        graphics.text(font, trim(label, Math.max(20, rowWidth - controlWidth - 12)), x, y + 5, TEXT, true);
        fill(graphics, controlX, y + 1, controlX + controlWidth, y + 1 + CONTROL_HEIGHT, CONTROL);
        boolean leftHover = inside(mouseX, mouseY, controlX, y + 1, 16, CONTROL_HEIGHT);
        boolean rightHover = inside(mouseX, mouseY, controlX + controlWidth - 16, y + 1, 16, CONTROL_HEIGHT);
        fill(graphics, controlX, y + 1, controlX + 16, y + 1 + CONTROL_HEIGHT, leftHover ? CONTROL_HOVER : CONTROL);
        fill(graphics, controlX + controlWidth - 16, y + 1, controlX + controlWidth, y + 1 + CONTROL_HEIGHT,
            rightHover ? CONTROL_HOVER : CONTROL);
        graphics.text(font, "<", controlX + 5, y + 5, TEXT, true);
        graphics.text(font, ">", controlX + controlWidth - 11, y + 5, TEXT, true);
        String shown = trim(value, controlWidth - 38);
        graphics.text(font, shown, controlX + controlWidth / 2 - font.width(shown) / 2, y + 5, TEXT, true);
        addClickRegion(controlX, y + 1, controlWidth / 2, CONTROL_HEIGHT, (clickX, clickY, button) -> previous.run());
        addClickRegion(controlX + controlWidth / 2, y + 1, controlWidth - controlWidth / 2, CONTROL_HEIGHT,
            (clickX, clickY, button) -> next.run());
    }

    private int drawWrappedText(
        GuiGraphicsExtractor graphics,
        String text,
        int x,
        int y,
        int maxWidth,
        int color,
        int maxLines
    ) {
        List<String> lines = wrappedLines(text, maxWidth, maxLines);
        int cursorY = y;
        for (String line : lines) {
            graphics.text(font, line, x, cursorY, color, true);
            cursorY += 12;
        }
        return Math.max(0, cursorY - y);
    }

    private List<String> wrappedLines(String text, int maxWidth, int maxLines) {
        if (text == null || text.isBlank() || maxLines <= 0) {
            return List.of();
        }
        ArrayList<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.width(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            current.append(word);
            if (lines.size() == maxLines - 1) {
                break;
            }
        }
        if (!current.isEmpty() && lines.size() < maxLines) {
            lines.add(trim(current.toString(), maxWidth));
        }
        if (lines.size() == maxLines && font.width(lines.getLast()) > maxWidth) {
            lines.set(maxLines - 1, trim(lines.getLast(), maxWidth));
        }
        return List.copyOf(lines);
    }

    private void drawButton(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int buttonWidth,
        int buttonHeight,
        String label,
        int mouseX,
        int mouseY,
        Runnable action
    ) {
        boolean hovered = inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
        fill(graphics, x, y, x + buttonWidth, y + buttonHeight, hovered ? CONTROL_HOVER : CONTROL);
        String shown = trim(label, buttonWidth - 6);
        graphics.text(font, shown, x + buttonWidth / 2 - font.width(shown) / 2, y + 6, 0xFF000000, false);
        addClickRegion(x, y, buttonWidth, buttonHeight, (clickX, clickY, button) -> {
            if (button == 0) {
                action.run();
            }
        });
    }

    private void loadPlayer(String username) {
        String name = username == null ? "" : username.trim();
        if (name.isBlank()) {
            loadState = LoadState.ERROR;
            statusMessage = "Enter a username first.";
            return;
        }
        loadState = LoadState.LOADING;
        statusMessage = "Loading " + name + "...";
        copiedClassAverage = false;
        copiedCatacombs = false;
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                PIXELSTATS_DUNGEON_API
                    + "?username="
                    + URLEncoder.encode(name, StandardCharsets.UTF_8)
                    + "&_kungFresh="
                    + System.currentTimeMillis()
            ))
            .timeout(Duration.ofSeconds(12))
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache, no-store, max-age=0")
            .header("Pragma", "no-cache")
            .header("User-Agent", "Kung-CatacombsCalculator")
            .GET()
            .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete((response, throwable) -> Minecraft.getInstance().execute(() -> {
                if (throwable != null) {
                    loadState = LoadState.ERROR;
                    statusMessage = "API error: " + shortError(throwable);
                    KungMod.LOGGER.warn("Failed to load PixelStats dungeon data.", throwable);
                    return;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    loadState = LoadState.ERROR;
                    statusMessage = "API returned HTTP " + response.statusCode();
                    return;
                }
                try {
                    loadedPlayer = parsePlayer(response.body());
                    selectedProfileIndex = Math.max(0, loadedPlayer.selectedIndex());
                    applyProfilePerks();
                    loadState = LoadState.LOADED;
                    statusMessage = "Loaded " + loadedPlayer.name() + " on " + selectedProfileName() + ".";
                } catch (RuntimeException exception) {
                    loadState = LoadState.ERROR;
                    statusMessage = "Could not read calculator data.";
                    KungMod.LOGGER.warn("Failed to parse PixelStats dungeon data.", exception);
                }
            }));
    }

    private PlayerData parsePlayer(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        String name = string(root, "name", usernameBox == null ? "" : usernameBox.getValue());
        ArrayList<ProfileData> profiles = new ArrayList<>();
        JsonElement profilesElement = root.get("profiles");
        if (profilesElement != null && profilesElement.isJsonArray()) {
            for (JsonElement element : profilesElement.getAsJsonArray()) {
                if (element != null && element.isJsonObject()) {
                    profiles.add(parseProfile(element.getAsJsonObject()));
                }
            }
        }
        if (profiles.isEmpty()) {
            throw new IllegalArgumentException("no profiles");
        }
        int selected = 0;
        for (int index = 0; index < profiles.size(); index++) {
            if (profiles.get(index).selected()) {
                selected = index;
                break;
            }
        }
        return new PlayerData(name, profiles, selected);
    }

    private ProfileData parseProfile(JsonObject object) {
        JsonObject data = objectMember(object, "data");
        EnumMap<DungeonClass, Double> classXp = new EnumMap<>(DungeonClass.class);
        EnumMap<DungeonClass, Integer> perks = new EnumMap<>(DungeonClass.class);
        JsonObject classXpObject = objectMember(data, "classXp");
        JsonObject perksObject = objectMember(data, "classPerks");
        for (DungeonClass dungeonClass : CLASSES) {
            classXp.put(dungeonClass, number(classXpObject, dungeonClass.id(), 0.0));
            perks.put(dungeonClass, (int) Math.round(number(perksObject, dungeonClass.id(), 0.0)));
        }
        return new ProfileData(
            string(object, "id", ""),
            string(object, "cuteName", "Profile"),
            bool(object, "selected"),
            number(data, "cataXp", 0.0),
            classXp,
            perks,
            classFromId(string(data, "selectedClass", "")),
            (long) number(data, "secrets", 0.0),
            (int) number(data, "dailyRuns", 0.0),
            (int) number(data, "journals", 0.0),
            parseFloorStats(objectMember(data, "catacombs")),
            parseFloorStats(objectMember(data, "master"))
        );
    }

    private FloorStats parseFloorStats(JsonObject object) {
        return new FloorStats(
            parseIntMap(objectMember(object, "bestScore")),
            parseIntMap(objectMember(object, "completions")),
            parseIntMap(objectMember(object, "sPlus")),
            parseIntMap(objectMember(object, "s"))
        );
    }

    private Map<String, Integer> parseIntMap(JsonObject object) {
        if (object == null) {
            return Map.of();
        }
        java.util.HashMap<String, Integer> values = new java.util.HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                values.put(entry.getKey(), (int) Math.round(entry.getValue().getAsDouble()));
            }
        }
        return Map.copyOf(values);
    }

    private void applyProfilePerks() {
        ProfileData profile = selectedProfile();
        if (profile == null) {
            return;
        }
        for (DungeonClass dungeonClass : CLASSES) {
            classPerks.put(dungeonClass, Math.clamp(profile.classPerk(dungeonClass), 0, 5));
        }
    }

    private Calculation calculate() {
        double baseXp = selectedFloor.baseXp();
        int maxRuns = baseXp >= 15_000.0 ? 26 : baseXp == 4_880.0 ? 51 : 76;
        double hecatomb = HECATOMB_BONUSES[Math.clamp(hecatombLevel, 0, HECATOMB_BONUSES.length - 1)];
        MayorBoost mayor = currentMayorBoost();
        double cataMultiplier;
        if (expertRing && mayor.multiplier() > 1.0) {
            cataMultiplier = 0.95 + (mayor.multiplier() - 1.0 + (maxRuns - 1) / 100.0)
                + 0.1 + hecatomb + (maxRuns - 1) * (0.024 + hecatomb / 50.0);
        } else if (expertRing) {
            cataMultiplier = 0.95 + 0.1 + hecatomb + (maxRuns - 1) * (0.024 + hecatomb / 50.0);
        } else {
            cataMultiplier = 0.95 + hecatomb + (maxRuns - 1) * (0.022 + hecatomb / 50.0);
        }
        long cataPerRun = (long) Math.ceil(baseXp * cataMultiplier * (1.0 + globalBoost));

        EnumMap<DungeonClass, Double> classXpPerRun = new EnumMap<>(DungeonClass.class);
        for (DungeonClass dungeonClass : CLASSES) {
            double perk = classPerks.getOrDefault(dungeonClass, 0) * 0.02;
            double value = baseXp * (1.0 + 2.0 * hecatomb + perk + scarfAccessory.bonus()
                + graduateLevel * 0.02 + globalBoost) * mayor.multiplier();
            classXpPerRun.put(dungeonClass, value);
        }
        double average = 0.0;
        for (DungeonClass dungeonClass : CLASSES) {
            average += classXpPerRun.get(dungeonClass);
        }
        average /= CLASSES.length;

        ProfileData profile = selectedProfile();
        Long runsToCatacombs = null;
        Breakdown breakdown = null;
        if (profile != null) {
            double remaining = xpForLevel(targetCatacombsLevel) - profile.cataXp();
            runsToCatacombs = remaining <= 0.0 ? 0L : (long) Math.ceil(remaining / cataPerRun);
            breakdown = calculateBreakdown(profile.classXp(), classXpPerRun);
        }
        return new Calculation(cataPerRun, average, targetCatacombsLevel, runsToCatacombs, breakdown);
    }

    private Breakdown calculateBreakdown(
        Map<DungeonClass, Double> currentClassXp,
        Map<DungeonClass, Double> classXpPerRun
    ) {
        EnumMap<DungeonClass, Double> remaining = new EnumMap<>(DungeonClass.class);
        EnumMap<DungeonClass, Long> perClass = new EnumMap<>(DungeonClass.class);
        double totalRemaining = 0.0;
        for (DungeonClass dungeonClass : CLASSES) {
            double xp = Math.max(0.0, xpForLevel(50) - currentClassXp.getOrDefault(dungeonClass, 0.0));
            remaining.put(dungeonClass, xp);
            perClass.put(dungeonClass, 0L);
            totalRemaining += xp;
        }
        if (totalRemaining <= 0.0) {
            return new Breakdown(0L, perClass);
        }
        for (DungeonClass dungeonClass : CLASSES) {
            if (remaining.get(dungeonClass) > 0.0 && classXpPerRun.getOrDefault(dungeonClass, 0.0) <= 0.0) {
                for (DungeonClass classKey : CLASSES) {
                    perClass.put(classKey, remaining.get(classKey) > 0.0 ? Long.MAX_VALUE : 0L);
                }
                return new Breakdown(Long.MAX_VALUE, perClass);
            }
        }

        long low = 0L;
        long high = 0L;
        for (DungeonClass dungeonClass : CLASSES) {
            double xpPerRun = classXpPerRun.getOrDefault(dungeonClass, 0.0);
            if (xpPerRun > 0.0) {
                high += (long) Math.ceil(remaining.get(dungeonClass) / xpPerRun);
            }
        }
        high = Math.max(1L, high);
        while (requiredRunCount(remaining, classXpPerRun, high) > high && high < Long.MAX_VALUE / 2L) {
            high *= 2L;
        }

        while (low < high) {
            long mid = low + (high - low) / 2L;
            if (requiredRunCount(remaining, classXpPerRun, mid) <= mid) {
                high = mid;
            } else {
                low = mid + 1L;
            }
        }

        perClass = requiredRunsForTotal(remaining, classXpPerRun, low);
        long assigned = 0L;
        for (long runs : perClass.values()) {
            assigned += runs;
        }
        while (assigned < low) {
            DungeonClass target = extraRunTarget(remaining, classXpPerRun, perClass);
            perClass.put(target, perClass.get(target) + 1L);
            assigned++;
        }
        return new Breakdown(low, perClass);
    }

    private long requiredRunCount(
        Map<DungeonClass, Double> remaining,
        Map<DungeonClass, Double> classXpPerRun,
        long totalRuns
    ) {
        long total = 0L;
        for (long runs : requiredRunsForTotal(remaining, classXpPerRun, totalRuns).values()) {
            total += runs;
        }
        return total;
    }

    private EnumMap<DungeonClass, Long> requiredRunsForTotal(
        Map<DungeonClass, Double> remaining,
        Map<DungeonClass, Double> classXpPerRun,
        long totalRuns
    ) {
        EnumMap<DungeonClass, Long> runs = new EnumMap<>(DungeonClass.class);
        for (DungeonClass dungeonClass : CLASSES) {
            double xpPerRun = classXpPerRun.getOrDefault(dungeonClass, 0.0);
            double xpLeftAfterTeamBonus = remaining.getOrDefault(dungeonClass, 0.0) - xpPerRun * totalRuns / 4.0;
            long selectedRuns = xpLeftAfterTeamBonus <= 0.0001 || xpPerRun <= 0.0
                ? 0L
                : (long) Math.ceil(xpLeftAfterTeamBonus / (xpPerRun * 0.75) - 0.0000001);
            runs.put(dungeonClass, Math.max(0L, selectedRuns));
        }
        return runs;
    }

    private DungeonClass extraRunTarget(
        Map<DungeonClass, Double> remaining,
        Map<DungeonClass, Double> classXpPerRun,
        Map<DungeonClass, Long> perClass
    ) {
        boolean hasRequiredClass = false;
        for (long runs : perClass.values()) {
            if (runs > 0L) {
                hasRequiredClass = true;
                break;
            }
        }

        DungeonClass best = CLASSES[0];
        double bestScore = Double.NEGATIVE_INFINITY;
        for (DungeonClass dungeonClass : CLASSES) {
            if (hasRequiredClass && perClass.getOrDefault(dungeonClass, 0L) <= 0L) {
                continue;
            }
            double xpPerRun = classXpPerRun.getOrDefault(dungeonClass, 1.0);
            double score = remaining.getOrDefault(dungeonClass, 0.0) / Math.max(1.0, xpPerRun);
            if (score > bestScore) {
                bestScore = score;
                best = dungeonClass;
            }
        }
        return best;
    }

    private void copyClassAverageSummary(Calculation calculation) {
        String summary = classAverageSummaryLine(calculation);
        Minecraft.getInstance().keyboardHandler.setClipboard(summary);
        copiedClassAverage = true;
        copiedCatacombs = false;
    }

    private void copyCatacombsSummary(Calculation calculation) {
        String summary = catacombsSummaryLine(calculation);
        Minecraft.getInstance().keyboardHandler.setClipboard(summary);
        copiedClassAverage = false;
        copiedCatacombs = true;
    }

    private String classAverageSummaryLine(Calculation calculation) {
        if (loadedPlayer == null || calculation.breakdown() == null) {
            return "[Kung] Load a player to calculate Class Average 50.";
        }
        Breakdown breakdown = calculation.breakdown();
        ArrayList<String> parts = new ArrayList<>();
        for (DungeonClass dungeonClass : CLASSES) {
            long runs = breakdown.perClass(dungeonClass);
            if (runs > 0L && runs < Long.MAX_VALUE) {
                parts.add(format(runs) + " " + dungeonClass.label());
            }
        }
        String classPart = parts.isEmpty() ? "0 class-specific runs" : String.join(", ", parts);
        return "[Kung] It will take " + runsLabel(breakdown.total()) + " " + selectedFloor.shortLabel()
            + " runs for " + loadedPlayer.name()
            + " to reach Class Average 50 (" + classPart + ")";
    }

    private String catacombsSummaryLine(Calculation calculation) {
        if (loadedPlayer == null || calculation.runsToCatacombs() == null) {
            return "[Kung] Load a player to calculate Catacombs 50.";
        }
        return "[Kung] It will take " + runsLabel(calculation.runsToCatacombs()) + " " + selectedFloor.shortLabel()
            + " runs for " + loadedPlayer.name()
            + " to reach Catacombs " + calculation.targetLevel();
    }

    private MayorBoost currentMayorBoost() {
        if (manualMayorBoost != null) {
            return manualMayorBoost;
        }
        double multiplier = SkyBlockMayorTracker.INSTANCE.catacombsXpMultiplier();
        if (multiplier >= 1.58) {
            return MayorBoost.AURA;
        }
        if (multiplier >= 1.49) {
            return MayorBoost.DERPY;
        }
        return MayorBoost.NONE;
    }

    private String mayorLabel(MayorBoost boost) {
        if (manualMayorBoost != null) {
            return boost.label();
        }
        return "Auto: " + SkyBlockMayorTracker.INSTANCE.catacombsXpBoostLabel();
    }

    private double cycleGlobalBoost(int direction) {
        double[] values = {0.0, 0.05, 0.10, 0.15, 0.20, 0.30};
        int current = 0;
        for (int index = 0; index < values.length; index++) {
            if (Math.abs(values[index] - globalBoost) < 0.001) {
                current = index;
                break;
            }
        }
        return values[Math.floorMod(current + direction, values.length)];
    }

    private ProfileData selectedProfile() {
        if (loadedPlayer == null || loadedPlayer.profiles().isEmpty()) {
            return null;
        }
        selectedProfileIndex = Math.clamp(selectedProfileIndex, 0, loadedPlayer.profiles().size() - 1);
        return loadedPlayer.profiles().get(selectedProfileIndex);
    }

    private String selectedProfileName() {
        ProfileData profile = selectedProfile();
        return profile == null ? "..." : profile.cuteName();
    }

    private int maxVerticalScroll() {
        return Math.max(0, CONTENT_HEIGHT + 80 - height);
    }

    private void layoutUsernameBox() {
        if (usernameBox == null) {
            return;
        }
        int inputWidth = Math.min(176, Math.max(120, width / 4));
        int x = width / 2 - inputWidth / 2 - 28;
        usernameBox.setX(x);
        usernameBox.setY(TOP);
        usernameBox.setWidth(inputWidth);
        usernameBox.setHeight(20);
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
        net.minecraft.client.Minecraft client = Minecraft.getInstance();
        int physicalSize = horizontal ? client.getWindow().getWidth() : client.getWindow().getHeight();
        int guiSize = horizontal ? client.getWindow().getGuiScaledWidth() : client.getWindow().getGuiScaledHeight();
        if (physicalSize <= 0 || guiSize <= 0) {
            return rounded;
        }
        return (int) Math.round(value * guiSize / physicalSize);
    }

    private void addClickRegion(int x, int y, int regionWidth, int regionHeight, ClickAction action) {
        clickRegions.add(new ClickRegion(x, y, regionWidth, regionHeight, action));
    }

    private String trim(String value, int maxWidth) {
        if (value == null) {
            return "";
        }
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

    private int classColor(String label) {
        DungeonClass dungeonClass = classFromLabel(label);
        return dungeonClass == null ? TEXT : dungeonClass.color();
    }

    private double classAverage(ProfileData profile) {
        double average = 0.0;
        for (DungeonClass dungeonClass : CLASSES) {
            average += levelFromXp(profile.classXp(dungeonClass));
        }
        return average / CLASSES.length;
    }

    private static double levelFromXp(double xp) {
        if (xp >= xpForLevel(50)) {
            return 50.0;
        }
        for (int level = 0; level < 50; level++) {
            long current = xpForLevel(level);
            long next = xpForLevel(level + 1);
            if (xp < next) {
                return level + Math.max(0.0, xp - current) / Math.max(1.0, next - current);
            }
        }
        return 50.0;
    }

    private static long xpForLevel(int level) {
        return CATACOMBS_XP[Math.clamp(level, 0, CATACOMBS_XP.length - 1)];
    }

    private static String floorStat(Map<String, Integer> values, String floor) {
        Integer value = values.get(floor);
        return value == null ? "..." : String.valueOf(value);
    }

    private static String duration(Map<String, Integer> values, String floor) {
        Integer millis = values.get(floor);
        if (millis == null || millis <= 0) {
            return "...";
        }
        int seconds = Math.round(millis / 1000.0f);
        return (seconds / 60) + "m " + String.format(Locale.ROOT, "%02d", seconds % 60) + "s";
    }

    private String hecatombLabel() {
        String[] roman = {"None", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        int level = Math.clamp(hecatombLevel, 0, 10);
        if (level == 0) {
            return "None";
        }
        double shown = level == 10 ? 1.0 : level * 0.1;
        return roman[level] + " (+" + String.format(Locale.ROOT, "%.1f", shown) + "%)";
    }

    private static String romanLevel(int level) {
        String[] roman = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return roman[Math.clamp(level, 0, roman.length - 1)];
    }

    private static String boostLabel(double boost) {
        return boost <= 0.0 ? "None" : "+" + Math.round(boost * 100.0) + "%";
    }

    private static String classLabel(DungeonClass dungeonClass) {
        return dungeonClass == null ? "..." : dungeonClass.label();
    }

    private static String format(long value) {
        return INTEGER_FORMAT.format(value);
    }

    private static String runsLabel(Long value) {
        if (value == null) {
            return "...";
        }
        if (value == Long.MAX_VALUE) {
            return "infinite";
        }
        return format(value);
    }

    private static String decimal(double value) {
        return String.format(Locale.GERMANY, "%.1f", value);
    }

    private static String compactXp(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000_000.0) {
            return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000.0);
        }
        if (absolute >= 1_000_000.0) {
            return String.format(Locale.ROOT, "%.2fM", value / 1_000_000.0);
        }
        if (absolute >= 1_000.0) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return format(Math.round(value));
    }

    private static JsonObject objectMember(JsonObject object, String name) {
        if (object == null || !object.has(name)) {
            return null;
        }
        JsonElement value = object.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String name, String fallback) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return fallback;
        }
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static double number(JsonObject object, String name, double fallback) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return fallback;
        }
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsDouble() : fallback;
    }

    private static boolean bool(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return false;
        }
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    private static String shortError(Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static DungeonClass classFromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (DungeonClass dungeonClass : CLASSES) {
            if (dungeonClass.id().equalsIgnoreCase(id)) {
                return dungeonClass;
            }
        }
        return null;
    }

    private static DungeonClass classFromLabel(String label) {
        for (DungeonClass dungeonClass : CLASSES) {
            if (dungeonClass.label().equals(label)) {
                return dungeonClass;
            }
        }
        return null;
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int regionWidth, int regionHeight) {
        return mouseX >= x && mouseX < x + regionWidth && mouseY >= y && mouseY < y + regionHeight;
    }

    private enum LoadState {
        IDLE,
        LOADING,
        LOADED,
        ERROR
    }

    private enum DungeonClass {
        ARCHER("archer", "Archer", "Toxophilite", 0xFFFF6B6B),
        BERSERK("berserk", "Berserk", "Unbridled Rage", 0xFFFFB86C),
        HEALER("healer", "Healer", "Heart of Gold", 0xFFFF66FF),
        MAGE("mage", "Mage", "Cold Efficiency", 0xFF66E7FF),
        TANK("tank", "Tank", "Diamond in the Rough", 0xFF8DFF73);

        private final String id;
        private final String label;
        private final String perkName;
        private final int color;

        DungeonClass(String id, String label, String perkName, int color) {
            this.id = id;
            this.label = label;
            this.perkName = perkName;
            this.color = color;
        }

        String id() {
            return id;
        }

        String label() {
            return label;
        }

        String perkName() {
            return perkName;
        }

        int color() {
            return color;
        }
    }

    private enum FloorOption {
        ENTRANCE("entrance", "Entrance", "E", "0", 55.0, false),
        F1("f1", "Floor 1", "F1", "1", 110.0, false),
        F2("f2", "Floor 2", "F2", "2", 220.0, false),
        F3("f3", "Floor 3", "F3", "3", 560.0, false),
        F4("f4", "Floor 4", "F4", "4", 1_420.0, false),
        F5("f5", "Floor 5", "F5", "5", 2_400.0, false),
        F6("f6", "Floor 6", "F6", "6", 4_880.0, false),
        F7("f7", "Floor 7", "F7", "7", 28_000.0, false),
        M1("m1", "Master 1", "M1", "1", 15_000.0, true),
        M2("m2", "Master 2", "M2", "2", 20_000.0, true),
        M3("m3", "Master 3", "M3", "3", 35_000.0, true),
        M4("m4", "Master 4", "M4", "4", 55_000.0, true),
        M5("m5", "Master 5", "M5", "5", 70_000.0, true),
        M6("m6", "Master 6", "M6", "6", 100_000.0, true),
        M7("m7", "Master 7", "M7", "7", 300_000.0, true);

        private final String id;
        private final String label;
        private final String shortLabel;
        private final String floorNumber;
        private final double baseXp;
        private final boolean masterMode;

        FloorOption(String id, String label, String shortLabel, String floorNumber, double baseXp, boolean masterMode) {
            this.id = id;
            this.label = label;
            this.shortLabel = shortLabel;
            this.floorNumber = floorNumber;
            this.baseXp = baseXp;
            this.masterMode = masterMode;
        }

        String label() {
            return label;
        }

        String shortLabel() {
            return shortLabel;
        }

        String floorNumber() {
            return floorNumber;
        }

        double baseXp() {
            return baseXp;
        }

        boolean masterMode() {
            return masterMode;
        }

        FloorOption previous() {
            FloorOption[] values = values();
            return values[Math.floorMod(ordinal() - 1, values.length)];
        }

        FloorOption next() {
            FloorOption[] values = values();
            return values[Math.floorMod(ordinal() + 1, values.length)];
        }
    }

    private enum ScarfAccessory {
        NONE("None", 0.0),
        STUDIES("Studies (+2%)", 0.02),
        THESIS("Thesis (+4%)", 0.04),
        GRIMOIRE("Grimoire (+6%)", 0.06);

        private final String label;
        private final double bonus;

        ScarfAccessory(String label, double bonus) {
            this.label = label;
            this.bonus = bonus;
        }

        String label() {
            return label;
        }

        double bonus() {
            return bonus;
        }

        ScarfAccessory previous() {
            ScarfAccessory[] values = values();
            return values[Math.floorMod(ordinal() - 1, values.length)];
        }

        ScarfAccessory next() {
            ScarfAccessory[] values = values();
            return values[Math.floorMod(ordinal() + 1, values.length)];
        }
    }

    private enum MayorBoost {
        NONE("None", 1.0),
        DERPY("Derpy (+50%)", 1.5),
        AURA("Aura (+59%)", 1.59);

        private final String label;
        private final double multiplier;

        MayorBoost(String label, double multiplier) {
            this.label = label;
            this.multiplier = multiplier;
        }

        String label() {
            return label;
        }

        double multiplier() {
            return multiplier;
        }

        MayorBoost previous() {
            MayorBoost[] values = values();
            return values[Math.floorMod(ordinal() - 1, values.length)];
        }

        MayorBoost next() {
            MayorBoost[] values = values();
            return values[Math.floorMod(ordinal() + 1, values.length)];
        }
    }

    @FunctionalInterface
    private interface ClickAction {
        void click(int mouseX, int mouseY, int button);
    }

    private record ClickRegion(int x, int y, int width, int height, ClickAction action) {
    }

    private record PlayerData(String name, List<ProfileData> profiles, int selectedIndex) {
        PlayerData {
            profiles = List.copyOf(profiles);
        }
    }

    private record ProfileData(
        String id,
        String cuteName,
        boolean selected,
        double cataXp,
        Map<DungeonClass, Double> classXp,
        Map<DungeonClass, Integer> classPerks,
        DungeonClass selectedClass,
        long secrets,
        int dailyRuns,
        int journals,
        FloorStats catacombs,
        FloorStats master
    ) {
        double classXp(DungeonClass dungeonClass) {
            return classXp.getOrDefault(dungeonClass, 0.0);
        }

        int classPerk(DungeonClass dungeonClass) {
            return classPerks.getOrDefault(dungeonClass, 0);
        }
    }

    private record FloorStats(
        Map<String, Integer> bestScore,
        Map<String, Integer> completions,
        Map<String, Integer> sPlus,
        Map<String, Integer> s
    ) {
        int value(Map<String, Integer> values, String floor) {
            return values.getOrDefault(floor, 0);
        }

        long totalCompletions() {
            return completions.getOrDefault("total", 0);
        }
    }

    private record Calculation(
        long cataPerRun,
        double averageClassPerRun,
        int targetLevel,
        Long runsToCatacombs,
        Breakdown breakdown
    ) {
    }

    private record Breakdown(long total, Map<DungeonClass, Long> perClass) {
        long perClass(DungeonClass dungeonClass) {
            return perClass.getOrDefault(dungeonClass, 0L);
        }
    }
}
