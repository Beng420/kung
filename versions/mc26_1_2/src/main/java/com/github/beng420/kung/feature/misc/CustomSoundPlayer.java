package com.github.beng420.kung.feature.misc;

import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/** One reusable device stream, rather than opening a new audio device line for every hit. */
final class CustomSoundPlayer {
    private static final AudioFormat FORMAT = new AudioFormat(CustomSoundMixer.OUTPUT_RATE, 16, 2, true, false);
    private static final int CHUNK_BYTES = 256 * CustomSoundMixer.FRAME_BYTES;
    private final Consumer<String> diagnostic;
    private final LineFactory lines;
    private Session session;
    private long retryAtNanos;

    CustomSoundPlayer(Consumer<String> diagnostic) {
        this(diagnostic, () -> AudioSystem.getSourceDataLine(FORMAT));
    }

    CustomSoundPlayer(Consumer<String> diagnostic, LineFactory lines) {
        this.diagnostic = diagnostic;
        this.lines = lines;
    }

    synchronized void play(CustomSoundMixer.Sample sample, float volume, float pitch) {
        if (System.nanoTime() < retryAtNanos) {
            return;
        }
        if (session == null) {
            Session next = new Session();
            next.mixer.play(sample, volume, pitch);
            session = next;
            Thread worker = new Thread(() -> run(next), "Kung Custom Audio");
            worker.setDaemon(true);
            worker.start();
        } else {
            session.mixer.play(sample, volume, pitch);
        }
    }

    synchronized void stop() {
        if (session != null) {
            session.running = false;
            SourceDataLine output = session.output;
            if (output != null) {
                // Closing also releases a writer blocked inside the device; never drain obsolete cues.
                output.close();
            }
            session = null;
        }
    }

    private void run(Session current) {
        try (SourceDataLine line = lines.create()) {
            line.open(FORMAT, CHUNK_BYTES * 4);
            current.output = line;
            if (!current.running) {
                return;
            }
            line.start();
            diagnostic.accept("output opened rate=48000 channels=centered bufferBytes=" + line.getBufferSize());
            byte[] chunk = new byte[CHUNK_BYTES];
            while (current.running) {
                current.mixer.mix(chunk);
                int offset = 0;
                while (current.running && offset < chunk.length) {
                    int written = line.write(chunk, offset, chunk.length - offset);
                    if (written <= 0) {
                        throw new IllegalStateException("Audio output stopped accepting samples");
                    }
                    offset += written;
                }
            }
            line.stop();
            line.flush();
        } catch (LineUnavailableException | IllegalArgumentException | IllegalStateException exception) {
            if (current.running) {
                diagnostic.accept("output failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
            synchronized (this) {
                if (session == current) {
                    retryAtNanos = System.nanoTime() + 5_000_000_000L;
                }
            }
        } finally {
            synchronized (this) {
                if (session == current) {
                    session = null;
                }
            }
        }
    }

    private static final class Session {
        private final CustomSoundMixer mixer = new CustomSoundMixer();
        private volatile boolean running = true;
        private volatile SourceDataLine output;
    }

    @FunctionalInterface
    interface LineFactory {
        SourceDataLine create() throws LineUnavailableException;
    }
}
