package dev.nybikyt.simpleVoiceRadio.Bridges;

import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import dev.nybikyt.simpleVoiceRadio.Misc.RadioAudioEffect;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class JavaZoom {

    private static final AudioFormat PCM_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            48000.0f,
            16,
            1,
            2,
            48000.0f,
            false
    );

    private static final int FRAME_SIZE = 960;
    private static final int BYTES_PER_FRAME = FRAME_SIZE * 2;
    private static final long FRAME_DURATION_MS = 20L;

    private static volatile boolean streaming = false;
    private static volatile boolean stopRequested = false;

    public static boolean isStreaming() {
        return streaming;
    }

    public static void stopStreaming() {
        stopRequested = true;
    }

    public interface AudioChunkListener {
        void onChunk(byte[] opusChunk);
    }

    public static CompletableFuture<Void> streamAudio(AudioInputStream audioStream, OpusEncoder opusEncoder, AudioChunkListener listener, RadioAudioEffect audioEffect, boolean applyEffect) {
        streaming = true;
        stopRequested = false;
        return CompletableFuture.runAsync(() -> {
            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(PCM_FORMAT, audioStream)) {
                streamPcm(pcmStream, opusEncoder, listener, audioEffect, applyEffect);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                throw new RuntimeException("Audio streaming failed", e);
            } finally {
                streaming = false;
                stopRequested = false;
            }
        });
    }

    private static void streamPcm(AudioInputStream pcmStream, OpusEncoder opusEncoder, AudioChunkListener listener, RadioAudioEffect audioEffect, boolean applyEffect) throws Exception {
        byte[] buffer = new byte[BYTES_PER_FRAME];
        short[] pcmFrame = new short[FRAME_SIZE];

        long nextFrameTime = System.currentTimeMillis();
        int bytesRead;

        while (!stopRequested && (bytesRead = pcmStream.read(buffer)) != -1) {
            padBufferIfNeeded(buffer, bytesRead);
            decodePcmFrame(buffer, pcmFrame);

            if (audioEffect != null && applyEffect) {
                audioEffect.apply(pcmFrame);
            }

            byte[] opusData = opusEncoder.encode(pcmFrame);
            if (opusData != null && opusData.length > 0) {
                listener.onChunk(opusData);
            }

            nextFrameTime += FRAME_DURATION_MS;
            sleepUntil(nextFrameTime);
        }
    }

    private static void padBufferIfNeeded(byte[] buffer, int bytesRead) {
        if (bytesRead < BYTES_PER_FRAME) {
            Arrays.fill(buffer, bytesRead, BYTES_PER_FRAME, (byte) 0);
        }
    }

    private static void decodePcmFrame(byte[] buffer, short[] pcmFrame) {
        ByteBuffer.wrap(buffer)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .get(pcmFrame);
    }

    private static void sleepUntil(long targetTime) throws InterruptedException {
        long sleepTime = targetTime - System.currentTimeMillis();
        if (sleepTime > 0) {
            Thread.sleep(sleepTime);
        }
    }
}