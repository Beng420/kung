package com.github.beng420.kung.feature;

import com.github.beng420.kung.runtime.AppServices;

public interface Feature {
    void initialize(AppServices services);

    boolean isEnabled();

    default void reset() { }

    default void shutdown() { }
}
