package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.beng420.kung.feature.dungeon.room.RoomType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.junit.Test;

public final class RareDungeonRoomCatalogTest {
    private static final Set<String> WIKI_RARE_NAMES = Set.of(
        "Vinny 8 Ball", "Pillars", "Sand Dragon", "Tombstone", "Stone Window",
        "Mini Rail Track", "Trinity", "Hanging Vines"
    );

    @Test public void bundledRareRoomsKeepTheirExistingMetadataAndHashes() throws Exception {
        JsonArray rooms;
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(
            "/kung-dungeon-scans/known-rooms.json"), StandardCharsets.UTF_8)) {
            rooms = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("rooms");
        }
        int rareRooms = 0;
        for (var element : rooms) {
            JsonObject room = element.getAsJsonObject();
            if (WIKI_RARE_NAMES.contains(room.get("name").getAsString())) {
                assertEquals("RARE", room.get("type").getAsString());
                rareRooms++;
            }
        }
        assertEquals(8, rareRooms);

        DungeonKnownRoomCatalog.reload();
        var lavaPit = DungeonKnownRoomCatalog.knownCoreHint(1192954774);
        assertNotNull(lavaPit);
        assertEquals("Lava Pit", lavaPit.name());
        assertEquals(RoomType.NORMAL, lavaPit.type());
        assertEquals(3, lavaPit.secrets());
        assertEquals(1, lavaPit.crypts());
        assertFalse(lavaPit.prince());
        var stableLavaPit = DungeonKnownRoomCatalog.knownRoomInfos("Lava Pit", 0, -408192692);
        assertEquals(1, stableLavaPit.size());
        assertEquals(RoomType.NORMAL, stableLavaPit.getFirst().type());
        assertEquals(RoomType.NORMAL, DungeonKnownRoomCatalog.knownRoomInfos("Quad Lava").getFirst().type());
    }

    @Test public void legacyNormalJsonImportsPromoteVerifiedRareNamesWithoutChangingOtherMetadata() throws Exception {
        JsonArray rooms = new JsonArray();
        int core = 10_000;
        for (String name : WIKI_RARE_NAMES) {
            rooms.add(room(name, "NORMAL", 7, 12, true, core++, 20_000));
        }
        rooms.add(room("Unverified custom room", "RARE", 2, 0, false, core, 20_001));
        JsonObject database = new JsonObject();
        database.add("rooms", rooms);

        var templates = parseTemplates(database.toString());
        assertEquals(9, templates.size());
        for (var template : templates) {
            assertEquals(RoomType.RARE, template.type());
            if (WIKI_RARE_NAMES.contains(template.name())) {
                assertEquals(7, template.secrets());
                assertEquals(12, template.crypts());
                assertTrue(template.prince());
                assertEquals(20_000, template.components().getFirst().stableCoreHash());
            }
        }
    }

    @Test public void establishedRareAliasesRemainRareAfterNormalization() throws Exception {
        Map<String, String> aliases = Map.of("Haning Vines", "Hanging Vines");
        for (var alias : aliases.entrySet()) {
            JsonArray rooms = new JsonArray();
            rooms.add(room(alias.getKey(), "NORMAL", 0, 0, false, 30_000, 40_000));
            JsonObject database = new JsonObject();
            database.add("rooms", rooms);
            var template = parseTemplates(database.toString()).getFirst();
            assertEquals(alias.getValue(), template.name());
            assertEquals(RoomType.RARE, template.type());
            assertEquals(30_000, template.components().getFirst().coreHash());
            assertEquals(40_000, template.components().getFirst().stableCoreHash());
        }
    }

    @Test public void userConfirmedLavaRoomOnlyAddsATypeHint() throws Exception {
        Properties types = new Properties();
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(
            "/kung-dungeon-scans/known-room-types.properties"), StandardCharsets.UTF_8)) {
            types.load(reader);
        }
        assertEquals("RARE", types.getProperty("-1005518830"));
        DungeonKnownRoomCatalog.reload();
        assertNull(DungeonKnownRoomCatalog.knownCoreHint(-1005518830));
        assertTrue(DungeonKnownRoomCatalog.knownRoomInfos("Lava Pool").isEmpty());
        assertTrue(DungeonKnownRoomCatalog.knownRoomInfos("Lava Pit", -1005518830, 1296131753).isEmpty());
    }

    @Test public void mistakenRareLavaPitImportsAreRepairedWithoutLosingTheirHashes() throws Exception {
        for (String name : List.of("Lava Pit", "Lava Skull", "Lava Tomb")) {
            JsonArray rooms = new JsonArray();
            rooms.add(room(name, "RARE", 3, 1, false, 1192954774, -408192692));
            JsonObject database = new JsonObject();
            database.add("rooms", rooms);
            var template = parseTemplates(database.toString()).getFirst();
            assertEquals("Lava Pit", template.name());
            assertEquals(RoomType.NORMAL, template.type());
            assertEquals(3, template.secrets());
            assertEquals(1, template.crypts());
            assertEquals(1192954774, template.components().getFirst().coreHash());
            assertEquals(-408192692, template.components().getFirst().stableCoreHash());
        }
    }

    private static JsonObject room(String name, String type, int secrets, int crypts, boolean prince,
                                   int coreHash, int stableHash) {
        JsonObject room = new JsonObject();
        room.addProperty("name", name);
        room.addProperty("type", type);
        room.addProperty("secrets", secrets);
        room.addProperty("crypts", crypts);
        room.addProperty("prince", prince);
        JsonObject hash = new JsonObject();
        hash.addProperty("core", coreHash);
        hash.addProperty("stable", stableHash);
        JsonArray hashes = new JsonArray();
        hashes.add(hash);
        JsonObject component = new JsonObject();
        component.addProperty("dx", 0);
        component.addProperty("dz", 0);
        component.add("hashes", hashes);
        JsonArray components = new JsonArray();
        components.add(component);
        JsonObject variant = new JsonObject();
        variant.add("components", components);
        JsonArray variants = new JsonArray();
        variants.add(variant);
        room.add("variants", variants);
        return room;
    }

    @SuppressWarnings("unchecked")
    private static List<DungeonKnownRoomCatalog.RoomTemplate> parseTemplates(String json) throws Exception {
        Class<?> databaseClass = Class.forName(DungeonKnownRoomCatalog.class.getName() + "$RoomDatabase");
        Constructor<?> constructor = databaseClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object database = constructor.newInstance();
        Method read = DungeonKnownRoomCatalog.class.getDeclaredMethod("readRoomDatabase", String.class, databaseClass);
        read.setAccessible(true);
        read.invoke(null, json, database);
        Method templates = databaseClass.getDeclaredMethod("templates");
        templates.setAccessible(true);
        return (List<DungeonKnownRoomCatalog.RoomTemplate>) templates.invoke(database);
    }
}
