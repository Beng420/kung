package com.github.beng420.kung.feature.safari;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Species/biome facts and source links are maintained in docs/CRITTER_SAFARI.md. */
public final class SafariCritters {
    public enum Region {
        FOREST("Forest", List.of("Foxtrot", "Bluebird", "Honeybug", "Treefrog", "Woodchucker",
            "Fluffling", "Hideonfloor", "Parakeet", "Macaw")),
        CAVERN("Cavern", List.of("Cavernfish", "Flitter", "Shyworm", "Driftling", "Chuckwalla",
            "Rockmite", "Scrappy", "Snoozle", "Gemzie")),
        ICY("Icy", List.of("Strongarm", "Tepid", "Polaris", "Shuddersquid", "Billygoat",
            "Mantis Shrimp", "Nozzlenose", "Troodon", "Wumpa")),
        HAUNTED("Haunted", List.of("Areita", "Bloodbat", "Duplico", "Gazer", "Litterbug",
            "Solsnatcher", "Gimmiegold", "Hideonwall", "Hideyho", "Doomspiral"));

        private final String title;
        private final List<String> critters;

        Region(String title, List<String> critters) {
            this.title = title;
            this.critters = critters;
        }

        public String title() { return title; }
        public List<String> critters() { return critters; }
    }

    private static final Map<String, String> NAMES;
    static {
        Map<String, String> names = new HashMap<>();
        for (Region region : Region.values()) {
            for (String name : region.critters()) names.put(name.toLowerCase(Locale.ROOT), name);
        }
        NAMES = Map.copyOf(names);
    }

    private SafariCritters() {}

    public static String canonical(String text) {
        if (text == null) return null;
        String name = text.strip().toLowerCase(Locale.ROOT);
        if (name.startsWith("sparkling ")) name = name.substring("sparkling ".length()).strip();
        return NAMES.get(name);
    }
}
