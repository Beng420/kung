package com.github.beng420.kung;

/**
 * Build flavor, picked by Gradle from src/full or src/modrinth (-Pmodrinth).
 * A compile-time constant: javac drops the branches of the other flavor, so
 * {@code if (!KungBuild.MODRINTH)} code is not in the Modrinth jar at all.
 */
public final class KungBuild {
    public static final boolean MODRINTH = true;

    private KungBuild() {
    }
}
