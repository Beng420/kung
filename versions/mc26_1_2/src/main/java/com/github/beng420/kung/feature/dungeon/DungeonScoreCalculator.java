package com.github.beng420.kung.feature.dungeon;

public final class DungeonScoreCalculator {
    private DungeonScoreCalculator() {
    }

    public static int skillScore(int completedRoomScore, int deaths, int failedPuzzles) {
        int puzzlePenalty = Math.max(0, failedPuzzles) * 14;
        return 20 + Math.clamp(completedRoomScore - deathPenalty(deaths) - puzzlePenalty, 0, 80);
    }

    public static int projectedSkillScore(int deaths, int failedPuzzles) {
        return skillScore(80, deaths, failedPuzzles);
    }

    public static int deathPenalty(int deaths) {
        return deaths <= 0 ? 0 : deaths * 2 - 1;
    }

    public static double requiredSecretsPercent(int floor, boolean masterMode) {
        if (masterMode) {
            return 100.0;
        }
        return switch (floor) {
            case 1 -> 30.0;
            case 2 -> 40.0;
            case 3 -> 50.0;
            case 4 -> 60.0;
            case 5 -> 70.0;
            case 6 -> 85.0;
            default -> 100.0;
        };
    }

    public static int secretScoreFromPercent(double secretsPercent, double requiredPercent) {
        if (secretsPercent < 0.0 || requiredPercent <= 0.0) {
            return 0;
        }
        return Math.clamp((int) (40.0 * Math.min(requiredPercent, secretsPercent) / requiredPercent), 0, 40);
    }

    public static int secretScoreFromCount(int foundSecrets, int totalSecrets, double requiredPercent) {
        if (totalSecrets <= 0 || requiredPercent <= 0.0) {
            return 0;
        }
        double requiredSecrets = requiredPercent * totalSecrets / 100.0;
        return Math.clamp((int) Math.floor(40.0 * Math.max(0, foundSecrets) / requiredSecrets), 0, 40);
    }

    public static int bonusScore(
        int cryptsOpened,
        boolean mimicKilled,
        boolean mimicRequiredAndAllSecrets,
        boolean princeKilled,
        boolean batScoreKilled
    ) {
        return Math.min(5, Math.max(0, cryptsOpened))
            + (mimicKilled || mimicRequiredAndAllSecrets ? 2 : 0)
            + (princeKilled ? 1 : 0)
            + (batScoreKilled ? 1 : 0);
    }

    public static int speedScore(long elapsedSeconds, long graceSeconds) {
        if (graceSeconds <= 0L || elapsedSeconds < graceSeconds) {
            return 100;
        }
        double timePastRequirement = ((double) (elapsedSeconds - graceSeconds) / graceSeconds) * 100.0;
        if (timePastRequirement < 20.0) {
            return 100 - (int) timePastRequirement / 2;
        }
        if (timePastRequirement < 40.0) {
            return 100 - (int) (10.0 + (timePastRequirement - 20.0) / 4.0);
        }
        if (timePastRequirement < 50.0) {
            return 100 - (int) (15.0 + (timePastRequirement - 40.0) / 5.0);
        }
        if (timePastRequirement < 60.0) {
            return 100 - (int) (17.0 + (timePastRequirement - 50.0) / 6.0);
        }
        return Math.clamp(
            100 - (int) (18.0 + (2.0 / 3.0) + (timePastRequirement - 60.0) / 7.0),
            0,
            100
        );
    }

    public static long speedGraceSeconds(int floor, boolean masterMode) {
        if (masterMode) {
            return switch (floor) {
                case 6 -> 10L * 60L;
                case 7 -> 14L * 60L;
                default -> 8L * 60L;
            };
        }
        return switch (floor) {
            case 4, 6 -> 12L * 60L;
            case 7 -> 14L * 60L;
            default -> 10L * 60L;
        };
    }
}
