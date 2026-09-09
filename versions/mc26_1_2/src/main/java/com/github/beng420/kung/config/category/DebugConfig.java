package com.github.beng420.kung.config.category;

public final class DebugConfig extends ConfigCategory {
    private boolean enabled = false;
    private boolean contextMessages = true;
    private boolean dungeonMessages = true;
    private boolean interfaceMessages = true;
    private boolean tarantulaMessages = true;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public boolean contextMessages() {
        return contextMessages;
    }

    public void setContextMessages(boolean contextMessages) {
        this.contextMessages = contextMessages;
        save();
    }

    public boolean dungeonMessages() {
        return dungeonMessages;
    }

    public void setDungeonMessages(boolean dungeonMessages) {
        this.dungeonMessages = dungeonMessages;
        save();
    }

    public boolean interfaceMessages() {
        return interfaceMessages;
    }

    public void setInterfaceMessages(boolean interfaceMessages) {
        this.interfaceMessages = interfaceMessages;
        save();
    }

    public boolean contextMessagesEnabled() {
        return enabled && contextMessages;
    }

    public boolean dungeonMessagesEnabled() {
        return enabled && dungeonMessages;
    }

    public boolean interfaceMessagesEnabled() {
        return enabled && interfaceMessages;
    }

    public boolean tarantulaMessagesEnabled() {
        return enabled && tarantulaMessages;
    }

    public boolean tarantulaMessages() { return tarantulaMessages; }
    public void setTarantulaMessages(boolean value) { tarantulaMessages = value; save(); }
}
