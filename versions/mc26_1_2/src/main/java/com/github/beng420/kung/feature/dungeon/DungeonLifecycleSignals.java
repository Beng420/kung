package com.github.beng420.kung.feature.dungeon;

import java.util.regex.Pattern;

public final class DungeonLifecycleSignals {
    private static final Pattern RUN_START = Pattern.compile("^Starting in 1 second\\.$");
    private static final Pattern RUN_FINISHED = Pattern.compile(
        "^\\s*(?:\\S\\s+)?Defeated\\s+(.+?)\\s+in\\s+0?([\\dhms ]+?)\\s*(?:\\(NEW RECORD!\\))?$"
    );
    private static final Pattern TEAM_SCORE = Pattern.compile("^Team Score: \\d{1,3} \\([SABCDUF][+]?\\)$");

    private DungeonLifecycleSignals() {
    }

    public static boolean isRunStart(String message) {
        return message != null && RUN_START.matcher(clean(message)).matches();
    }

    public static boolean isRunFinished(String message) {
        if (message == null) return false;
        String text = clean(message);
        return RUN_FINISHED.matcher(text).matches() || TEAM_SCORE.matcher(text).matches();
    }

    public static String clean(String message) {
        return message == null ? "" : message.replaceAll("\u00a7.", "").replaceAll("\\s+", " ").trim();
    }
}
