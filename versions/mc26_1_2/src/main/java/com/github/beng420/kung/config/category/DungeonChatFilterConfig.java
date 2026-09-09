package com.github.beng420.kung.config.category;

public final class DungeonChatFilterConfig extends ConfigCategory {
    private boolean enabled = false;
    private boolean blessings = true;
    private boolean lootSpam = true;
    private boolean watcher = true;
    private boolean bossMessages = false;
    private boolean bonzo = true;
    private boolean scarf = true;
    private boolean professor = true;
    private boolean thorn = true;
    private boolean livid = true;
    private boolean sadan = true;
    private boolean maxor = true;
    private boolean storm = true;
    private boolean goldor = true;
    private boolean necron = true;
    private boolean witherKing = true;

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; save(); }
    public boolean blessings() { return blessings; }
    public void setBlessings(boolean value) { blessings = value; save(); }
    public boolean lootSpam() { return lootSpam; }
    public void setLootSpam(boolean value) { lootSpam = value; save(); }
    public boolean watcher() { return watcher; }
    public void setWatcher(boolean value) { watcher = value; save(); }
    public boolean bossMessages() { return bossMessages; }
    public void setBossMessages(boolean value) { bossMessages = value; save(); }
    public boolean bonzo() { return bonzo; }
    public void setBonzo(boolean value) { bonzo = value; save(); }
    public boolean scarf() { return scarf; }
    public void setScarf(boolean value) { scarf = value; save(); }
    public boolean professor() { return professor; }
    public void setProfessor(boolean value) { professor = value; save(); }
    public boolean thorn() { return thorn; }
    public void setThorn(boolean value) { thorn = value; save(); }
    public boolean livid() { return livid; }
    public void setLivid(boolean value) { livid = value; save(); }
    public boolean sadan() { return sadan; }
    public void setSadan(boolean value) { sadan = value; save(); }
    public boolean maxor() { return maxor; }
    public void setMaxor(boolean value) { maxor = value; save(); }
    public boolean storm() { return storm; }
    public void setStorm(boolean value) { storm = value; save(); }
    public boolean goldor() { return goldor; }
    public void setGoldor(boolean value) { goldor = value; save(); }
    public boolean necron() { return necron; }
    public void setNecron(boolean value) { necron = value; save(); }
    public boolean witherKing() { return witherKing; }
    public void setWitherKing(boolean value) { witherKing = value; save(); }
}
