package com.github.beng420.kung.ui;

import com.github.beng420.kung.mixin.FontAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/** Menu-local font: measurements, editing and drawing share the same resource-reload-aware provider. */
public final class UiMenuFont extends Font {
    static final FontDescription MENU = new FontDescription.Resource(Identifier.fromNamespaceAndPath("kung", "menu"));

    private UiMenuFont(Font base) {
        super(menuProvider(((FontAccessor) base).kung$getProvider()));
    }

    public static Font wrap(Font base) { return base instanceof UiMenuFont ? base : new UiMenuFont(base); }

    static Provider menuProvider(Provider base) {
        return new Provider() {
            @Override public GlyphSource glyphs(FontDescription description) {
                return base.glyphs(FontDescription.DEFAULT.equals(description) ? MENU : description);
            }
            @Override public EffectGlyph effect() { return base.effect(); }
        };
    }

    @Override public PreparedText prepareText(String text, float x, float y, int color, boolean shadow, int background) {
        return super.prepareText(text, x, y, color, false, background);
    }

    @Override public PreparedText prepareText(FormattedCharSequence text, float x, float y, int color,
                                            boolean shadow, boolean inverseDepth, int background) {
        return super.prepareText(text, x, y, color, false, inverseDepth, background);
    }
}
