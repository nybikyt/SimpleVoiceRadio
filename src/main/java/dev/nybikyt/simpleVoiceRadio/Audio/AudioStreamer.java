package dev.nybikyt.simpleVoiceRadio.Audio;

import de.maxhenkel.voicechat.api.opus.OpusEncoder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AudioStreamer {

    private static final float TARGET_RATE = 48000.0f;
    private static final int FRAME_SIZE = 960;
    private static final long FRAME_DURATION_MS = 20L;
    private static final int READ_BLOCK_FRAMES = 2048;

    private static final AtomicBoolean STREAMING = new AtomicBoolean(false);
    private static volatile boolean stopRequested = false;

    private AudioStreamer() {
    }

    public static boolean isStreaming() {
        return STREAMING.get();
    }

    public static void stopStreaming() {
        stopRequested = true;
    }

    @FunctionalInterface
    public interface StreamSource {
        AudioInputStream open() throws Exception;
    }

    @FunctionalInterface
    public interface AudioChunkListener {
        void onChunk(byte[] opusChunk);
    }

    public static void probe(StreamSource source) throws Exception {
        ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(AudioStreamer.class.getClassLoader());
        try (AudioInputStream rawStream = source.open();
             AudioInputStream pcmStream = toPcm16(rawStream)) {
            pcmStream.getFormat();
        } finally {
            Thread.currentThread().setContextClassLoader(previousLoader);
        }
    }

    public static CompletableFuture<Void> streamAudio(StreamSource source, OpusEncoder opusEncoder, AudioChunkListener listener, RadioAudioEffect audioEffect, boolean applyEffect, boolean loop) {
        if (!STREAMING.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Already streaming"));
        }
        stopRequested = false;
        return CompletableFuture.runAsync(() -> {
            ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(AudioStreamer.class.getClassLoader());
            try {
                do {
                    try (AudioInputStream rawStream = source.open()) {
                        streamDecoded(rawStream, opusEncoder, listener, audioEffect, applyEffect);
                    }
                } while (loop && !stopRequested);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                throw new RuntimeException("Audio streaming failed", e);
            } finally {
                Thread.currentThread().setContextClassLoader(previousLoader);
                STREAMING.set(false);
                stopRequested = false;
            }
        });
    }

    private static void streamDecoded(AudioInputStream rawStream, OpusEncoder opusEncoder, AudioChunkListener listener, RadioAudioEffect audioEffect, boolean applyEffect) throws Exception {
        try (AudioInputStream pcmStream = toPcm16(rawStream)) {
            AudioFormat format = pcmStream.getFormat();
            int channels = Math.max(1, format.getChannels());
            float sourceRate = format.getSampleRate() > 0 ? format.getSampleRate() : TARGET_RATE;

            FramePump pump = new FramePump(opusEncoder, listener, audioEffect, applyEffect);
            LinearResampler resampler = sourceRate == TARGET_RATE ? null : new LinearResampler(sourceRate / TARGET_RATE);

            byte[] buffer = new byte[READ_BLOCK_FRAMES * channels * 2];
            int bytesRead;
            while (!stopRequested && (bytesRead = pcmStream.read(buffer)) != -1) {
                if (bytesRead == 0) continue;
                short[] mono = toMono(buffer, bytesRead, channels);
                if (resampler == null) {
                    pump.accept(mono, mono.length);
                } else {
                    resampler.process(mono, mono.length, pump);
                }
            }
            pump.finish();
        }
    }

    private static AudioInputStream toPcm16(AudioInputStream rawStream) {
        AudioFormat base = rawStream.getFormat();
        if (base.getEncoding() == AudioFormat.Encoding.PCM_SIGNED && base.getSampleSizeInBits() == 16 && !base.isBigEndian()) {
            return rawStream;
        }
        float rate = base.getSampleRate() > 0 ? base.getSampleRate() : TARGET_RATE;
        int channels = Math.max(1, base.getChannels());
        AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, channels, channels * 2, rate, false);
        return AudioSystem.getAudioInputStream(target, rawStream);
    }

    private static short[] toMono(byte[] buffer, int bytes, int channels) {
        int frames = bytes / (2 * channels);
        short[] mono = new short[frames];
        int index = 0;
        for (int frame = 0; frame < frames; frame++) {
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                int low = buffer[index++] & 0xFF;
                int high = buffer[index++];
                sum += (short) ((high << 8) | low);
            }
            mono[frame] = (short) (sum / channels);
        }
        return mono;
    }

    private static final class FramePump {

        private final OpusEncoder encoder;
        private final AudioChunkListener listener;
        private final RadioAudioEffect effect;
        private final boolean applyEffect;
        private final short[] frame = new short[FRAME_SIZE];
        private int filled = 0;
        private long nextFrameTime = System.currentTimeMillis();

        private FramePump(OpusEncoder encoder, AudioChunkListener listener, RadioAudioEffect effect, boolean applyEffect) {
            this.encoder = encoder;
            this.listener = listener;
            this.effect = effect;
            this.applyEffect = applyEffect;
        }

        void accept(short[] samples, int length) throws InterruptedException {
            for (int i = 0; i < length; i++) {
                add(samples[i]);
            }
        }

        void add(short sample) throws InterruptedException {
            frame[filled++] = sample;
            if (filled == FRAME_SIZE) emit();
        }

        void finish() throws InterruptedException {
            if (filled > 0) {
                Arrays.fill(frame, filled, FRAME_SIZE, (short) 0);
                filled = FRAME_SIZE;
                emit();
            }
        }

        private void emit() throws InterruptedException {
            filled = 0;
            if (stopRequested) return;

            if (applyEffect && effect != null) {
                effect.apply(frame);
            }

            byte[] opusData = encoder.encode(frame);
            if (opusData != null && opusData.length > 0) {
                listener.onChunk(opusData);
            }

            nextFrameTime += FRAME_DURATION_MS;
            long sleepTime = nextFrameTime - System.currentTimeMillis();
            if (sleepTime > 0) {
                Thread.sleep(sleepTime);
            }
        }
    }

    private static final class LinearResampler {

        private final double step;
        private double position = 0.0;
        private short carry;
        private boolean hasCarry = false;

        private LinearResampler(double step) {
            this.step = step;
        }

        void process(short[] source, int length, FramePump pump) throws InterruptedException {
            if (length == 0) return;

            short[] data;
            int limit;
            if (hasCarry) {
                data = new short[length + 1];
                data[0] = carry;
                System.arraycopy(source, 0, data, 1, length);
                limit = length + 1;
            } else {
                data = source;
                limit = length;
            }

            int index;
            while ((index = (int) position) + 1 < limit) {
                double fraction = position - index;
                pump.add((short) Math.round(data[index] + (data[index + 1] - data[index]) * fraction));
                position += step;
            }

            carry = data[limit - 1];
            hasCarry = true;
            position -= (limit - 1);
        }
    }
}
