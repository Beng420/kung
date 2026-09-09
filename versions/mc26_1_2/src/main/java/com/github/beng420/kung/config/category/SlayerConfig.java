package com.github.beng420.kung.config.category;

public final class SlayerConfig extends ConfigCategory {
    private boolean tarantulaHelperEnabled = false;
    private boolean eggSacPredictionRendererEnabled = false;
    private boolean eggSacPredictionEnabled = false;
    private EggSacPredictionRenderMode eggSacPredictionRenderMode = EggSacPredictionRenderMode.BOX;
    private boolean tarantulaDebugSlayerSpawned = true;
    private boolean tarantulaDebugSlayerPosition = true;
    private boolean tarantulaDebugSlayerPhaseChange = true;
    private boolean tarantulaDebugSlayerDead = true;
    private boolean tarantulaDebugEggSacPhaseStart = true;
    private boolean tarantulaDebugEggSacPhaseDone = true;
    private boolean tarantulaDebugEggSacLearning = true;

    public boolean tarantulaHelperEnabled() { return tarantulaHelperEnabled; }
    public void setTarantulaHelperEnabled(boolean value) { tarantulaHelperEnabled = value; save(); }
    public boolean eggSacPredictionRendererEnabled() { return eggSacPredictionRendererEnabled; }
    public void setEggSacPredictionRendererEnabled(boolean value) { eggSacPredictionRendererEnabled = value; save(); }
    public boolean eggSacPredictionEnabled() { return eggSacPredictionEnabled; }
    public void setEggSacPredictionEnabled(boolean value) { eggSacPredictionEnabled = value; save(); }
    public EggSacPredictionRenderMode eggSacPredictionRenderMode() { return eggSacPredictionRenderMode; }
    public String eggSacPredictionRenderModeLabel() { return eggSacPredictionRenderMode.label(); }
    public void cycleEggSacPredictionRenderMode() { setEggSacPredictionRenderMode(eggSacPredictionRenderMode.next()); }
    public void setEggSacPredictionRenderMode(EggSacPredictionRenderMode value) {
        eggSacPredictionRenderMode = value == null ? EggSacPredictionRenderMode.BOX : value;
        save();
    }
    public boolean tarantulaDebugSlayerSpawned() { return tarantulaDebugSlayerSpawned; }
    public void setTarantulaDebugSlayerSpawned(boolean value) { tarantulaDebugSlayerSpawned = value; save(); }
    public boolean tarantulaDebugSlayerPosition() { return tarantulaDebugSlayerPosition; }
    public void setTarantulaDebugSlayerPosition(boolean value) { tarantulaDebugSlayerPosition = value; save(); }
    public boolean tarantulaDebugSlayerPhaseChange() { return tarantulaDebugSlayerPhaseChange; }
    public void setTarantulaDebugSlayerPhaseChange(boolean value) { tarantulaDebugSlayerPhaseChange = value; save(); }
    public boolean tarantulaDebugSlayerDead() { return tarantulaDebugSlayerDead; }
    public void setTarantulaDebugSlayerDead(boolean value) { tarantulaDebugSlayerDead = value; save(); }
    public boolean tarantulaDebugEggSacPhaseStart() { return tarantulaDebugEggSacPhaseStart; }
    public void setTarantulaDebugEggSacPhaseStart(boolean value) { tarantulaDebugEggSacPhaseStart = value; save(); }
    public boolean tarantulaDebugEggSacPhaseDone() { return tarantulaDebugEggSacPhaseDone; }
    public void setTarantulaDebugEggSacPhaseDone(boolean value) { tarantulaDebugEggSacPhaseDone = value; save(); }
    public boolean tarantulaDebugEggSacLearning() { return tarantulaDebugEggSacLearning; }
    public void setTarantulaDebugEggSacLearning(boolean value) { tarantulaDebugEggSacLearning = value; save(); }

    public enum EggSacPredictionRenderMode {
        BOX("Quader"),
        GRID("Grid");

        private final String label;

        EggSacPredictionRenderMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public EggSacPredictionRenderMode next() {
            EggSacPredictionRenderMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
