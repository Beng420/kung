package com.github.beng420.kung.ui;

import static org.junit.Assert.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.junit.Test;

public final class UiMenuFontTest {
    @Test public void bundledFontDefinitionsDecodeWithMinecraftAndResolveTheirFontFiles() throws Exception {
        var loader = getClass().getClassLoader();
        try (var stream = loader.getResourceAsStream("assets/kung/font/chat_spacing.json")) {
            assertNotNull(stream);
            var providers = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("providers");
            for (var json : providers) {
                // Minecraft rejects the whole font definition if even one provider has an invalid ID.
                var provider = GlyphProviderDefinition.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
                if (provider instanceof TrueTypeGlyphProviderDefinition ttf) {
                    var id = ttf.location();
                    try (var fontFile = loader.getResourceAsStream(
                        "assets/" + id.getNamespace() + "/font/" + id.getPath())) {
                        assertNotNull("Missing Minecraft font resource " + id, fontFile);
                    }
                }
            }
        }
    }

    @Test public void menuFontOnlyTouchesDefaultGlyphsAndKeepsExplicitResources() {
        var requested = new ArrayList<FontDescription>();
        Font.Provider original = new Font.Provider() {
            @Override public GlyphSource glyphs(FontDescription description) { requested.add(description); return null; }
            @Override public EffectGlyph effect() { return null; }
        };
        var provider = UiMenuFont.menuProvider(original);
        var other = new FontDescription.Resource(Identifier.fromNamespaceAndPath("kung", "chat_spacing"));
        // Without a client the runtime font cannot be built, so both end up asking the game's own font.
        provider.glyphs(FontDescription.DEFAULT);
        provider.glyphs(other);
        assertEquals(java.util.List.of(FontDescription.DEFAULT, other), requested);
    }

    @Test public void bundledFontLoadsAndKeepsItsLicense() throws Exception {
        var loader = getClass().getClassLoader();
        try (var stream = loader.getResourceAsStream("assets/kung/font/inter.ttf")) {
            assertNotNull(stream);
            var font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream);
            assertTrue(font.getFamily(java.util.Locale.ROOT).contains("Inter"));
            assertEquals(-1, font.canDisplayUpTo("Kung Updates Changelogs äöüß"));
        }
        try (var stream = loader.getResourceAsStream("assets/kung/font/inter-ofl.txt")) {
            assertNotNull(stream);
            assertTrue(new String(stream.readAllBytes(), StandardCharsets.UTF_8).contains("SIL OPEN FONT LICENSE"));
        }
    }
}
