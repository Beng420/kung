package com.github.beng420.kung.feature.dungeon.room;

public record DungeonRoom(
    int gridX,
    int gridY,
    int width,
    int height,
    RoomType type
) {
}