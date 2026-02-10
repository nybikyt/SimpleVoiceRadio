package org.nyt.simpleVoiceRadio.Misc;

import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;
import java.util.Random;
import java.util.stream.IntStream;

public class RadioAudioEffect {
    private static final float SAMPLE_RATE = 48000.0f;
    private static final int FRAME_SIZE = 960;
    private static final float MAX_SHORT = 32767.0f;
    private static final float LN_2 = (float) Math.log(2.0);

    private final Random random = new Random();
    private final SimpleVoiceRadio plugin;

    private float lastInputSample1;
    private float lastInputSample2;
    private float lastOutputSample1;
    private float lastOutputSample2;

    public RadioAudioEffect(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
    }

    public void apply(short[] data) {
        if (data.length != FRAME_SIZE) return;

        float severity = Math.max(0.0f, Math.min(1.0f,
                (float) plugin.getConfig().getDouble("audio-effects.severity", 0.05)));

        IntStream.range(0, FRAME_SIZE)
                .filter(i -> random.nextFloat() < severity)
                .forEach(i -> data[i] = 0);

        float[] floats = new float[FRAME_SIZE];
        IntStream.range(0, FRAME_SIZE).forEach(i -> floats[i] = data[i] / MAX_SHORT);

        float maxValue = MAX_SHORT;
        for (int i = 0; i < FRAME_SIZE; i++) {
            float sample = floats[i] = bandpassFilter(floats[i]) * MAX_SHORT;
            float abs = Math.abs(sample);
            if (abs > maxValue) {
                maxValue = abs;
            }
        }

        float factor = MAX_SHORT / maxValue;
        IntStream.range(0, FRAME_SIZE)
                .forEach(i -> data[i] = (short) (int) (floats[i] * factor));
    }

    private float bandpassFilter(float inputSample) {
        float centerFrequency = (float) plugin.getConfig().getDouble("audio-effects.center_frequency", 750.0);
        float bandwidth = (float) plugin.getConfig().getDouble("audio-effects.bandwidth", 4000.0);
        float severity = Math.max(0.0f, Math.min(1.0f, (float) plugin.getConfig().getDouble("audio-effects.severity", 0.05)));

        float normalizedCenterFrequency = 2.0f * centerFrequency / SAMPLE_RATE;
        float normalizedBandwidth = 2.0f * bandwidth / SAMPLE_RATE;

        float adjustedBandwidth = normalizedBandwidth * (1.0f - severity * 0.1f);

        float w0 = (float) (2.0 * Math.PI) * normalizedCenterFrequency;
        float sin = (float) Math.sin(w0);
        float alpha = sin * (float) Math.sinh(LN_2 * 0.5f * adjustedBandwidth * w0 / sin);
        float a0 = 1.0f + alpha;
        float cos = (float) Math.cos(w0);
        float b0 = (1.0f - cos) * 0.5f;
        float b1 = 1.0f - cos;
        float b2 = b0;
        float a1 = -2.0f * cos;
        float a2 = 1.0f - alpha;

        float filteredSample = (b0 * inputSample + b1 * lastInputSample1 + b2 * lastInputSample2 - a1 * lastOutputSample1 - a2 * lastOutputSample2) / a0;

        lastInputSample2 = lastInputSample1;
        lastInputSample1 = inputSample;
        lastOutputSample2 = lastOutputSample1;
        lastOutputSample1 = filteredSample;

        return filteredSample;
    }
}