package com.github.beng420.kung.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import org.junit.Test;

public final class MenuFontTest {
    @Test
    public void googleFontsStylesheetGivesUpItsTrueTypeFile() throws IOException {
        String css = "/* latin */\n@font-face {\n  font-family: 'Roboto';\n  font-style: normal;\n"
            + "  src: url(https://fonts.gstatic.com/s/roboto/v48/KFO7CnqEu92Fr1ME7kSn66aGLdTylUAMQXC89YmC2DPNWubEbVmZiArmlw.ttf)"
            + " format('truetype');\n}\n";
        assertEquals("https://fonts.gstatic.com/s/roboto/v48/"
            + "KFO7CnqEu92Fr1ME7kSn66aGLdTylUAMQXC89YmC2DPNWubEbVmZiArmlw.ttf", KungFonts.ttfUrl(css));
        // Only .ttf works in Minecraft, so a woff2-only answer has to fail instead of saving junk.
        assertThrows(IOException.class, () -> KungFonts.ttfUrl(css.replace(".ttf", ".woff2")));
    }
}
