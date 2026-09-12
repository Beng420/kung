package com.github.beng420.kung.feature.misc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.Test;

public final class CustomSoundMixerTest {
    @Test
    public void stereoImportsAreCenteredInBothEars() {
        CustomSoundMixer mixer = new CustomSoundMixer();
        mixer.play(CustomSoundMixer.decodePcm(pcm(12_000, 0, -4_000, 2_000), 2, 48_000), 1, 1);
        byte[] output = new byte[12];
        mixer.mix(output);
        assertArrayEquals(pcm(6_000, 6_000, -1_000, -1_000, 0, 0), output);
    }

    @Test
    public void pitchIsResampledWithoutChangingTheOutputDeviceRate() {
        CustomSoundMixer.Sample sample = CustomSoundMixer.decodePcm(pcm(0, 1_000, 2_000, 3_000), 1, 48_000);
        CustomSoundMixer high = new CustomSoundMixer();
        high.play(sample, 1, 2);
        byte[] fast = new byte[12];
        high.mix(fast);
        assertArrayEquals(pcm(0, 0, 2_000, 2_000, 0, 0), fast);
        CustomSoundMixer low = new CustomSoundMixer();
        low.play(sample, 1, .5f);
        byte[] slow = new byte[12];
        low.mix(slow);
        assertArrayEquals(pcm(0, 0, 500, 500, 1_000, 1_000), slow);
    }

    @Test
    public void sampleRateAndBufferBoundariesDoNotResetThePlaybackCursor() {
        CustomSoundMixer mixer = new CustomSoundMixer();
        mixer.play(CustomSoundMixer.decodePcm(pcm(0, 1_000, 2_000), 1, 24_000), 1, 1);
        byte[] first = new byte[12];
        byte[] second = new byte[12];
        mixer.mix(first);
        mixer.mix(second);
        assertArrayEquals(pcm(0, 0, 500, 500, 1_000, 1_000), first);
        assertArrayEquals(pcm(1_500, 1_500, 2_000, 2_000, 2_000, 2_000), second);
        assertEquals(0, mixer.activeVoices());
    }

    @Test
    public void overlappingHitsMixImmediatelyAndSaturateInsteadOfWrapping() {
        CustomSoundMixer mixer = new CustomSoundMixer();
        CustomSoundMixer.Sample sample = CustomSoundMixer.decodePcm(pcm(30_000, -30_000), 1, 48_000);
        mixer.play(sample, 1, 1);
        mixer.play(sample, 1, 1);
        byte[] output = new byte[8];
        mixer.mix(output);
        assertArrayEquals(pcm(32_767, 32_767, -32_768, -32_768), output);
        assertEquals(0, mixer.activeVoices());
    }

    @Test
    public void hitBurstsRemainBoundedAndKeepTheNewestCues() {
        CustomSoundMixer mixer = new CustomSoundMixer();
        mixer.play(CustomSoundMixer.decodePcm(pcm(10_000), 1, 48_000), 1, 1);
        for (int i = 0; i < 32; i++) {
            mixer.play(CustomSoundMixer.decodePcm(pcm(1), 1, 48_000), 1, 1);
        }
        assertEquals(32, mixer.activeVoices());
        byte[] output = new byte[4];
        mixer.mix(output);
        assertArrayEquals(pcm(32, 32), output);
    }

    @Test
    public void bundledWavFilesDecodeAndFinishAtEverySupportedPitchWithoutAnAudioDevice() throws Exception {
        for (String name : new String[] {"kung_arrow_ping.wav", "kung_arrow_plink.wav", "kung_wither_fade.wav", "kung_wither_chime.wav"}) {
            try (var resource = new BufferedInputStream(getClass().getResourceAsStream("/assets/kung/sounds/custom/" + name));
                 AudioInputStream input = AudioSystem.getAudioInputStream(resource)) {
                AudioFormat format = input.getFormat();
                AudioFormat decodedFormat = new AudioFormat(format.getSampleRate(), 16, format.getChannels(), true, false);
                try (AudioInputStream decoded = AudioSystem.getAudioInputStream(decodedFormat, input)) {
                    CustomSoundMixer.Sample sample = CustomSoundMixer.decodePcm(decoded.readAllBytes(), format.getChannels(), format.getSampleRate());
                    assertTrue(name, sample.mono().length > 0);
                    for (float pitch : new float[] {.25f, 1f, 3f}) {
                        CustomSoundMixer mixer = new CustomSoundMixer();
                        mixer.play(sample, 1, pitch);
                        int expectedFrames = (int) Math.ceil(sample.mono().length * 48_000d / (sample.sampleRate() * pitch));
                        byte[] output = new byte[(expectedFrames + 1) * 4];
                        mixer.mix(output);
                        boolean audible = false;
                        for (int offset = 0; offset < output.length; offset += 4) {
                            assertEquals(output[offset], output[offset + 2]);
                            assertEquals(output[offset + 1], output[offset + 3]);
                            audible |= output[offset] != 0 || output[offset + 1] != 0;
                        }
                        assertTrue(name, audible);
                        assertEquals(name, 0, mixer.activeVoices());
                    }
                }
            }
        }
    }

    private static byte[] pcm(int... samples) {
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            bytes[i * 2] = (byte) samples[i];
            bytes[i * 2 + 1] = (byte) (samples[i] >> 8);
        }
        return bytes;
    }
}
