package com.github.beng420.kung.message;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class KungMessagesTest {
    @Test
    public void formatsAreaAndBody() {
        assertEquals(
            "[Kung Room Sync] Connected",
            KungMessages.success(" Room Sync ", "Connected").getString()
        );
    }

    @Test
    public void supportsMessagesWithoutArea() {
        assertEquals("[Kung] Ready", KungMessages.info("Ready").getString());
    }

    @Test
    public void handlesOptionalValues() {
        assertEquals("[Kung] ", KungMessages.component(null, null, null).getString());
    }
}
