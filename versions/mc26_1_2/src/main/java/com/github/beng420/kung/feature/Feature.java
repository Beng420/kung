package com.github.beng420.kung.feature;

import com.github.beng420.kung.runtime.LifecycleComponent;

public interface Feature extends LifecycleComponent {
    boolean isEnabled();
}
