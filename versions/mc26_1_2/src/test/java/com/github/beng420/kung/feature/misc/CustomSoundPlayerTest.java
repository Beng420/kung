package com.github.beng420.kung.feature.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.SourceDataLine;
import org.junit.Test;

public final class CustomSoundPlayerTest {
    @Test
    public void rapidHitsShareOneOutputAndStoppingReleasesABlockedWrite() throws Exception {
        FakeLine line = new FakeLine();
        AtomicInteger opened = new AtomicInteger();
        List<String> diagnostics = new CopyOnWriteArrayList<>();
        CustomSoundPlayer player = new CustomSoundPlayer(diagnostics::add, () -> {
            opened.incrementAndGet();
            return line.proxy();
        });
        try {
            player.play(sample(100), 1, 1);
            assertTrue(line.writing.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 100; i++) {
                player.play(sample(i), 1, 1);
            }
            assertEquals(1, opened.get());
            player.stop();
            assertTrue(line.finishedWriting.await(2, TimeUnit.SECONDS));
            assertEquals(0, diagnostics.stream().filter(message -> message.contains("failed")).count());
        } finally {
            player.stop();
        }
    }

    @Test
    public void restartingUsesAFreshSessionWithoutPendingOldCues() throws Exception {
        FakeLine first = new FakeLine();
        FakeLine second = new FakeLine();
        AtomicInteger opened = new AtomicInteger();
        CustomSoundPlayer player = new CustomSoundPlayer(message -> { }, () ->
            opened.getAndIncrement() == 0 ? first.proxy() : second.proxy());
        try {
            player.play(sample(123), 1, 1);
            assertTrue(first.writing.await(2, TimeUnit.SECONDS));
            player.play(sample(500), 1, 1);
            player.stop();
            player.play(sample(456), 1, 1);
            assertTrue(second.writing.await(2, TimeUnit.SECONDS));
            assertEquals(456, second.firstSample);
            assertEquals(2, opened.get());
        } finally {
            player.stop();
        }
    }

    private static CustomSoundMixer.Sample sample(int value) {
        return new CustomSoundMixer.Sample(new float[] {value}, 48_000);
    }

    private static final class FakeLine {
        private final CountDownLatch writing = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);
        private final CountDownLatch finishedWriting = new CountDownLatch(1);
        private volatile int firstSample;

        SourceDataLine proxy() {
            return (SourceDataLine) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {SourceDataLine.class},
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "getBufferSize": return 4096;
                        case "write":
                            byte[] pcm = (byte[]) arguments[0];
                            firstSample = (short) ((pcm[0] & 0xff) | (pcm[1] << 8));
                            writing.countDown();
                            closed.await(3, TimeUnit.SECONDS);
                            finishedWriting.countDown();
                            return 0;
                        case "close": closed.countDown(); return null;
                        default: return null;
                    }
                });
        }
    }
}
