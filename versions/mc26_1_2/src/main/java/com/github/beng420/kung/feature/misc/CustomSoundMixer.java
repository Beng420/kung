package com.github.beng420.kung.feature.misc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bounded PCM mixing with identical left/right channels and no world coordinates. */
final class CustomSoundMixer {
    static final int OUTPUT_RATE = 48_000;
    static final int FRAME_BYTES = 4;
    private static final int MAX_VOICES = 32;
    private final List<Voice> voices = new ArrayList<>();
    private float[] mixed = new float[0];

    static Sample decodePcm(byte[] pcm, int channels, float sampleRate) {
        if (channels < 1 || channels > 8 || !Float.isFinite(sampleRate) || sampleRate <= 0
            || pcm.length % (channels * 2) != 0) {
            throw new IllegalArgumentException("Invalid decoded PCM format");
        }
        float[] mono = new float[pcm.length / (channels * 2)];
        for (int frame = 0; frame < mono.length; frame++) {
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                int offset = (frame * channels + channel) * 2;
                sum += (short) ((pcm[offset] & 0xff) | (pcm[offset + 1] << 8));
            }
            mono[frame] = sum / (float) channels;
        }
        return new Sample(mono, sampleRate);
    }

    synchronized void play(Sample sample, float volume, float pitch) {
        if (sample.mono().length == 0 || !Float.isFinite(volume) || volume <= 0 || !Float.isFinite(pitch)) {
            return;
        }
        // Bursts retain the newest cues instead of accumulating delayed playback or device threads.
        if (voices.size() == MAX_VOICES) {
            voices.removeFirst();
        }
        voices.add(new Voice(sample, Math.clamp(volume, 0, 5), Math.clamp(pitch, 0.25f, 3)));
    }

    synchronized void mix(byte[] output) {
        if (output.length % FRAME_BYTES != 0) {
            throw new IllegalArgumentException("Output must contain complete stereo frames");
        }
        int frames = output.length / FRAME_BYTES;
        if (mixed.length != frames) {
            mixed = new float[frames];
        } else {
            Arrays.fill(mixed, 0);
        }
        for (Voice voice : voices) {
            float[] data = voice.sample.mono();
            for (int frame = 0; frame < frames && voice.position < data.length; frame++) {
                int index = (int) voice.position;
                float fraction = (float) (voice.position - index);
                float next = data[Math.min(index + 1, data.length - 1)];
                mixed[frame] += (data[index] + (next - data[index]) * fraction) * voice.volume;
                voice.position += voice.step;
            }
        }
        voices.removeIf(voice -> voice.position >= voice.sample.mono().length);
        for (int frame = 0; frame < frames; frame++) {
            int sample = Math.clamp(Math.round(mixed[frame]), Short.MIN_VALUE, Short.MAX_VALUE);
            int offset = frame * FRAME_BYTES;
            output[offset] = output[offset + 2] = (byte) sample;
            output[offset + 1] = output[offset + 3] = (byte) (sample >> 8);
        }
    }

    synchronized int activeVoices() {
        return voices.size();
    }

    record Sample(float[] mono, float sampleRate) { }

    private static final class Voice {
        private final Sample sample;
        private final float volume;
        private final double step;
        private double position;

        private Voice(Sample sample, float volume, float pitch) {
            this.sample = sample;
            this.volume = volume;
            this.step = sample.sampleRate() * (double) pitch / OUTPUT_RATE;
        }
    }
}
