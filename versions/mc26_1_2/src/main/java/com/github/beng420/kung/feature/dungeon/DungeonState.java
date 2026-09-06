package com.github.beng420.kung.feature.dungeon;

import java.util.List;

import com.github.beng420.kung.feature.dungeon.room.DungeonRoom;

public final class DungeonState {
    private boolean inDungeon;
    private List<DungeonRoom> rooms = List.of();

    public boolean isInDungeon() {
        return inDungeon;
    }

    public List<DungeonRoom> rooms() {
        return List.copyOf(rooms);
    }

    public void setInDungeon(boolean inDungeon) {
        this.inDungeon = inDungeon;
    }

    public void setRooms(List<DungeonRoom> rooms) {
        this.rooms = List.copyOf(rooms);
    }
}
