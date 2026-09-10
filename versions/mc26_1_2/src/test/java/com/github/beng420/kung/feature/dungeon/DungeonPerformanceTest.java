package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public final class DungeonPerformanceTest {
    @Test
    public void repeatedPrinceLookupsKeepTheirResult() {
        List<String> names = DungeonKnownRoomCatalog.knownRoomNames().stream().limit(24).toList();
        List<Boolean> expected = names.stream().map(DungeonKnownRoomCatalog::hasPrince).toList();
        for (int frame = 0; frame < 5; frame++) {
            for (String name : names) DungeonKnownRoomCatalog.hasPrince(name);
        }
        long started = System.nanoTime();
        for (int frame = 0; frame < 50; frame++) {
            for (int index = 0; index < names.size(); index++) {
                assertEquals(expected.get(index), DungeonKnownRoomCatalog.hasPrince(names.get(index)));
            }
        }
        System.out.printf("Prince lookup workload: %.3f ms per 24-room frame%n",
            (System.nanoTime() - started) / 50_000_000.0);
    }
}
