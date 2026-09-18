package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class DungeonRoomRepositoryTest {
    private static final List<String> PRINCE_ROOMS = List.of("Doors", "Skull", "Supertall", "Withermancer");
    private static final List<String> NON_PRINCE_ROOMS = List.of(
        "Big Red Flag", "Sloth", "Leaves", "Bridges", "Chambers", "Grass Ruin",
        "Waterfall", "Red Blue", "Market", "Flags", "Quartz Knight", "Pirate", "Andesite"
    );

    @Test
    public void matchesARoomTemplateWithoutChangingItsIdentity() {
        DungeonKnownRoomCatalog.RoomTemplate template = new DungeonKnownRoomCatalog.RoomTemplate(
            "Test Room",
            com.github.beng420.kung.feature.dungeon.room.RoomType.NORMAL,
            7,
            2,
            false,
            1,
            List.of(new DungeonKnownRoomCatalog.TemplateComponent(0, 0, 101, 1001))
        );
        List<DungeonScanPoint> points = new ArrayList<>();
        for (DungeonKnownRoomCatalog.TemplateComponent component : template.components()) {
            points.add(new DungeonScanPoint(
                component.dx() * 2,
                component.dz() * 2,
                component.dx() * 32,
                component.dz() * 32,
                DungeonScanPointKind.ROOM,
                true,
                component.coreHash(),
                component.stableCoreHash(),
                0,
                DungeonDoorKind.NONE
            ));
        }
        DungeonMapSnapshot snapshot = new DungeonMapSnapshot();
        snapshot.addScan(1, 1L, 5, 5, points);
        assertNotNull(snapshot.pointAt(0, 0));
        assertTrue(template.components().get(0).matches(101, 1001));

        List<DungeonKnownRoomCatalog.MatchedRoom> matches =
            DungeonKnownRoomCatalog.matchKnownRooms(snapshot, List.of(template), Map.of());

        DungeonKnownRoomCatalog.MatchedRoom matched = matches.stream()
            .filter(match -> match.template().name().equals("Test Room"))
            .findFirst()
            .orElseThrow();
        assertEquals(template.type(), matched.template().type());
        assertEquals(template.secrets(), matched.template().secrets());
        assertEquals(1, matched.components().size());
        assertTrue(matched.contains(0, 0));
    }

    @Test
    public void readsPrinceClassificationFromBundledRoomData() throws Exception {
        try (InputStream stream = DungeonKnownRoomCatalog.class.getResourceAsStream("/kung-dungeon-scans/known-rooms.json")) {
            assertNotNull(stream);
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Object database = importedDatabase(json);
            Method toJson = database.getClass().getDeclaredMethod("toJson");
            toJson.setAccessible(true);
            assertEquals(princeByIdentity(json), princeByIdentity((String) toJson.invoke(database)));
        }
    }

    @Test
    public void importsWithoutPrinceMetadataUseTheCorrectedFallback() throws Exception {
        for (String name : PRINCE_ROOMS) assertTrue(name, importedRoom(name, "").prince());
        for (String name : NON_PRINCE_ROOMS) assertFalse(name, importedRoom(name, "").prince());
        assertTrue(importedRoom("Super Tall", "").prince());
        assertTrue(importedRoom("Withermancers", "").prince());
        assertFalse(importedRoom("Red-Blue", "").prince());
    }

    @Test
    public void drawBridgeAliasPreservesExplicitPrinceMetadata() throws Exception {
        assertFalse(importedRoom("Draw Bridge", "").prince());
        for (boolean prince : List.of(false, true)) {
            var room = importedRoom("Draw Bridge", ",\"prince\":" + prince);
            assertEquals("Bridges", room.name());
            assertEquals(6, room.secrets());
            assertEquals(6, room.crypts());
            assertEquals(prince, room.prince());
            assertEquals(101, room.components().getFirst().coreHash());
        }
    }

    private static Map<List<String>, Boolean> princeByIdentity(String json) {
        Map<List<String>, Boolean> values = new LinkedHashMap<>();
        for (var element : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("rooms")) {
            var room = element.getAsJsonObject();
            assertTrue(room.get("prince").getAsJsonPrimitive().isBoolean());
            values.put(List.of(room.get("name").getAsString(), room.get("type").getAsString(),
                room.get("secrets").getAsString()), room.get("prince").getAsBoolean());
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static DungeonKnownRoomCatalog.RoomTemplate importedRoom(String name, String princeField) throws Exception {
        String json = """
            {"rooms":[{"name":"%s","type":"NORMAL","secrets":6,"crypts":6%s,
              "variants":[{"components":[{"dx":0,"dz":0,"hashes":[{"core":101,"stable":1001}]}]}]}]}
            """.formatted(name, princeField);
        Object database = importedDatabase(json);
        Method templates = database.getClass().getDeclaredMethod("templates");
        templates.setAccessible(true);
        return ((List<DungeonKnownRoomCatalog.RoomTemplate>) templates.invoke(database)).getFirst();
    }

    private static Object importedDatabase(String json) throws Exception {
        Class<?> databaseClass = Class.forName(DungeonKnownRoomCatalog.class.getName() + "$RoomDatabase");
        Constructor<?> constructor = databaseClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object database = constructor.newInstance();
        Method read = DungeonKnownRoomCatalog.class.getDeclaredMethod("readRoomDatabase", String.class, databaseClass);
        read.setAccessible(true);
        read.invoke(null, json, database);
        return database;
    }
}
