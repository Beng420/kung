package com.github.beng420.kung.feature.garden;

import static com.github.beng420.kung.util.GuiDraw.fill;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.config.KungHudEditorScreen;
import com.github.beng420.kung.config.category.FeastConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.feature.Feature;
import com.github.beng420.kung.runtime.KungPaths;
import com.github.beng420.kung.skyblock.HypixelInstanceTracker;
import com.github.beng420.kung.skyblock.HypixelLocation;
import com.github.beng420.kung.skyblock.SkyBlockMayorTracker;
import com.github.beng420.kung.util.KungDebugRecorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;

public final class FeastOverlayFeature extends ConfigurableFeature<FeastConfig> implements Feature {
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(KungMod.MOD_ID, "feast_progress");
    private static final int WIDTH = 180;
    private static final int HEIGHT = 40;
    private static final int BAR_X = 3;
    private static final int BAR_Y = 15;
    private static final int BAR_WIDTH = WIDTH - BAR_X * 2;
    private static final int BAR_HEIGHT = 10;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int GREEN = 0xFF29B34A;
    private static final int YELLOW = 0xFFE6C63B;
    private static final int BAR_OUTLINE = 0xEE101510;
    // Illustrative editor values; live milestone boundaries always come from the menu.
    private static final FeastProgress.Snapshot EXAMPLE = new FeastProgress.Snapshot(
        FeastProgress.Kind.GRAND, 234, List.of(5, 25, 75, 150, 250, 350, 450, 550, 750));

    private final FeastSession session = new FeastSession();
    private final FeastKernels kernels = new FeastKernels();
    private final FeastContext context = new FeastContext();
    private FeastStateStore store;
    private FeastPersistence persistence;
    private long epoch = -1;
    private List<String> lastSidebar;
    private List<String> lastVisible;
    private boolean inGarden;
    private boolean inHubFarm;
    private boolean hypixel;
    private int menuTicks;
    private AbstractContainerMenu diagnosticMenu;
    private String diagnosticKey = "";
    private int diagnosticSnapshots;

    public FeastOverlayFeature() { super(config -> config.feast); }

    @Override
    protected void onInitialize() {
        store = new FeastStateStore(KungPaths.fileLayout().settingsDirectory().resolve("feast-progress.json"),
            Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "kung-feast-save");
                thread.setDaemon(true);
                return thread;
            }));
        persistence = new FeastPersistence(store, session, kernels);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        registerMessageObserver(this::observeMessage);
        HudElementRegistry.attachElementBefore(VanillaHudElements.PLAYER_LIST, HUD_ID,
            (graphics, delta) -> render(graphics));
    }

    static void registerMessageObserver(Consumer<String> observer) {
        // ALLOW_GAME calls every listener even when another mod hides the message, before MODIFY_GAME.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) observer.accept(message.getString());
            return true;
        });
    }

    private void observeMessage(String text) {
        if (!initialized() || !isEnabled() || !hypixel) return;
        if (FeastContext.profileMessage(text)) {
            context.observeProfileMessage(text);
            selectProfile();
        } else if (FeastContext.profileId(text) != null) {
            persistence.identify(FeastContext.profileId(text));
        } else if (text.contains("Seasoning") || text.contains("Kernel")) {
            updateContext();
            if (text.contains("Seasoning")) {
                boolean counted = session.donate(text);
                KungDebugRecorder.event("feast-donation", "counted=" + counted
                    + " amount=" + FeastProgress.donationAmount(text) + " event=" + session.event()
                    + " synced=" + (session.snapshot() != null) + " message=" + text);
                if (counted) recordProgress("donation");
            }
            if (kernels.observeMessage(text)) recordKernels("donation");
        }
    }

    @Override
    public boolean isEnabled() { return initialized() && config().enabled(); }

    @Override
    protected void onReset() {
        if (persistence != null) persistence.reset();
        session.reset();
        kernels.reset();
        context.reset();
        epoch = -1;
        lastSidebar = null;
        lastVisible = null;
        inGarden = false;
        inHubFarm = false;
        hypixel = false;
        menuTicks = 0;
        diagnosticMenu = null;
        diagnosticKey = "";
        diagnosticSnapshots = 0;
    }

    @Override
    protected void onShutdown() {
        if (store != null) store.flush();
        onReset();
    }

    private void tick(Minecraft client) {
        if (!initialized()) return;
        if (!isEnabled()) {
            onReset();
            return;
        }
        // A world transfer can briefly have no player; only disconnect invalidates the session.
        if (client.player == null || client.level == null) return;
        var server = client.getCurrentServer();
        hypixel = server != null && isHypixel(server.ip);
        if (!hypixel) {
            onReset();
            return;
        }
        updateContext();
        if (++menuTicks >= 5) {
            menuTicks = 0;
            observeMenu(client);
        }
    }

    private void updateContext() {
        HypixelInstanceTracker instance = HypixelInstanceTracker.INSTANCE;
        if (epoch != instance.instanceEpoch()) {
            epoch = instance.instanceEpoch();
            context.worldChanged();
            kernels.worldChanged();
            lastSidebar = null;
            lastVisible = null;
        }
        if (lastSidebar != instance.sidebarLines() || lastVisible != instance.visibleLines()) {
            lastSidebar = instance.sidebarLines();
            lastVisible = instance.visibleLines();
            context.observe(lastSidebar, lastVisible);
            selectProfile();
            if (kernels.observeSidebar(lastSidebar)) recordKernels("sidebar");
            inGarden = instance.tracking() && FeastContext.garden(instance.instanceLine(), lastVisible);
            inHubFarm = instance.tracking() && FeastContext.hubFarm(instance.instanceLine(), lastVisible);
        }
        var mayor = SkyBlockMayorTracker.INSTANCE.feastStatus();
        var previous = session.snapshot();
        session.select(context.event(mayor.active(System.currentTimeMillis()), mayor.electionYear()), context.hasDate());
        if (previous != null && session.snapshot() == null) {
            KungDebugRecorder.event("feast", "reset reason=changed-or-inactive-feast");
        }
        if (persistence != null) persistence.restoreProgress();
    }

    private void observeMenu(Minecraft client) {
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)
            || screen.getMenu() != client.player.containerMenu
            || FeastProgress.Kind.fromTitle(screen.getTitle().getString()) == null
                && !FeastKernels.supportsMenu(screen.getTitle().getString())) {
            session.closeMenu();
            kernels.closeMenu();
            diagnosticMenu = null;
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        int topSlots = menu.slots.size() - 36;
        if (topSlots <= 0 || topSlots > 54) return;
        List<FeastProgress.MenuItem> items = new ArrayList<>();
        for (int index = 0; index < topSlots; index++) {
            var stack = menu.slots.get(index).getItem();
            if (stack.isEmpty()) continue;
            var lore = stack.get(DataComponents.LORE);
            items.add(new FeastProgress.MenuItem(stack.getHoverName().getString(),
                lore == null ? List.of() : lore.lines().stream().limit(40).map(line -> line.getString()).toList()));
        }
        String title = screen.getTitle().getString();
        Long balance = FeastKernels.readMenu(title, items);
        if (kernels.observeMenu(menu, balance)) recordKernels("menu");
        if (FeastProgress.Kind.fromTitle(title) == null) {
            session.closeMenu();
            diagnosticMenu = null;
            return;
        }
        var read = FeastProgress.inspectMenu(title, items);
        diagnoseMenu(menu, title, items, read, balance);
        if (session.observeMenu(menu, read.snapshot())) recordProgress("menu");
    }

    private void diagnoseMenu(AbstractContainerMenu menu, String title, List<FeastProgress.MenuItem> items,
                              FeastProgress.MenuRead read, Long menuKernels) {
        if (menu != diagnosticMenu) {
            diagnosticMenu = menu;
            diagnosticKey = "";
            diagnosticSnapshots = 0;
        }
        var milestones = items.stream()
            .filter(item -> HypixelLocation.clean(item.name()).contains("Milestone")).limit(9).toList();
        String key = read.reason() + ":" + session.event() + ":" + milestones + ":" + menuKernels;
        if (key.equals(diagnosticKey) || diagnosticSnapshots >= 3) return;
        diagnosticKey = key;
        diagnosticSnapshots++;
        KungDebugRecorder.event("feast-menu", "container=" + menu.containerId + " title=" + title
            + " event=" + session.event() + " result=" + read.reason() + " milestones=" + milestones.size());
        if (FeastProgress.Kind.fromTitle(title) == FeastProgress.Kind.GRAND) {
            KungDebugRecorder.event("feast-kernels", "source=menu-read balance=" + menuKernels
                + " bakery=" + items.stream().filter(item -> HypixelLocation.clean(item.name()).equals("Grand Bakery"))
                    .limit(2).toList());
        }
        // Capture the actual server lore on failed syncs instead of silently returning an unknown total.
        if (read.snapshot() == null) {
            for (var item : milestones) {
                KungDebugRecorder.event("feast-menu", "item=" + item.name() + " lore=" + item.lore());
            }
            if (milestones.isEmpty()) KungDebugRecorder.event("feast-menu",
                "names=" + items.stream().map(FeastProgress.MenuItem::name).toList());
        }
    }

    private void selectProfile() {
        var player = Minecraft.getInstance().player;
        if (persistence != null && player != null
            && persistence.select(player.getUUID().toString(), context.profile())) {
            KungDebugRecorder.event("feast", "profile=" + context.profile() + " cache-selected");
        }
    }

    private void recordKernels(String source) {
        if (persistence != null) persistence.save();
        KungDebugRecorder.event("feast-kernels", "source=" + source + " balance=" + kernels.balance());
    }

    private void recordProgress(String source) {
        if (persistence != null) persistence.save();
        var value = session.snapshot();
        KungDebugRecorder.event("feast", "source=" + source + " event=" + session.baselineEvent()
            + " donations=" + value.donations() + "/" + value.goal() + " goals=" + value.goals());
    }

    private void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || client.player == null || client.screen instanceof KungHudEditorScreen) return;
        // Consult fresh shared instance state at render time, including the first frame after a warp.
        updateContext();
        if (!visible(hypixel, inGarden, inHubFarm, config().showInHubFarm(), session.event())) return;
        draw(graphics, config(), session.event().kind(), session.snapshot(), kernels.balance());
    }

    static boolean visible(boolean hypixel, boolean garden, FeastContext.Event event) {
        return visible(hypixel, garden, false, false, event);
    }

    static boolean visible(boolean hypixel, boolean garden, boolean hubFarm, boolean showInHubFarm,
                           FeastContext.Event event) {
        return hypixel && (garden || hubFarm && showInHubFarm) && event != null;
    }

    public static void drawPreview(GuiGraphicsExtractor graphics, FeastConfig config) {
        draw(graphics, config, FeastProgress.Kind.GRAND, EXAMPLE, 1_234L);
    }

    private static void draw(GuiGraphicsExtractor graphics, FeastConfig config,
                             FeastProgress.Kind kind, FeastProgress.Snapshot value, Long kernels) {
        var font = Minecraft.getInstance().font;
        int goal = value == null ? kind.defaultGoal() : value.goal();
        String total = (value == null ? "--" : Integer.toString(value.donations())) + " / " + goal;
        String next = footer(kind, value, kernels);
        int segments = value == null ? (kind == FeastProgress.Kind.GRAND ? 9 : 5) : value.goals().size();
        float scale = config.scale() / 100.0F;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(config.x(), config.y());
            graphics.pose().scale(scale, scale);
            graphics.text(font, total, (WIDTH - font.width(total)) / 2, 2, TEXT, true);
            drawBar(graphics, segments, value);
            float footerScale = Math.min(1.0F, BAR_WIDTH / (float) Math.max(1, font.width(next)));
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(WIDTH / 2.0F, 29);
                graphics.pose().scale(footerScale, footerScale);
                graphics.text(font, next, -font.width(next) / 2, 0,
                    value != null && value.complete() ? GREEN : TEXT, true);
            } finally {
                graphics.pose().popMatrix();
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    static String footer(FeastProgress.Kind kind, FeastProgress.Snapshot value, Long kernels) {
        String next = value == null ? "Open Feast menu to sync"
            : value.complete() ? "All milestones reached" : value.toNext() + " to next";
        return kind == FeastProgress.Kind.GRAND
            ? next + " - " + (kernels == null ? "--" : String.format(Locale.US, "%,d", kernels)) + " Kernels"
            : next;
    }

    private static void drawBar(GuiGraphicsExtractor graphics, int segments, FeastProgress.Snapshot value) {
        fill(graphics, BAR_X, BAR_Y, BAR_X + BAR_WIDTH, BAR_Y + BAR_HEIGHT, BAR_OUTLINE);
        fill(graphics, BAR_X + 1, BAR_Y + 1, BAR_X + BAR_WIDTH - 1, BAR_Y + BAR_HEIGHT - 1, 0xBBE0E7E0);
        int previousGoal = 0;
        int markerX = -1;
        for (int index = 0; index < segments; index++) {
            int left = BAR_X + 1 + (int) Math.round((BAR_WIDTH - 2) * index / (double) segments);
            int right = BAR_X + 1 + (int) Math.round((BAR_WIDTH - 2) * (index + 1) / (double) segments);
            int contentLeft = left + (index == 0 ? 0 : 1);
            if (value != null) {
                int tierGoal = value.goals().get(index);
                if (value.donations() >= tierGoal) {
                    fill(graphics, contentLeft, BAR_Y + 1, right, BAR_Y + BAR_HEIGHT - 1, GREEN);
                } else if (value.donations() >= previousGoal) {
                    fill(graphics, contentLeft, BAR_Y + 1, right, BAR_Y + BAR_HEIGHT - 1, YELLOW);
                    double progress = (value.donations() - previousGoal) / (double) (tierGoal - previousGoal);
                    markerX = contentLeft + (int) Math.round((right - contentLeft - 1) * progress);
                }
                previousGoal = tierGoal;
            }
            if (index > 0) fill(graphics, left, BAR_Y + 1, left + 1, BAR_Y + BAR_HEIGHT - 1, 0xDD101510);
        }
        // Extend beyond the bar so the progress marker stays distinct from milestone dividers.
        if (markerX >= 0) {
            fill(graphics, markerX - 1, BAR_Y - 2, markerX + 2, BAR_Y + BAR_HEIGHT + 2, BAR_OUTLINE);
            fill(graphics, markerX, BAR_Y - 1, markerX + 1, BAR_Y + BAR_HEIGHT + 1, TEXT);
        }
    }

    public static OverlayBounds overlayBounds(FeastConfig config) {
        float scale = config.scale() / 100.0F;
        return new OverlayBounds(config.x(), config.y(), Math.round(WIDTH * scale), Math.round(HEIGHT * scale));
    }

    static boolean isHypixel(String address) {
        if (address == null || address.isBlank()) return false;
        String host = ServerAddress.parseString(address).getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }

    public record OverlayBounds(int x, int y, int width, int height) {}
}
