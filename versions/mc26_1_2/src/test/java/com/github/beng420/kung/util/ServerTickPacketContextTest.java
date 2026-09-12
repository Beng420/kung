package com.github.beng420.kung.util;

import static org.junit.Assert.*;

import java.util.concurrent.CompletableFuture;
import org.junit.Test;

public final class ServerTickPacketContextTest {
    @Test
    public void bundleProvenanceIsRestoredAfterNestedDispatchAndExceptions() {
        ServerTickSequence sequence = new ServerTickSequence();
        assertTrue(sequence.accept(-10, ServerTickPacketContext.isApplyingBundle()));
        assertThrows(IllegalStateException.class, () -> ServerTickPacketContext.applyBundle(() -> {
            assertFalse(sequence.accept(-11, ServerTickPacketContext.isApplyingBundle()));
            ServerTickPacketContext.applyBundle(() ->
                assertFalse(sequence.accept(-12, ServerTickPacketContext.isApplyingBundle())));
            assertTrue(ServerTickPacketContext.isApplyingBundle());
            throw new IllegalStateException("Dispatch or enqueue failed");
        }));
        assertFalse(ServerTickPacketContext.isApplyingBundle());
        assertFalse(sequence.accept(-10, false)); // Ignored bundle IDs did not change the baseline.
        assertTrue(sequence.accept(-11, false));
        assertTrue(sequence.accept(-12, false));
    }

    @Test
    public void packetThreadScopeDoesNotLeakToAnotherThread() {
        ServerTickPacketContext.applyBundle(() -> {
            assertFalse(CompletableFuture.supplyAsync(ServerTickPacketContext::isApplyingBundle).join());
            assertTrue(ServerTickPacketContext.isApplyingBundle());
        });
        assertFalse(ServerTickPacketContext.isApplyingBundle());
    }
}
