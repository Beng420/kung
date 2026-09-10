package com.github.beng420.kung.feature.dungeon;

import java.util.UUID;
import java.util.EnumSet;

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
    private final EnumSet<DungeonBonusContribution> bonuses = EnumSet.noneOf(DungeonBonusContribution.class);

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
    public String bonusMarkers() {
        StringBuilder result = new StringBuilder();
        for (DungeonBonusContribution bonus : bonuses) result.append(bonus.marker());
        return result.toString();
    }

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
    void setRoomClearBounds(int minimum, int maximum) {
        soloRoomsCleared = Math.max(0, minimum);
        roomsCleared = Math.max(soloRoomsCleared, maximum);
    }
    void addBonus(DungeonBonusContribution bonus) { bonuses.add(bonus); }
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

    void resetRunCounters() {
        deaths = 0;
        roomsCleared = 0;
        soloRoomsCleared = 0;
        secretsFound = 0;
        apiRunSecretsFound = -1;
        roomGridX = -1;
        roomGridZ = -1;
        lastSeenTick = 0;
        bonuses.clear();
    }

    void merge(DungeonPlayerStats other) {
        bonuses.addAll(other.bonuses);
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
