package dev.nybikyt.simpleVoiceRadio.Audio;

import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.BlockKey;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.Radio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.RadioState;
import dev.nybikyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.Utils.Scheduler;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceAddon;
import org.bukkit.Location;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public class AudioRouter {

    private static final long SPEAKER_IDLE_MILLIS = 30_000L;
    private static final long CHANNEL_IDLE_MILLIS = 30_000L;
    private static final long CLEANUP_PERIOD_TICKS = 200L;
    private static final long LISTENER_CACHE_MILLIS = 250L;
    private static final long BROADCAST_TARGETS_MILLIS = 500L;
    private static final int FULL_SIGNAL_LEVEL = 15;

    private final SimpleVoiceRadio plugin;
    private final PluginConfig config;
    private final DataManager dataManager;
    private final DisplayEntityManager displayEntityManager;
    private final ChannelManager channelManager;
    private final AntennaManager antennaManager;
    private final SignalManager signalManager;

    private final Map<UUID, SpeakerProcessor> speakerProcessors = new ConcurrentHashMap<>();
    private final Map<BlockKey, ListenerCacheEntry> listenerCache = new ConcurrentHashMap<>();
    private Scheduler.TaskHandle cleanupTask;

    private volatile List<Location> broadcastTargets;
    private volatile long broadcastTargetsExpireAt;

    private record ListenerCacheEntry(boolean hasListeners, long expiresAt) {
    }

    public AudioRouter(SimpleVoiceRadio plugin, PluginConfig config, DataManager dataManager, DisplayEntityManager displayEntityManager, ChannelManager channelManager) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;
        this.displayEntityManager = displayEntityManager;
        this.channelManager = channelManager;
        this.antennaManager = new AntennaManager(plugin, config, dataManager);
        this.signalManager = config.signalSystemActive()
                ? new SignalManager(plugin, dataManager, antennaManager)
                : null;

        startCleanupTask();
    }

    private static final class SpeakerProcessor {

        private final OpusDecoder decoder;
        private final OpusEncoder encoder;
        private final RadioAudioEffect effect;
        private volatile long lastUsed;

        private SpeakerProcessor(OpusDecoder decoder, OpusEncoder encoder, RadioAudioEffect effect) {
            this.decoder = decoder;
            this.encoder = encoder;
            this.effect = effect;
            this.lastUsed = System.currentTimeMillis();
        }

        private void close() {
            decoder.close();
            encoder.close();
        }
    }

    public AntennaManager getAntennaManager() {
        return antennaManager;
    }

    private void startCleanupTask() {
        cleanupTask = Scheduler.runAsyncTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            speakerProcessors.entrySet().removeIf(entry -> {
                if (now - entry.getValue().lastUsed > SPEAKER_IDLE_MILLIS) {
                    entry.getValue().close();
                    channelManager.releaseStream(entry.getKey());
                    return true;
                }
                return false;
            });
            channelManager.cleanupIdle(CHANNEL_IDLE_MILLIS);
            listenerCache.values().removeIf(entry -> now >= entry.expiresAt());
        }, CLEANUP_PERIOD_TICKS, CLEANUP_PERIOD_TICKS);
    }

    public void handleMicPacket(UUID speakerId, Location speakerLocation, byte[] audioData) {
        if (audioData == null || audioData.length == 0) return;

        double radius = config.inputSearchRadius();
        Map<Location, Radio> nearbyInputs = dataManager.findInputRadiosNear(speakerLocation, radius);
        if (nearbyInputs.isEmpty()) return;

        SpeakerProcessor processor = speakerProcessors.computeIfAbsent(speakerId, id -> {
            VoicechatServerApi api = SimpleVoiceAddon.getApi();
            return new SpeakerProcessor(api.createDecoder(), api.createEncoder(), new RadioAudioEffect(config));
        });
        processor.lastUsed = System.currentTimeMillis();

        byte[] processedData = applyRadioEffects(audioData, processor.effect, processor.decoder, processor.encoder);

        Map<Integer, Location> bestInputs = new HashMap<>();
        Map<Integer, Double> bestDistances = new HashMap<>();
        for (Map.Entry<Location, Radio> entry : nearbyInputs.entrySet()) {
            int frequency = entry.getValue().getFrequency();
            double distanceSquared = distanceSquaredToCenter(entry.getKey(), speakerLocation);
            Double best = bestDistances.get(frequency);
            if (best == null || distanceSquared < best) {
                bestDistances.put(frequency, distanceSquared);
                bestInputs.put(frequency, entry.getKey());
            }
        }

        for (Map.Entry<Integer, Location> entry : bestInputs.entrySet()) {
            int frequency = entry.getKey();
            BlockKey transmitter = BlockKey.of(entry.getValue());

            if (!antennaManager.hasClearance(transmitter)) continue;

            if (signalManager != null) {
                int level = SignalManager.calculateLevel(Math.sqrt(bestDistances.get(frequency)), radius);
                signalManager.report(frequency, transmitter, speakerId, level);
            }

            sendToOutputs(transmitter, frequency, processedData, speakerId);
        }
    }

    private static double distanceSquaredToCenter(Location block, Location point) {
        double dx = block.getBlockX() + 0.5 - point.getX();
        double dy = block.getBlockY() + 0.5 - point.getY();
        double dz = block.getBlockZ() + 0.5 - point.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public void handleDiscPacket(Location radioLocation, byte[] audioData, RadioAudioEffect effect, OpusEncoder encoder, OpusDecoder decoder, UUID streamId) {
        if (audioData == null || audioData.length == 0) return;

        Radio radio = dataManager.get(radioLocation);
        if (radio == null || radio.getFrequency() <= 0) return;

        int frequency = radio.getFrequency();
        BlockKey transmitter = BlockKey.of(radioLocation);

        if (!antennaManager.hasClearance(transmitter)) return;

        if (signalManager != null) {
            signalManager.report(frequency, transmitter, streamId, FULL_SIGNAL_LEVEL);
        }

        byte[] processedData = config.effectsForDiscs()
                ? applyRadioEffects(audioData, effect, decoder, encoder)
                : audioData;

        sendToOutputs(transmitter, frequency, processedData, streamId);
    }

    private void sendToOutputs(BlockKey transmitter, int frequency, byte[] audioData, UUID streamId) {
        dataManager.forEachRadio(RadioState.OUTPUT, frequency, (location, radio) -> {
            if (!location.isChunkLoaded()) return;
            BlockKey receiver = BlockKey.of(location);
            if (!antennaManager.hasClearance(receiver)) return;
            if (!antennaManager.inRange(transmitter, receiver)) return;
            sendToChannel(location, audioData, streamId);
        });
    }

    public void playAudio(AudioStreamer.StreamSource source, Integer frequency, boolean loop) {
        VoicechatServerApi api = SimpleVoiceAddon.getApi();
        if (api == null) return;

        OpusEncoder encoder = api.createEncoder();
        RadioAudioEffect effect = new RadioAudioEffect(config);
        UUID streamId = UUID.randomUUID();

        AudioStreamer.streamAudio(source, encoder, chunk -> broadcastAudio(chunk, frequency, streamId), effect, config.effectsForFiles(), loop)
                .whenComplete((result, ex) -> {
                    encoder.close();
                    if (unwrap(ex) instanceof IllegalStateException) return;
                    if (ex != null) SimpleVoiceRadio.LOGGER.error("Audio playback failed: ", ex);
                    broadcastTargets = null;
                    resetRadios(RadioState.BROADCAST);
                    channelManager.releaseStream(streamId);
                });
    }

    private Throwable unwrap(Throwable ex) {
        return ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
    }

    private void broadcastAudio(byte[] audioData, Integer frequency, UUID streamId) {
        if (audioData == null || audioData.length == 0) return;

        List<Location> targets = refreshBroadcastTargets(frequency);
        if (targets.isEmpty()) return;

        for (Location location : targets) {
            Radio radio = dataManager.get(location);
            if (radio == null || !location.isChunkLoaded()) continue;
            if (!antennaManager.hasClearance(BlockKey.of(location))) continue;

            if (radio.getState() == RadioState.OUTPUT) {
                dataManager.updateState(location, RadioState.BROADCAST);
                displayEntityManager.scheduleStateSkin(location, radio);
            }

            if (signalManager != null) {
                signalManager.reportDirect(BlockKey.of(location), streamId, FULL_SIGNAL_LEVEL);
            }

            sendToChannel(location, audioData, streamId);
        }
    }

    private List<Location> refreshBroadcastTargets(Integer frequency) {
        long now = System.currentTimeMillis();
        List<Location> targets = broadcastTargets;
        if (targets != null && now < broadcastTargetsExpireAt) return targets;

        Map<Location, Radio> radios = new HashMap<>();
        radios.putAll(dataManager.getRadios(RadioState.OUTPUT));
        radios.putAll(dataManager.getRadios(RadioState.BROADCAST));
        if (frequency != null) radios.values().removeIf(radio -> radio.getFrequency() != frequency);

        targets = List.copyOf(radios.keySet());
        broadcastTargets = targets;
        broadcastTargetsExpireAt = now + BROADCAST_TARGETS_MILLIS;
        return targets;
    }

    private void sendToChannel(Location location, byte[] audioData, UUID streamId) {
        if (!hasListeners(location)) return;

        LocationalAudioChannel channel = channelManager.getChannel(location, streamId);
        if (channel != null) channel.send(audioData);
    }

    private boolean hasListeners(Location location) {
        BlockKey key = BlockKey.of(location);
        long now = System.currentTimeMillis();
        ListenerCacheEntry cached = listenerCache.get(key);
        if (cached != null && now < cached.expiresAt()) return cached.hasListeners();

        VoicechatServerApi api = SimpleVoiceAddon.getApi();
        ServerLevel serverLevel = api.fromServerLevel(location.getWorld());
        Collection<ServerPlayer> nearbyPlayers = api.getPlayersInRange(
                serverLevel,
                api.createPosition(location.getBlockX() + 0.5, location.getBlockY() + 0.5, location.getBlockZ() + 0.5),
                (float) config.outputRadius()
        );
        boolean result = !nearbyPlayers.isEmpty();
        listenerCache.put(key, new ListenerCacheEntry(result, now + LISTENER_CACHE_MILLIS));
        return result;
    }

    public void resetRadios(RadioState state) {
        dataManager.getRadios(state).forEach((location, radio) -> {
            dataManager.updateState(location, RadioState.OUTPUT);
            displayEntityManager.scheduleStateSkin(location, radio);
        });
    }

    private byte[] applyRadioEffects(byte[] opusData, RadioAudioEffect effect, OpusDecoder decoder, OpusEncoder encoder) {
        if (opusData.length == 0 || !config.effectsEnabled()) return opusData;

        try {
            short[] pcmData = decoder.decode(opusData);
            if (pcmData == null || pcmData.length != 960) return opusData;
            effect.apply(pcmData);
            try {
                return encoder.encode(pcmData);
            } catch (AssertionError e) {
                return opusData;
            }
        } catch (Exception e) {
            return opusData;
        }
    }

    public void shutdown() {
        if (cleanupTask != null) cleanupTask.cancel();
        if (signalManager != null) signalManager.shutdown();
        antennaManager.shutdown();
        speakerProcessors.values().forEach(SpeakerProcessor::close);
        speakerProcessors.clear();
        listenerCache.clear();
        channelManager.clear();
    }
}
