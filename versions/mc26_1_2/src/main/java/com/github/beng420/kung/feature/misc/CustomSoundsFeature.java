package com.github.beng420.kung.feature.misc;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.feature.Feature;
import com.github.beng420.kung.util.KungDebugRecorder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public final class CustomSoundsFeature extends ConfigurableFeature<MiscConfig> implements Feature {
    public static final CustomSoundsFeature INSTANCE = new CustomSoundsFeature();
    private static final String SOUND_DIRECTORY_NAME = "custom-sounds";
    private static final String SOUND_DIRECTORY_LABEL = "config/kung/custom-sounds";
    private static final String BUNDLED_SOUND_RESOURCE_PREFIX = "/assets/kung/sounds/custom/";
    private static final List<String> BUNDLED_SOUND_NAMES = List.of(
        "kung_arrow_ping.wav",
        "kung_arrow_plink.wav",
        "kung_wither_fade.wav",
        "kung_wither_chime.wav"
    );
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("wav", "aif", "aiff", "au", "mp3");
    private static final int WITHER_SHIELD_DURATION_TICKS = 100;
    private static final ExecutorService SOUND_EXECUTOR = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "Kung Custom Sound");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<String, CachedSound> SOUND_CACHE = new ConcurrentHashMap<>();
    private static volatile List<String> availableSounds = List.of();
    private static long clientTicks;
    private static long witherShieldExpiresAt = Long.MIN_VALUE;

    private CustomSoundsFeature() {
        super(config -> config.misc);
    }

    @Override
    protected void onInitialize() {
        installBundledSounds();
        refreshSoundIndex();
        ClientTickEvents.END_CLIENT_TICK.register(CustomSoundsFeature::tick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> observeMessage(message));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
            observeMessage(message));
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide() && player == Minecraft.getInstance().player) {
                observeItemUse(player.getItemInHand(hand));
            }
            return InteractionResult.PASS;
        });
    }

    @Override
    public boolean isEnabled() {
        return config().customSoundsEnabled();
    }

    public static void observeSoundEvent(SoundEvent sound) {
        if (!enabled() || sound == null) {
            return;
        }
        Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(sound);
        if (id == null || !"minecraft".equals(id.getNamespace())) {
            return;
        }
        String path = id.getPath();
        if ("entity.arrow.hit".equals(path) || "entity.arrow.hit_player".equals(path)) {
            playArrowHit();
        }
    }

    public static String soundsFolderStatus() {
        if (availableSounds.isEmpty()) {
            return "Folder: " + SOUND_DIRECTORY_LABEL;
        }
        return "Files: " + String.join(", ", availableSounds);
    }

    public static void refreshSoundIndex() {
        try {
            Path directory = soundsDirectory();
            Files.createDirectories(directory);
            List<String> next = new ArrayList<>();
            try (var files = Files.list(directory)) {
                files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(CustomSoundsFeature::isSupportedSoundFile)
                    .sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
                    .forEach(next::add);
            }
            availableSounds = List.copyOf(next);
        } catch (IOException exception) {
            KungMod.LOGGER.warn("Failed to scan custom sound directory.", exception);
            availableSounds = List.of();
        }
    }

    private static void installBundledSounds() {
        try {
            Path directory = soundsDirectory();
            Files.createDirectories(directory);
            for (String soundName : BUNDLED_SOUND_NAMES) {
                Path target = directory.resolve(soundName);
                if (Files.exists(target)) {
                    continue;
                }
                try (InputStream stream = CustomSoundsFeature.class.getResourceAsStream(
                    BUNDLED_SOUND_RESOURCE_PREFIX + soundName
                )) {
                    if (stream == null) {
                        KungMod.LOGGER.warn("Bundled custom sound {} was not found in the jar.", soundName);
                        continue;
                    }
                    Files.copy(stream, target);
                }
            }
        } catch (IOException exception) {
            KungMod.LOGGER.warn("Failed to install bundled custom sounds.", exception);
        }
    }

    public static void playArrowHit() {
        playConfigured(INSTANCE.config().customArrowHitSounds(), CustomSoundEvent.ARROW_HIT);
    }

    public static void playArrowHitSound(String soundName) {
        playSingle(soundName, CustomSoundEvent.ARROW_HIT);
    }

    public static void playWitherShieldExpire() {
        playConfigured(INSTANCE.config().customWitherShieldExpireSounds(), CustomSoundEvent.WITHER_SHIELD_EXPIRE);
    }

    public static void playWitherShieldExpireSound(String soundName) {
        playSingle(soundName, CustomSoundEvent.WITHER_SHIELD_EXPIRE);
    }

    public static List<String> configuredSoundNames(String configuredSounds) {
        if (configuredSounds == null || configuredSounds.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String soundName : configuredSounds.split(",")) {
            String normalized = normalizeSoundName(soundName);
            if (!normalized.isBlank() && !names.contains(normalized)) {
                names.add(normalized);
            }
        }
        return List.copyOf(names);
    }

    private static void tick(Minecraft client) {
        clientTicks++;
        if (witherShieldExpiresAt != Long.MIN_VALUE && clientTicks >= witherShieldExpiresAt) {
            witherShieldExpiresAt = Long.MIN_VALUE;
            playWitherShieldExpire();
        }
    }

    private static void observeItemUse(ItemStack stack) {
        if (!enabled() || stack == null || stack.isEmpty()) {
            return;
        }
        String itemName = clean(stack.getHoverName().getString());
        if (itemName.contains("hyperion")
            || itemName.contains("astraea")
            || itemName.contains("scylla")
            || itemName.contains("valkyrie")
            || itemName.contains("necron's blade")
            || itemName.contains("necrons blade")) {
            witherShieldExpiresAt = clientTicks + WITHER_SHIELD_DURATION_TICKS;
            KungDebugRecorder.event("custom-sounds", "scheduled wither-shield-expire tick=" + witherShieldExpiresAt);
        }
    }

    private static void observeMessage(Component message) {
        if (!enabled() || message == null) {
            return;
        }
        String text = clean(message.getString());
        if (text.contains("wither shield")
            && (text.contains("expired") || text.contains("ended") || text.contains("ran out"))) {
            witherShieldExpiresAt = Long.MIN_VALUE;
            playWitherShieldExpire();
        }
    }

    private static void playConfigured(String configuredSounds, CustomSoundEvent event) {
        if (!enabled() || configuredSounds == null || configuredSounds.isBlank()) {
            return;
        }
        for (String soundName : configuredSoundNames(configuredSounds)) {
            playSingle(soundName, event);
        }
    }

    private static void playSingle(String soundName, CustomSoundEvent event) {
        if (!enabled()) {
            return;
        }
        String normalized = normalizeSoundName(soundName);
        if (normalized.isBlank()) {
            return;
        }
        MiscConfig config = INSTANCE.config();
        int volumeTenths = event == CustomSoundEvent.ARROW_HIT
            ? config.customArrowHitSoundVolumeTenths(normalized)
            : config.customWitherShieldExpireSoundVolumeTenths(normalized);
        if (volumeTenths <= 0) {
            return;
        }
        int pitchHundredths = event == CustomSoundEvent.ARROW_HIT
            ? config.customArrowHitSoundPitchHundredths(normalized)
            : config.customWitherShieldExpireSoundPitchHundredths(normalized);
        float volume = Math.clamp(volumeTenths / 10.0f, 0.0f, 5.0f);
        float pitch = Math.clamp(pitchHundredths / 100.0f, 0.25f, 3.0f);
        CompletableFuture.runAsync(() -> playSound(normalized, volume, pitch), SOUND_EXECUTOR);
    }

    private static void playSound(String soundName, float volume, float pitch) {
        try {
            CachedSound sound = cachedSound(soundName);
            if (sound == null || sound.pcm().length == 0) {
                return;
            }
            AudioFormat sourceFormat = sound.format();
            float sampleRate = Math.clamp(sourceFormat.getSampleRate() * pitch, 8000.0f, 192000.0f);
            AudioFormat playbackFormat = new AudioFormat(
                sourceFormat.getEncoding(),
                sampleRate,
                sourceFormat.getSampleSizeInBits(),
                sourceFormat.getChannels(),
                sourceFormat.getFrameSize(),
                sampleRate,
                sourceFormat.isBigEndian()
            );
            try (SourceDataLine line = AudioSystem.getSourceDataLine(playbackFormat)) {
                line.open(playbackFormat);
                line.start();
                byte[] pcm = scaledPcm(sound.pcm(), volume);
                line.write(pcm, 0, pcm.length);
                line.drain();
            }
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException | IllegalArgumentException exception) {
            KungMod.LOGGER.warn("Failed to play custom sound {}.", soundName, exception);
        }
    }

    private static CachedSound cachedSound(String soundName) throws IOException, UnsupportedAudioFileException {
        String normalized = normalizeSoundName(soundName);
        Path path = soundsDirectory().resolve(normalized).normalize();
        if (!path.startsWith(soundsDirectory()) || !Files.isRegularFile(path) || !isSupportedSoundFile(normalized)) {
            return null;
        }
        long modified = Files.getLastModifiedTime(path).toMillis();
        CachedSound cached = SOUND_CACHE.get(normalized);
        if (cached != null && cached.modifiedAtMillis() == modified) {
            return cached;
        }

        try (AudioInputStream input = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat baseFormat = input.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(),
                16,
                baseFormat.getChannels(),
                baseFormat.getChannels() * 2,
                baseFormat.getSampleRate(),
                false
            );
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(decodedFormat, input)) {
                byte[] pcm = decoded.readAllBytes();
                CachedSound next = new CachedSound(modified, pcm, decodedFormat);
                SOUND_CACHE.put(normalized, next);
                return next;
            }
        }
    }

    private static byte[] scaledPcm(byte[] source, float volume) {
        if (Math.abs(volume - 1.0f) < 0.001f) {
            return source;
        }
        byte[] copy = Arrays.copyOf(source, source.length);
        for (int index = 0; index + 1 < copy.length; index += 2) {
            int sample = (short) ((copy[index] & 0xFF) | (copy[index + 1] << 8));
            int scaled = Math.round(sample * volume);
            scaled = Math.clamp(scaled, Short.MIN_VALUE, Short.MAX_VALUE);
            copy[index] = (byte) (scaled & 0xFF);
            copy[index + 1] = (byte) ((scaled >> 8) & 0xFF);
        }
        return copy;
    }

    private static Path soundsDirectory() {
        return KungConfig.configDirectory().resolve(SOUND_DIRECTORY_NAME);
    }

    private static boolean enabled() {
        return INSTANCE.isEnabled();
    }

    private static boolean isSupportedSoundFile(String name) {
        String normalized = normalizeSoundName(name);
        int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot == normalized.length() - 1) {
            return false;
        }
        return SUPPORTED_EXTENSIONS.contains(normalized.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String normalizeSoundName(String value) {
        if (value == null) {
            return "";
        }
        String name = value.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name;
    }

    private static String clean(String text) {
        String stripped = ChatFormatting.stripFormatting(text == null ? "" : text);
        return (stripped == null ? "" : stripped).toLowerCase(Locale.ROOT);
    }

    private record CachedSound(long modifiedAtMillis, byte[] pcm, AudioFormat format) {
    }

    private enum CustomSoundEvent {
        ARROW_HIT,
        WITHER_SHIELD_EXPIRE
    }
}
