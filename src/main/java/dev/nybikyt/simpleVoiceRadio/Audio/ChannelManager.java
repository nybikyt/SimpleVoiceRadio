package dev.nybikyt.simpleVoiceRadio.Audio;

import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Voice.VoiceBackend;
import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelManager {

    private final VoiceBackend backend;
    private final Map<ChannelKey, Entry> channels = new ConcurrentHashMap<>();

    public ChannelManager(VoiceBackend backend) {
        this.backend = backend;
    }

    private record ChannelKey(DataManager.BlockKey radio, UUID streamId) {
    }

    private static final class Entry {

        private final VoiceBackend.Channel channel;
        private volatile long lastUsed;

        private Entry(VoiceBackend.Channel channel) {
            this.channel = channel;
            this.lastUsed = System.currentTimeMillis();
        }
    }

    public VoiceBackend.Channel getChannel(Location radioLocation, UUID streamId) {
        if (radioLocation.getWorld() == null) return null;

        ChannelKey key = new ChannelKey(DataManager.BlockKey.of(radioLocation), streamId);
        Entry entry = channels.computeIfAbsent(key, k -> {
            VoiceBackend.Channel channel = backend.createChannel(radioLocation);
            if (channel == null) return null;
            return new Entry(channel);
        });
        if (entry == null) return null;
        entry.lastUsed = System.currentTimeMillis();
        return entry.channel;
    }

    public void invalidateRadio(Location radioLocation) {
        DataManager.BlockKey radioKey = DataManager.BlockKey.of(radioLocation);
        channels.entrySet().removeIf(entry -> {
            if (entry.getKey().radio().equals(radioKey)) {
                entry.getValue().channel.flush();
                return true;
            }
            return false;
        });
    }

    public void releaseStream(UUID streamId) {
        channels.entrySet().removeIf(entry -> {
            if (entry.getKey().streamId().equals(streamId)) {
                entry.getValue().channel.flush();
                return true;
            }
            return false;
        });
    }

    public void cleanupIdle(long maxIdleMillis) {
        long now = System.currentTimeMillis();
        channels.entrySet().removeIf(entry -> {
            if (now - entry.getValue().lastUsed > maxIdleMillis) {
                entry.getValue().channel.flush();
                return true;
            }
            return false;
        });
    }

    public void clear() {
        channels.values().forEach(entry -> entry.channel.flush());
        channels.clear();
    }
}
