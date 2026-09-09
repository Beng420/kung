package com.github.beng420.kung.feature.dungeon;

import java.util.UUID;

public final class DungeonPlayerStats {
    private final UUID uuid;
    private String name;
    private DungeonRunStats.DungeonClass dungeonClass = DungeonRunStats.DungeonClass.UNKNOWN;
    private int deaths;
    private int roomsCleared;
    private int soloRoomsCleared;
    private int secretsFound;
    private int totalSecretsFound = -1;
    private int runSecretBaseline = -1;
    private int apiRunSecretsFound = -1;
    private int roomGridX = -1;
    private int roomGridZ = -1;
    private long lastSeenTick;

    DungeonPlayerStats(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public DungeonRunStats.DungeonClass dungeonClass() { return dungeonClass; }
    public int deaths() { return deaths; }
    public int roomsCleared() { return roomsCleared; }
    public int soloRoomsCleared() { return soloRoomsCleared; }
    public int secretsFound() { return Math.max(secretsFound, apiRunSecretsFound); }
    public int totalSecretsFound() { return totalSecretsFound; }
    public int roomGridX() { return roomGridX; }
    public int roomGridZ() { return roomGridZ; }
    public long lastSeenTick() { return lastSeenTick; }

    void setName(String name) { this.name = name; }
    void setDungeonClass(DungeonRunStats.DungeonClass dungeonClass) { this.dungeonClass = dungeonClass; }
    void incrementDeaths(int count) { deaths += count; }
    void setDeaths(int deaths) { this.deaths = Math.max(this.deaths, deaths); }
    void incrementSecrets(int count) { secretsFound += count; }
    void observeRoom(int roomGridX, int roomGridZ, long nowTick) {
        this.roomGridX = roomGridX;
        this.roomGridZ = roomGridZ;
        this.lastSeenTick = nowTick;
    }
    void incrementRoomsCleared(boolean solo) {
        roomsCleared++;
        if (solo) {
            soloRoomsCleared++;
        }
    }
    void setSecretsFound(int secretsFound) { this.secretsFound = Math.max(this.secretsFound, secretsFound); }
    void setTotalSecretsFound(int totalSecretsFound) {
        this.totalSecretsFound = Math.max(this.totalSecretsFound, totalSecretsFound);
    }
    void setRunSecretBaseline(int runSecretBaseline) {
        this.runSecretBaseline = Math.max(this.runSecretBaseline, runSecretBaseline);
    }
    void setApiRunSecretsFound(int apiRunSecretsFound) {
        this.apiRunSecretsFound = Math.max(this.apiRunSecretsFound, apiRunSecretsFound);
    }

    void merge(DungeonPlayerStats other) {
        deaths += other.deaths;
        roomsCleared += other.roomsCleared;
        soloRoomsCleared += other.soloRoomsCleared;
        secretsFound += other.secretsFound;
        totalSecretsFound = Math.max(totalSecretsFound, other.totalSecretsFound);
        runSecretBaseline = Math.max(runSecretBaseline, other.runSecretBaseline);
        apiRunSecretsFound = Math.max(apiRunSecretsFound, other.apiRunSecretsFound);
        if (dungeonClass == DungeonRunStats.DungeonClass.UNKNOWN
            && other.dungeonClass != DungeonRunStats.DungeonClass.UNKNOWN) {
            dungeonClass = other.dungeonClass;
        }
        if (roomGridX < 0 && other.roomGridX >= 0) {
            roomGridX = other.roomGridX;
            roomGridZ = other.roomGridZ;
            lastSeenTick = other.lastSeenTick;
        }
    }
}
