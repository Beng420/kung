package com.github.beng420.kung.skyblock;

/** One world session owns its evidence. Missing UI rows do not create a new session. */
public final class HypixelInstanceState {
    public enum Source { SIDEBAR, TAB }
    private long epoch;
    private HypixelLocation location = HypixelLocation.UNKNOWN;
    private String serverId = "";
    private HypixelLocation sidebarLocation = HypixelLocation.UNKNOWN;
    private HypixelLocation tabLocation = HypixelLocation.UNKNOWN;

    public void beginWorld() {
        epoch++;
        location = HypixelLocation.UNKNOWN;
        serverId = "";
        sidebarLocation = HypixelLocation.UNKNOWN;
        tabLocation = HypixelLocation.UNKNOWN;
    }

    public void observe(HypixelLocation next, String server) {
        observe(Source.SIDEBAR, next, server);
    }

    /** Returns true when a changed server row invalidated the other cached rows. */
    public boolean observe(Source source, HypixelLocation next, String server) {
        if (!server.isBlank() && !serverId.isBlank() && !serverId.equals(server)) {
            beginWorld();
            serverId = server;
            return true;
        }
        if (!server.isBlank()) serverId = server;
        HypixelLocation previous = source == Source.SIDEBAR ? sidebarLocation : tabLocation;
        if (source == Source.SIDEBAR) sidebarLocation = next;
        else tabLocation = next;
        if (!next.equals(previous) && next.kind() != HypixelLocation.Kind.UNKNOWN) location = next;
        return false;
    }

    public long epoch() { return epoch; }
    public HypixelLocation location() { return location; }
    public String serverId() { return serverId; }
    public boolean catacombs() { return location.kind() == HypixelLocation.Kind.CATACOMBS; }
}
