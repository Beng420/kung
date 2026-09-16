package com.github.beng420.kung.compat;

import com.github.beng420.kung.config.SettingEntry;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind;

/** Converts the editor's single input into Kung's existing context-specific loadout binding. */
final class KungOneConfigKeybind implements Supplier<OneConfigKeybind>, Consumer<OneConfigKeybind> {
    private final SettingEntry setting;
    private String cachedRaw;
    private OneConfigKeybind cached;

    KungOneConfigKeybind(SettingEntry setting) {
        this.setting = setting;
    }

    @Override
    public OneConfigKeybind get() {
        String raw = setting.rawKeybindSupplier().get();
        if (cached != null && Objects.equals(cachedRaw, raw)) return cached;
        cachedRaw = raw;
        int[] keys = new int[0];
        int[] mouse = new int[0];
        String[] parts = raw == null ? new String[0] : raw.split(":");
        if (parts.length >= 2) {
            try {
                int code = Integer.parseInt(parts[1]);
                if (code >= 0) {
                    if (parts[0].equals("key")) keys = new int[] {code};
                    if (parts[0].equals("mouse")) mouse = new int[] {code};
                }
            } catch (NumberFormatException ignored) {
                // Legacy/unrecognized encodings stay intact until the user chooses a new input.
            }
        }
        cached = new OneConfigKeybind(keys, mouse, (byte) 0, 0L, pressed -> false);
        return cached;
    }

    @Override
    public void accept(OneConfigKeybind value) {
        if (value == null || value.getMods() != 0) return;
        int[] keys = value.getKeyCodes();
        int[] mouse = value.getMouseBtns();
        int keyCount = keys == null ? 0 : keys.length;
        int mouseCount = mouse == null ? 0 : mouse.length;
        if (keyCount + mouseCount > 1) return;
        String raw;
        if (keyCount == 1 && keys[0] >= 0) {
            // GLFW key codes are sufficient for Kung; retain an existing physical scan code when unchanged.
            String current = setting.rawKeybindSupplier().get();
            raw = current != null && current.startsWith("key:" + keys[0] + ":")
                ? current : "key:" + keys[0] + ":0";
        } else if (mouseCount == 1 && mouse[0] >= 0) {
            raw = "mouse:" + mouse[0];
        } else if (keyCount + mouseCount == 0) {
            raw = "";
        } else return;
        setting.setText(raw);
        // Resets may supply a deserialized bind with no action. Always retain our own inert editor binding.
        cached = null;
        get();
    }
}
