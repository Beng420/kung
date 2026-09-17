package com.github.beng420.kung.config.category;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class HitboxesConfig extends ConfigCategory {
    public static final int DEFAULT_COLOR = 0xFF55FF55;
    private boolean enabled;
    private boolean dragonOverallBox = true;
    private boolean dragonPartBoxes = true;
    private Map<String, Integer> entities = new LinkedHashMap<>();

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; save(); }
    public boolean dragonOverallBox() { return dragonOverallBox; }
    public void setDragonOverallBox(boolean value) { dragonOverallBox = value; save(); }
    public boolean dragonPartBoxes() { return dragonPartBoxes; }
    public void setDragonPartBoxes(boolean value) { dragonPartBoxes = value; save(); }
    public Map<String, Integer> entities() { return Collections.unmodifiableMap(entities); }

    public void add(String id) {
        Identifier key = id == null ? null : Identifier.tryParse(id);
        if (key != null && entities.putIfAbsent(key.toString(), DEFAULT_COLOR) == null) save();
    }

    public void remove(String id) {
        if (entities.remove(id) != null) save();
    }

    public void setColor(String id, int color) {
        if (entities.containsKey(id)) {
            entities.put(id, color | 0xFF000000);
            save();
        }
    }

    public void normalize() {
        if (entities == null) entities = new LinkedHashMap<>();
        Map<String, Integer> valid = new LinkedHashMap<>();
        entities.forEach((id, color) -> {
            Identifier key = id == null ? null : Identifier.tryParse(id);
            if (key != null && color != null) valid.put(key.toString(), color | 0xFF000000);
        });
        entities = valid;
    }
}
