package dev.nybikyt.simpleVoiceRadio.Audio;

import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import java.util.Random;

public class RadioAudioEffect {

    private static final float SAMPLE_RATE = 48000.0f;
    private static final int FRAME_SIZE = 960;
    private static final float MAX_SHORT = 32767.0f;
    private static final float LN_2 = (float) Math.log(2.0);

    private final Random random = new Random();
    private final PluginConfig config;

    private float severity;
    private float centerFrequency;
    private float bandwidth;
    private float[] coefficients;

    private float lastInputSample1;
    private float lastInputSample2;
    private float lastOutputSample1;
    private float lastOutputSample2;

    public RadioAudioEffect(PluginConfig config) {
        this.config = config;
    }

    public void apply(short[] data) {
        if (data.length != FRAME_SIZE) return;

        refreshCoefficients();
        float severity = this.severity;
        float[] coeffs = this.coefficients;

        float[] floats = new float[FRAME_SIZE];
        for (int i = 0; i < FRAME_SIZE; i++) {
            float sample = data[i] / MAX_SHORT;
            if (random.nextFloat() < severity) sample = 0.0f;
            floats[i] = bandpassFilter(sample, coeffs);
        }

        float maxValue = 1.0f;
        for (float f : floats) {
            float abs = Math.abs(f);
            if (abs > maxValue) maxValue = abs;
        }

        float factor = MAX_SHORT / maxValue;
        for (int i = 0; i < FRAME_SIZE; i++) {
            data[i] = (short) Math.max(-32768, Math.min(32767, (int) (floats[i] * factor)));
        }
    }

    private void refreshCoefficients() {
        float newSeverity = Math.max(0.0f, Math.min(1.0f, config.effectSeverity()));
        float newCenterFrequency = config.effectCenterFrequency();
        float newBandwidth = config.effectBandwidth();

        if (coefficients != null && newSeverity == severity && newCenterFrequency == centerFrequency && newBandwidth == bandwidth) {
            return;
        }

        severity = newSeverity;
        centerFrequency = newCenterFrequency;
        bandwidth = newBandwidth;
        coefficients = computeCoefficients(centerFrequency, bandwidth, severity);
    }

    private float[] computeCoefficients(float centerFrequency, float bandwidth, float severity) {
        float normalizedCenterFrequency = 2.0f * centerFrequency / SAMPLE_RATE;
        float normalizedBandwidth = 2.0f * bandwidth / SAMPLE_RATE;
        float adjustedBandwidth = normalizedBandwidth * (1.0f - severity * 0.1f);

        float w0 = (float) (2.0 * Math.PI) * normalizedCenterFrequency;
        float sin = (float) Math.sin(w0);
        float cos = (float) Math.cos(w0);
        float alpha = sin * (float) Math.sinh(LN_2 * 0.5f * adjustedBandwidth * w0 / sin);

        float a0 = 1.0f + alpha;
        float b0 = (1.0f - cos) * 0.5f;
        float b1 = 1.0f - cos;
        float b2 = b0;
        float a1 = -2.0f * cos;
        float a2 = 1.0f - alpha;

        return new float[]{b0, b1, b2, a0, a1, a2};
    }

    private float bandpassFilter(float inputSample, float[] coeffs) {
        float b0 = coeffs[0], b1 = coeffs[1], b2 = coeffs[2];
        float a0 = coeffs[3], a1 = coeffs[4], a2 = coeffs[5];

        float filteredSample = (b0 * inputSample + b1 * lastInputSample1 + b2 * lastInputSample2
                - a1 * lastOutputSample1 - a2 * lastOutputSample2) / a0;

        lastInputSample2 = lastInputSample1;
        lastInputSample1 = inputSample;
        lastOutputSample2 = lastOutputSample1;
        lastOutputSample1 = filteredSample;

        return filteredSample;
    }
}
