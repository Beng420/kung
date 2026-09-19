package com.github.beng420.kung.runtime;

import java.util.UUID;
import net.minecraft.client.Minecraft;

/** Local account identity, independent of server-provided names or player UUIDs. */
public final class KungDeveloperAccess {
    private static final UUID BENG114 = UUID.fromString("69617dbf-568e-4632-9ee9-80bf67d534d9");

    private KungDeveloperAccess() { }

    public static boolean allowed() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.getUser() != null && allowed(client.getUser().getProfileId());
    }

    public static boolean allowed(UUID profileId) { return BENG114.equals(profileId); }
}
