package com.github.beng420.kung.feature.garden;

import com.github.beng420.kung.config.category.VisitorAlarmConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.message.KungMessages;
import com.github.beng420.kung.skyblock.HypixelInstanceTracker;
import com.github.beng420.kung.skyblock.HypixelLocation;
import com.github.beng420.kung.util.KungDebugRecorder;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.GameType;

public final class VisitorAlarmFeature extends ConfigurableFeature<VisitorAlarmConfig> {
    public static final VisitorAlarmFeature INSTANCE = new VisitorAlarmFeature();
    // Vanilla tab ordering; the shared context's unordered rows cannot associate visitor names with their header.
    private static final Comparator<PlayerInfo> TAB_ORDER = Comparator.comparingInt(PlayerInfo::getTabListOrder).reversed()
        .thenComparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
        .thenComparing(info -> info.getTeam() == null ? "" : info.getTeam().getName())
        .thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER);

    private final VisitorAlarmState state = new VisitorAlarmState();
    private final GardenCropTracker crops = new GardenCropTracker();
    private final VisitorTimerCompatibility compatibility = new VisitorTimerCompatibility();
    private final FeastContext profile = new FeastContext();
    private SimpleSoundInstance sound;
    // Keep ownership across alarm cycles/resets so the next alert replaces any surviving chat entry.
    private Component reminderMessage;
    private Set<String> candidateRoster;
    private long candidateSince;
    private long epoch = -1;
    private long worldSince;
    private long nextTone;
    private int polls;
    private boolean inGarden;
    private boolean announced;
    private boolean highTone;
    private int cropReductions;

    private VisitorAlarmFeature() { super(config -> config.visitorAlarm); }

    @Override
    protected void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay && initialized() && isEnabled()) observeMessage(message.getString());
            return true;
        });
    }

    @Override
    public boolean isEnabled() { return initialized() && config().enabled(); }

    private void tick(Minecraft client) {
        if (!isEnabled()) {
            if (state.queue() != null || sound != null) onReset();
            return;
        }
        var server = client.getCurrentServer();
        if (server == null || !HypixelLocation.isHypixelAddress(server.ip) || client.getConnection() == null) {
            onReset();
            return;
        }
        long now = now();
        var context = HypixelInstanceTracker.INSTANCE;
        if (epoch != context.instanceEpoch()) {
            epoch = context.instanceEpoch();
            worldSince = now;
            candidateRoster = null;
            crops.reset();
            profile.worldChanged();
        }
        inGarden = context.tracking() && VisitorQueue.ownGarden(context.instanceLine(), context.sidebarLines(), context.visibleLines());
        if (++polls >= 5) {
            polls = 0;
            if (profile.observe(context.sidebarLines(), context.visibleLines())) clearQueue(client);
            if (inGarden && client.player != null && now - worldSince >= 2_000) observeQueue(client, now);
        }
        if (inGarden && candidateRoster != null && now - candidateSince >= 500
            && state.queue() != null && state.queue().visitors().equals(candidateRoster)) state.tick(now);
        if (state.ringing()) {
            if (!announced && client.player != null) {
                announced = true;
                record("started", now);
            }
            if (client.player != null && state.reminderDue(now)) {
                reminderMessage = KungMessages.replaceLocal(client, reminderMessage, alarmMessage());
            }
            if (now >= nextTone) {
                stopSound(client);
                highTone = !highTone;
                sound = SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(),
                    highTone ? 1.6F : 1.0F, config().volume() / 100F);
                client.getSoundManager().play(sound);
                nextTone = now + 500;
            }
        } else {
            if (announced) record("visitor-served", now);
            announced = false;
            nextTone = 0;
            stopSound(client);
        }
    }

    private void observeQueue(Minecraft client, long now) {
        var players = client.getConnection().getListedOnlinePlayers();
        if (players.size() > 100) return;
        List<String> lines = players.stream().sorted(TAB_ORDER).limit(80)
            .map(PlayerInfo::getTabListDisplayName).map(component -> component == null ? "" : component.getString()).toList();
        var snapshot = VisitorQueue.parse(lines);
        if (snapshot == null) { candidateRoster = null; return; }
        if (!snapshot.visitors().equals(candidateRoster)) {
            candidateRoster = snapshot.visitors();
            candidateSince = now;
            return;
        }
        // Require a stable roster across packet batches, while still accepting each fresh timer value.
        if (now - candidateSince < 500) return;
        var previous = state.queue();
        boolean changed = previous == null || !previous.visitors().equals(snapshot.visitors()) || previous.full() != snapshot.full();
        if (changed) cropReductions = 0;
        state.observe(snapshot, now);
        if (previous == null && snapshot.full() && snapshot.count() == 5) {
            var seed = compatibility.read(System.currentTimeMillis());
            if (seed != null) {
                state.seed(seed.intervalMillis(), seed.remainingMillis(), now);
                record("existing-timer", now);
            } else record("full-queue-conservative-start", now);
        }
        if (changed) {
            record("queue", now);
        }
    }

    private void observeMessage(String message) {
        if (FeastContext.profileMessage(message)) {
            String previous = profile.profile();
            profile.observeProfileMessage(message);
            if (!previous.isBlank() && !previous.equals(profile.profile())) clearQueue(Minecraft.getInstance());
        } else if (inGarden && state.queue() != null && state.queue().full()) {
            var reward = VisitorQueue.pestReward(message);
            if (reward == null) return;
            long now = now();
            long before = state.remaining(now);
            if (reward.countsAsKill()) state.reduce(30_000, now);
            KungDebugRecorder.event("visitor-pest", "pest=" + reward.pest() + " item=" + reward.item()
                + " countsAsKill=" + reward.countsAsKill() + " beforeMs=" + before + " afterMs=" + state.remaining(now)
                + " cropReductions=" + cropReductions);
        }
    }

    public static void observeContainerInput(int containerId, int slotId, int button, ContainerInput input) {
        if (!INSTANCE.isEnabled() || !INSTANCE.inGarden || !INSTANCE.state.ringing()
            || (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE)
            || button < 0 || button > 1) return;
        var client = Minecraft.getInstance();
        if (client.player == null || !(client.screen instanceof AbstractContainerScreen<?> screen)) return;
        var menu = screen.getMenu();
        int topSlots = menu.slots.size() - 36;
        if (menu != client.player.containerMenu || menu.containerId != containerId
            || topSlots <= 13 || topSlots > 54 || slotId < 0 || slotId >= topSlots) return;
        var infoLore = menu.slots.get(13).getItem().get(DataComponents.LORE);
        if (infoLore == null) return;
        String title = screen.getTitle().getString();
        String action = menu.slots.get(slotId).getItem().getHoverName().getString();
        if (!VisitorOffer.isDecision(title, action,
            infoLore.lines().stream().limit(40).map(line -> line.getString()).toList(), INSTANCE.state.queue().visitors())) return;
        INSTANCE.acknowledge(client, "offer-click title=" + title + " action=" + action);
    }

    static Component alarmMessage() {
        return KungMessages.warning("Visitors",
            "6th visitor ready! Accept/decline a visitor or click here to mute.").copy().withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand("/kung visitors mute"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                    "Mute sound and reminders until the next visitor cycle."))));
    }

    public static boolean muteCurrentAlarm() {
        return INSTANCE.isEnabled() && INSTANCE.acknowledge(Minecraft.getInstance(), "chat-mute");
    }

    private boolean acknowledge(Minecraft client, String reason) {
        if (!state.acknowledge()) return false;
        stopSound(client);
        announced = false;
        nextTone = 0;
        record(reason, now());
        return true;
    }

    public static void observeCropClick(BlockPos pos) {
        if (!INSTANCE.isEnabled() || !INSTANCE.inGarden) return;
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.player.isSpectator()) return;
        var block = client.level.getBlockState(pos);
        if (!INSTANCE.crops.observe(pos, block) || INSTANCE.state.queue() == null || !INSTANCE.state.queue().full()) return;
        long now = now();
        long before = INSTANCE.state.remaining(now);
        INSTANCE.state.reduce(100, now);
        if (before <= 0) return;
        INSTANCE.cropReductions++;
        // Aggregate instead of recording every mining call; pest/alarm records also include the current count.
        if (INSTANCE.cropReductions == 1 || INSTANCE.cropReductions % 100 == 0) {
            KungDebugRecorder.event("visitor-crop", "block=" + BuiltInRegistries.BLOCK.getKey(block.getBlock())
                + " cropReductions=" + INSTANCE.cropReductions + " beforeMs=" + before
                + " afterMs=" + INSTANCE.state.remaining(now));
        }
    }

    private void clearQueue(Minecraft client) {
        state.reset();
        stopSound(client);
        candidateRoster = null;
        announced = false;
        nextTone = 0;
        crops.reset();
        cropReductions = 0;
    }

    private void stopSound(Minecraft client) {
        if (sound != null) client.getSoundManager().stop(sound);
        sound = null;
    }

    private void record(String reason, long now) {
        KungDebugRecorder.event("visitor-alarm", reason + " visitors="
            + (state.queue() == null ? List.of() : state.queue().visitors())
            + " remainingMs=" + state.remaining(now) + " intervalMs=" + state.interval() + " ringing=" + state.ringing()
            + " cropReductions=" + cropReductions);
    }

    @Override
    protected void onReset() {
        clearQueue(Minecraft.getInstance());
        inGarden = false;
        epoch = -1;
        polls = 0;
        profile.reset();
    }

    @Override
    protected void onShutdown() { onReset(); }

    private static long now() { return System.nanoTime() / 1_000_000; }
}
