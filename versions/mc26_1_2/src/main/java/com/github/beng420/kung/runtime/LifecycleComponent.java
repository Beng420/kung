package com.github.beng420.kung.runtime;

public interface LifecycleComponent {
    void initialize(AppServices services);

    default void reset() {
    }

    default void shutdown() {
    }
}
