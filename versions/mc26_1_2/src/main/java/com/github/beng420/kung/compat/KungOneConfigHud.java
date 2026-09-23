package com.github.beng420.kung.compat;

import com.github.beng420.kung.config.KungHudLayout;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.hud.v1.OneConfigHudWrapper;

/** OneConfig uses GUI units and a scale multiplier; Kung owns every saved value. */
final class KungOneConfigHud implements OneConfigHudWrapper {
    private final KungHudLayout.Entry layout;
    private final List<Property<?>> properties;
    private final BooleanSupplier writesAllowed;
    private String id;
    private String name;

    KungOneConfigHud(KungHudLayout.Entry layout, List<Property<?>> properties, BooleanSupplier writesAllowed) {
        this.layout = layout;
        this.properties = List.copyOf(properties);
        this.writesAllowed = writesAllowed;
        id = "kung_" + layout.id();
        name = layout.name();
    }

    @Override public String getId() { return id; }
    @Override public void setId(String id) { this.id = id; }
    @Override public String getName() { return name; }
    @Override public void setName(String name) { this.name = name; }
    @Override public String getModId() { return "kung"; }
    @Override public boolean getOwnsPlacement() { return true; }
    @Override public float getX() { return layout.bounds().x(); }
    @Override public float getY() { return layout.bounds().y(); }
    @Override public void setX(float x) { if (writesAllowed.getAsBoolean()) layout.moveX(x); }
    @Override public void setY(float y) { if (writesAllowed.getAsBoolean()) layout.moveY(y); }
    @Override public float getScale() { return layout.scale(); }
    @Override public void setScale(float scale) { if (writesAllowed.getAsBoolean()) layout.scale(scale); }
    // OneConfig's editor draws hidden HUDs dimmed; a switched-off Kung HUD takes no space there at all.
    @Override public float getScaledWidth() { return layout.enabled() ? layout.bounds().width() : 0; }
    @Override public float getScaledHeight() { return layout.enabled() ? layout.bounds().height() : 0; }
    // Proportional resize goes through setScale; dimensions are measured by Kung's renderers.
    @Override public void setScaledWidth(float width) { }
    @Override public void setScaledHeight(float height) { }
    @Override public boolean getHidden() { return !layout.enabled(); }
    @Override public void setHidden(boolean hidden) {
        if (writesAllowed.getAsBoolean()) layout.enabled(!hidden);
    }
    @Override public List<Property<?>> linkedProperties() { return properties; }
}
