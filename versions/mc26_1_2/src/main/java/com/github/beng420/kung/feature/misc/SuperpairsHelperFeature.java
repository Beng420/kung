package com.github.beng420.kung.feature.misc;

import static com.github.beng420.kung.util.GuiDraw.fill;

import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.feature.Feature;
import com.github.beng420.kung.feature.misc.SuperpairsBoard.CardCount;
import com.github.beng420.kung.feature.misc.SuperpairsBoard.PairStats;
import com.github.beng420.kung.util.KungDebugRecorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class SuperpairsHelperFeature extends ConfigurableFeature<MiscConfig> implements Feature {
    public static final SuperpairsHelperFeature INSTANCE = new SuperpairsHelperFeature();
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 36;
    private static final int MAX_CARD_LINES = 14;
    private static final int DEBUG_LINE_COUNT = 6;
    private static final int SAMPLE_WIDTH = 170;
    private static final int PADDING_X = 5;
    private static final int PADDING_Y = 4;
    private static final int ROW_HEIGHT = 10;
    private static final int PANEL = 0xAA111318;
    private static final int BORDER = 0xBB3498DB;
    private static final int TEXT = 0xFFFFFFFF;
    private static final Pattern FORMATTING = Pattern.compile("§[0-9A-FK-ORa-fk-or]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    // Observations and rendering run on the client thread after vanilla applies packets.
    private static final SuperpairsBoard board = new SuperpairsBoard();
    private static AbstractContainerMenu activeMenu;
    private static int topSlotCount;
    private static int slotUpdatePackets;
    private static int observedSlotUpdates;
    private static long lastLoggedRevision = -1;
    private static String lastObservedSlot = "none";

    private SuperpairsHelperFeature() {
        super(config -> config.misc);
    }

    @Override
    protected void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!INSTANCE.isEnabled() || !ensureSession(client)) {
                resetState("closed-or-disabled");
                return;
            }
            recordProgress();
        });

        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!isSuperpairsGameScreen(screen)) {
                return;
            }
            KungDebugRecorder.event("superpairs", "screen title=\"" + screen.getTitle().getString()
                + "\" enabled=" + INSTANCE.isEnabled());
            ScreenEvents.afterExtract(screen).register((currentScreen, graphics, mouseX, mouseY, tickDelta) ->
                renderAfterScreen(currentScreen, graphics)
            );
        });
    }

    @Override
    public boolean isEnabled() {
        return config().superpairsHelperEnabled();
    }

    private static void renderAfterScreen(Screen screen, GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!INSTANCE.isEnabled() || screen != client.screen || !ensureSession(client)) {
            return;
        }
        drawPanel(graphics, client, board.summary(), board.cards(), INSTANCE.config());
    }

    /** Called only from the applied ClientPacketListener slot hook, never Netty receipt. */
    public static void observeSlotUpdate(int containerId, int slot, ItemStack stack) {
        if (!acceptsPacket(containerId)) {
            return;
        }
        slotUpdatePackets++;
        observeBoardStack(slot, stack);
    }

    public static void observeContentUpdate(int containerId, List<ItemStack> stacks) {
        if (!acceptsPacket(containerId)) {
            return;
        }
        slotUpdatePackets++;
        for (int slot = 0; slot < Math.min(topSlotCount, stacks.size()); slot++) {
            observeBoardStack(slot, stacks.get(slot));
        }
    }

    private static boolean acceptsPacket(int containerId) {
        Minecraft client = Minecraft.getInstance();
        return INSTANCE.isEnabled()
            && client.player != null
            && client.player.containerMenu.containerId == containerId
            && ensureSession(client);
    }

    private static boolean ensureSession(Minecraft client) {
        if (client.player == null || !isSuperpairsGameScreen(client.screen)) {
            return false;
        }
        AbstractContainerMenu menu = ((AbstractContainerScreen<?>) client.screen).getMenu();
        if (menu != client.player.containerMenu || menu.slots.size() - PLAYER_INVENTORY_SLOT_COUNT != 54) {
            return false;
        }
        // A resize retains memory. Another menu never does, even with an identical
        // title or recycled numeric container id.
        if (activeMenu != menu) {
            resetState("new-menu");
            activeMenu = menu;
            topSlotCount = menu.slots.size() - PLAYER_INVENTORY_SLOT_COUNT;
            KungDebugRecorder.event("superpairs", "start container=" + menu.containerId
                + " title=\"" + client.screen.getTitle().getString() + "\" top=" + topSlotCount);
            // One seed covers enabling during an open game. All subsequent reveals
            // come from packets, including brief flips and automatically revealed partners.
            for (int slot = 0; slot < topSlotCount; slot++) {
                observeBoardStack(slot, menu.slots.get(slot).getItem());
            }
        }
        return true;
    }

    private static boolean isSuperpairsGameScreen(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> && isGameTitle(screen.getTitle().getString());
    }

    static boolean isGameTitle(String title) {
        String normalized = cleanText(title).toLowerCase(Locale.ROOT);
        return normalized.equals("superpairs")
            || (normalized.startsWith("superpairs (") && normalized.endsWith(")"));
    }

    private static void observeBoardStack(int slot, ItemStack stack) {
        if (!SuperpairsBoard.isBoardSlot(topSlotCount, slot) || stack == null || stack.isEmpty()) {
            return;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String label = cleanName(stack);
        long previousRevision = board.revision();
        SuperpairsBoard.Kind kind = board.observe(topSlotCount, slot, itemId, label, stack.getCount());
        if (kind == SuperpairsBoard.Kind.IGNORED) {
            return;
        }
        observedSlotUpdates++;
        if (board.revision() != previousRevision && kind != SuperpairsBoard.Kind.MARKER) {
            lastObservedSlot = slot + " " + trimForDebug(label, 36);
            KungDebugRecorder.event("superpairs", "reveal container=" + activeMenu.containerId
                + " slot=" + slot + " kind=" + kind + " item=" + itemId
                + " name=\"" + label + "\" count=" + stack.getCount());
        }
    }

    private static String cleanName(ItemStack stack) {
        String name = cleanText(stack.getHoverName().getString());
        if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("enchanted_book")) {
            var lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                for (var line : lore.lines()) {
                    String enchantment = cleanText(line.getString());
                    if (!enchantment.isBlank()) {
                        return enchantment;
                    }
                }
            }
        }
        return name;
    }

    private static String cleanText(String value) {
        return value == null ? "" : WHITESPACE.matcher(FORMATTING.matcher(value).replaceAll(""))
            .replaceAll(" ").trim();
    }

    private static void recordProgress() {
        if (lastLoggedRevision == board.revision()) {
            return;
        }
        lastLoggedRevision = board.revision();
        PairStats stats = board.summary();
        KungDebugRecorder.event("superpairs", "state container=" + activeMenu.containerId
            + " board=" + board.boardSlotCount() + " bonus=" + board.bonusSlotCount()
            + " pairs=" + stats.knownPairs() + " singles=" + stats.singleCards()
            + " unseen=" + stats.unknownFields() + " maxPairs=" + stats.maximumTotalPairs());
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
        MiscConfig config
    ) {
        List<String> lines = new ArrayList<>();
        if (board.boardSlotCount() > 0) {
            String total = pairStats.exactTotalPairs() >= 0
                ? Integer.toString(pairStats.exactTotalPairs())
                : "up to " + pairStats.maximumTotalPairs();
            lines.add("Pairs: " + pairStats.knownPairs() + " found | " + total + " total");
            lines.add("Unseen Cards: " + pairStats.unknownFields() + " | Singles: " + pairStats.singleCards());
        } else {
            lines.add("Superpairs (Scanning...)");
            lines.add("Unseen Cards: ?");
        }

        int visibleCards = Math.min(cards.size(), MAX_CARD_LINES);
        for (int index = 0; index < visibleCards; index++) {
            CardCount card = cards.get(index);
            String count = card.count() <= 2 ? card.count() + "/2" : card.count() + " seen, " + card.pairs() + " pairs";
            lines.add((card.complete() ? "Known: " : "Need: ") + card.label() + " (" + count + ")");
        }
        if (config.superpairsHelperDebugEnabled()) {
            Screen screen = client.screen;
            lines.add("title: " + trimForDebug(screen.getTitle().getString().trim(), 30));
            lines.add("screen: " + screen.getClass().getSimpleName());
            lines.add("slots: top " + topSlotCount + " board " + board.boardSlotCount());
            lines.add("known: bonus " + board.bonusSlotCount() + " types " + cards.size());
            lines.add("container: " + activeMenu.containerId + " revision " + board.revision());
            lines.add("packets: " + slotUpdatePackets + " slots " + observedSlotUpdates + " last " + lastObservedSlot);
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
                graphics.text(client.font, trimToWidth(client, lines.get(index), textWidth), 0, index * ROW_HEIGHT, TEXT, true);
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
        return value.length() <= maxLength ? value : value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static void resetState(String reason) {
        if (activeMenu == null) {
            return;
        }
        recordProgress();
        KungDebugRecorder.event("superpairs", "reset container=" + activeMenu.containerId + " reason=" + reason);
        activeMenu = null;
        board.reset();
        topSlotCount = 0;
        slotUpdatePackets = 0;
        observedSlotUpdates = 0;
        lastLoggedRevision = -1;
        lastObservedSlot = "none";
    }

    public record OverlayBounds(int x, int y, int width, int height) {
    }
}
