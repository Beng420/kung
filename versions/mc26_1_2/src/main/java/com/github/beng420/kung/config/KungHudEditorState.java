package com.github.beng420.kung.config;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Optional editors draw above their own background instead of the normal game HUD layer. */
public final class KungHudEditorState {
    private static BooleanSupplier externalEditor = () -> false;

    private KungHudEditorState() { }

    public static boolean externalEditing() { return externalEditor.getAsBoolean(); }

    public static void setExternalEditor(BooleanSupplier editor) {
        externalEditor = Objects.requireNonNull(editor);
    }
}
