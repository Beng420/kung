package com.github.beng420.kung.feature.misc;

import static com.github.beng420.kung.util.GuiDraw.fill;

import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.feature.Feature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class SuperpairsHelperFeature extends ConfigurableFeature<MiscConfig> implements Feature {
    public static final SuperpairsHelperFeature INSTANCE = new SuperpairsHelperFeature();
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 36;
    private static final int MAX_PLAYABLE_FIELDS = 28;
    private static final int ASSUMED_BONUS_FIELDS = 2;
    private static final int MAX_CARD_LINES = MAX_PLAYABLE_FIELDS / 2;
    private static final int SUPERPAIRS_LEAVE_GRACE_TICKS = 5;
    private static final int DEBUG_LINE_COUNT = 6;
    private static final int SAMPLE_WIDTH = 170;
    private static final int PADDING_X = 5;
    private static final int PADDING_Y = 4;
    private static final int ROW_HEIGHT = 10;
    private static final int PANEL = 0xAA111318;
    private static final int BORDER = 0xBB3498DB;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED_TEXT = 0xFFBBC4D0;

    private static final Map<Integer, CardInfo> discoveredSlots = new LinkedHashMap<>();
    private static final Map<Integer, String> discoveredBonusSlots = new LinkedHashMap<>();
    private static int boardSlotCount;
    private static boolean superpairsSessionActive;
    private static int screenLeaveGraceTicks;
    private static int slotUpdatePackets;
    private static int observedSlotUpdates;
    private static String lastObservedSlot = "none";

    private SuperpairsHelperFeature() {
        super(config -> config.misc);
    }

    @Override
    protected void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (screenLeaveGraceTicks <= 0) {
                return;
            }
            if (isSuperpairsGameOpen(client)) {
                screenLeaveGraceTicks = 0;
                return;
            }
            screenLeaveGraceTicks--;
            if (screenLeaveGraceTicks <= 0) {
                resetState();
            }
        });

        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!isSuperpairsGameScreen(screen)) {
                return;
            }

            screenLeaveGraceTicks = 0;
            if (!superpairsSessionActive) {
                resetState();
                superpairsSessionActive = true;
            }
            ScreenEvents.afterExtract(screen).register((currentScreen, graphics, mouseX, mouseY, tickDelta) ->
                renderAfterScreen(currentScreen, graphics)
            );
            ScreenEvents.remove(screen).register(removedScreen ->
                screenLeaveGraceTicks = SUPERPAIRS_LEAVE_GRACE_TICKS
            );
        });
    }

    @Override
    public boolean isEnabled() {
        return config().superpairsHelperEnabled();
    }

    private static synchronized void renderAfterScreen(Screen screen, GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!INSTANCE.isEnabled()
            || !isSuperpairsGameScreen(screen)
            || client.player == null) {
            return;
        }

        MiscConfig config = INSTANCE.config();
        SuperpairsSnapshot snapshot = readSnapshot(client);
        if (snapshot.boardSlots() > 0) {
            boardSlotCount = Math.max(boardSlotCount, snapshot.boardSlots());
        }

        // NEU (Stabil: verschwindet niemals):
        int bonusFields = discoveredBonusSlots.size();
        int totalPairs = Math.max(0, (boardSlotCount - bonusFields) / 2);

        List<CardCount> cards = discoveredCounts();
        PairStats pairStats = PairStats.of(totalPairs, cards);

        // Keep rendering while totalPairs is still 0 during the first few milliseconds.
        drawPanel(graphics, client, pairStats, cards, config, snapshot);
    }

    public static synchronized void observeSlotUpdate(int containerId, int slot, ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        slotUpdatePackets++;
        if (!INSTANCE.isEnabled()
            || !isSuperpairsGameOpen(client)
            || client.player == null
            || client.player.containerMenu.containerId != containerId
            || stack == null
            || stack.isEmpty()
            || !isBoardSlot(client.player.containerMenu, slot)) {
            return;
        }

        observedSlotUpdates++;
        lastObservedSlot = slot + " " + itemPath(stack) + " " + trimForDebug(cleanName(stack), 28);
        observeBoardStack(slot, stack);
    }

    private static boolean isSuperpairsGameOpen(Minecraft client) {
        if (client == null || client.player == null) {
            return false;
        }

        return isSuperpairsGameScreen(client.screen);
    }

    private static boolean isSuperpairsGameScreen(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return false;
        }

        String normalized = screen.getTitle().getString().trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("superpairs")
            && !normalized.contains("rewards")
            && !normalized.contains("stakes");
    }

    private static SuperpairsSnapshot readSnapshot(Minecraft client) {
        AbstractContainerMenu menu = client.player.containerMenu;
        int topSlotCount = Math.max(0, menu.slots.size() - PLAYER_INVENTORY_SLOT_COUNT);
        int boardSlots = 0;
        int markerSlots = 0;
        int visibleCardSlots = 0;
        int bonusSlots = 0;
        int ignoredSlots = 0;

        for (int index = 0; index < topSlotCount; index++) {
            Slot slot = menu.slots.get(index);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                ignoredSlots++;
                continue;
            }

            if (isBonusCard(stack)) {
                boardSlots++;
                bonusSlots++;
                observeBoardStack(index, stack);
            } else if (isBoardMarker(stack)) {
                boardSlots++;
                markerSlots++;
            } else if (isVisibleCard(stack)) {
                boardSlots++;
                visibleCardSlots++;
                observeBoardStack(index, stack);
            } else {
                ignoredSlots++;
            }
        }

        return new SuperpairsSnapshot(topSlotCount, boardSlots, markerSlots, visibleCardSlots, bonusSlots, ignoredSlots);
    }

    private static boolean isBoardSlot(AbstractContainerMenu menu, int slot) {
        int topSlotCount = Math.max(0, menu.slots.size() - PLAYER_INVENTORY_SLOT_COUNT);
        return slot >= 0 && slot < topSlotCount;
    }

    private static void observeBoardStack(int slot, ItemStack stack) {
        if (isBonusCard(stack)) {
            discoveredBonusSlots.put(slot, cleanName(stack));
        } else if (isVisibleCard(stack)) {
            discoveredSlots.put(slot, cardInfo(stack));
        }
    }

    private static boolean isBoardMarker(ItemStack stack) {
        String itemPath = itemPath(stack);
        return itemPath.endsWith("_stained_glass");
    }

    private static boolean isVisibleCard(ItemStack stack) {
        String itemPath = itemPath(stack);
        if (itemPath.contains("stained_glass") 
            || itemPath.equals("barrier") 
            || itemPath.equals("air")
            || itemPath.equals("clock") 
            || itemPath.equals("bookshelf")) {
            return false;
        }

        String name = cleanName(stack).toLowerCase(Locale.ROOT);
        if (name.isBlank()) {
            return false;
        }

        return !name.contains("superpairs")
            && !name.contains("remaining")
            && !name.contains("clicks")
            && !name.contains("timer")
            && !isBonusCard(stack);
    }

    private static boolean isBonusCard(ItemStack stack) {
        String itemPath = itemPath(stack);
        String name = cleanName(stack).toLowerCase(Locale.ROOT);
        return itemPath.equals("diamond")
            || name.contains("bonus")
            || name.contains("extra")
            || name.contains("second button")
            || name.contains("free click")
            || name.contains("instant find")
            || name.contains("reveal")
            || name.contains("shuffle");
    }

    private static CardInfo cardInfo(ItemStack stack) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String label = cleanName(stack);
        String count = stack.getCount() > 1 ? " x" + stack.getCount() : "";
        return new CardInfo(itemId + "|" + label.toLowerCase(Locale.ROOT) + count, label + count);
    }

    private static String cleanName(ItemStack stack) {
        String name = stack.getHoverName().getString().replaceAll("\\s+", " ").trim();
        
        // Include the lore enchantment so enchanted books can be distinguished reliably.
        if (itemPath(stack).equals("enchanted_book")) {
            var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
            if (lore != null && !lore.lines().isEmpty()) {
                String enchantment = lore.lines().get(0).getString().replaceAll("§[0-9a-fk-or]", "").trim();
                if (!enchantment.isBlank()) {
                    return enchantment;
                }
            }
        }

        return name;
    }

    private static String itemPath(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static List<CardCount> discoveredCounts() {
        Map<String, CardCount> counts = new LinkedHashMap<>();
        for (CardInfo card : discoveredSlots.values()) {
            CardCount existing = counts.get(card.key());
            counts.put(card.key(), existing == null
                ? new CardCount(card.label(), 1)
                : new CardCount(existing.label(), Math.min(2, existing.count() + 1)));
        }

        return counts.values().stream()
            .sorted(Comparator
                .comparingInt((CardCount card) -> card.complete() ? 1 : 0)
                .thenComparing(CardCount::label, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public static OverlayBounds overlayBounds(MiscConfig config) {
        float scale = config.superpairsHelperScale() / 100.0F;
        int sampleLines = 3 + MAX_CARD_LINES + (config.superpairsHelperDebugEnabled() ? DEBUG_LINE_COUNT : 0);
        int sampleHeight = PADDING_Y * 2 + sampleLines * ROW_HEIGHT;
        return new OverlayBounds(
            Math.round(config.superpairsHelperX() - scale),
            Math.round(config.superpairsHelperY() - scale),
            Math.round((SAMPLE_WIDTH + PADDING_X * 2 + 2) * scale),
            Math.round((sampleHeight + 2) * scale)
        );
    }

    private static void drawPanel(
        GuiGraphicsExtractor graphics,
        Minecraft client,
        PairStats pairStats,
        List<CardCount> cards,
        MiscConfig config,
        SuperpairsSnapshot snapshot
    ) {
        List<String> lines = new ArrayList<>();
        if (pairStats.totalPairs() > 0) {
            lines.add("Pairs: " + pairStats.solvedPairs() + " / " + pairStats.totalPairs()
                + " found | " + pairStats.unsolvedPairs() + " open");
            lines.add("Hidden Pairs: " + pairStats.hiddenPairs()
                + " | Singles: " + pairStats.singleCards());
        } else {
            lines.add("Superpairs (Scanning...)");
            lines.add("Hidden Pairs: ?");
        }

        int visibleCards = Math.min(cards.size(), MAX_CARD_LINES);
        for (int index = 0; index < visibleCards; index++) {
            CardCount card = cards.get(index);
            lines.add((card.complete() ? "Done: " : "Need: ") + card.label() + " (" + card.count() + "/2)");
        }
        if (config.superpairsHelperDebugEnabled()) {
            Screen screen = client.screen;
            String title = screen == null ? "none" : trimForDebug(screen.getTitle().getString().trim(), 30);
            String screenName = screen == null ? "none" : screen.getClass().getSimpleName();
            lines.add("title: " + title);
            lines.add("screen: " + screenName);
            lines.add("slots: top " + snapshot.topSlotCount() + " board " + snapshot.boardSlots() + " cache " + boardSlotCount);
            lines.add("scan: glass " + snapshot.markerSlots() + " cards " + snapshot.visibleCardSlots()
                + " bonus " + snapshot.bonusSlots() + " ignored " + snapshot.ignoredSlots());
            lines.add("state: active " + superpairsSessionActive + " grace " + screenLeaveGraceTicks
                + " pairs " + pairStats.totalPairs());
            lines.add("packets: " + observedSlotUpdates + "/" + slotUpdatePackets + " last " + lastObservedSlot);
        }

        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, client.font.width(line));
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        float scale = config.superpairsHelperScale() / 100.0F;
        int maxPanelWidth = Math.round((screenWidth - config.superpairsHelperX() * 2) / scale);
        textWidth = Math.min(textWidth, Math.max(80, maxPanelWidth - PADDING_X * 2));
        int panelHeight = PADDING_Y * 2 + lines.size() * ROW_HEIGHT;

        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(config.superpairsHelperX(), config.superpairsHelperY());
            graphics.pose().scale(scale, scale);
            fill(graphics, -PADDING_X - 1, -PADDING_Y - 1, textWidth + PADDING_X + 1, panelHeight + 1, BORDER);
            fill(graphics, -PADDING_X, -PADDING_Y, textWidth + PADDING_X, panelHeight, PANEL);
            for (int index = 0; index < lines.size(); index++) {
                int color = index == 0 && pairStats.unsolvedPairs() <= 0 ? MUTED_TEXT : TEXT;
                graphics.text(client.font, trimToWidth(client, lines.get(index), textWidth), 0, index * ROW_HEIGHT, color, true);
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static String trimToWidth(Minecraft client, String value, int maxWidth) {
        if (client.font.width(value) <= maxWidth) {
            return value;
        }

        String suffix = "...";
        for (int end = value.length(); end > 0; end--) {
            String candidate = value.substring(0, end) + suffix;
            if (client.font.width(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return suffix;
    }

    private static String trimForDebug(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static void resetState() {
        discoveredSlots.clear();
        discoveredBonusSlots.clear();
        boardSlotCount = 0;
        superpairsSessionActive = false;
        screenLeaveGraceTicks = 0;
        slotUpdatePackets = 0;
        observedSlotUpdates = 0;
        lastObservedSlot = "none";
    }

    private record SuperpairsSnapshot(
        int topSlotCount,
        int boardSlots,
        int markerSlots,
        int visibleCardSlots,
        int bonusSlots,
        int ignoredSlots
    ) {
    }

    private record CardInfo(String key, String label) {
    }

    private record PairStats(int totalPairs, int solvedPairs, int singleCards, int hiddenPairs) {
        static PairStats of(int totalPairs, List<CardCount> cards) {
            int solvedPairs = 0;
            int singleCards = 0;
            for (CardCount card : cards) {
                if (card.complete()) {
                    solvedPairs++;
                } else if (card.count() == 1) {
                    singleCards++;
                }
            }
            int hiddenPairs = Math.max(0, totalPairs - cards.size());
            return new PairStats(totalPairs, solvedPairs, singleCards, hiddenPairs);
        }

        int unsolvedPairs() {
            return Math.max(0, totalPairs - solvedPairs);
        }
    }

    private record CardCount(String label, int count) {
        boolean complete() {
            return count >= 2;
        }
    }

    public record OverlayBounds(int x, int y, int width, int height) {
    }
}
