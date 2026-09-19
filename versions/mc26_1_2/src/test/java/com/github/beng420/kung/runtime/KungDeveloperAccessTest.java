package com.github.beng420.kung.runtime;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.Test;

public final class KungDeveloperAccessTest {
    @Test public void onlyTheVerifiedAccountUuidEnablesDeveloperAccess() {
        assertTrue(KungDeveloperAccess.allowed(UUID.fromString("69617dbf-568e-4632-9ee9-80bf67d534d9")));
        assertFalse(KungDeveloperAccess.allowed(UUID.fromString("69617dbf-568e-4632-9ee9-80bf67d534d8")));
        assertFalse(KungDeveloperAccess.allowed(UUID.nameUUIDFromBytes("OfflinePlayer:Beng114".getBytes(StandardCharsets.UTF_8))));
        assertFalse(KungDeveloperAccess.allowed(null));
        assertFalse(KungDeveloperAccess.allowed());
    }
}
