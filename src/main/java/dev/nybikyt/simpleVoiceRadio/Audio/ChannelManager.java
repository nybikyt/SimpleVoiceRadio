package dev.nybikyt.simpleVoiceRadio.Audio;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceAddon;
import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelManager {

    private final PluginConfig config;
    private final Map<ChannelKey, Entry> channels = new ConcurrentHashMap<>();

    public ChannelManager(PluginConfig config) {
        this.config = config;
    }

    private record ChannelKey(DataManager.BlockKey radio, UUID streamId) {
    }

    private static final class Entry {

        private final LocationalAudioChannel channel;
        private volatile long lastUsed;

        private Entry(LocationalAudioChannel channel) {
            this.channel = channel;
            this.lastUsed = System.currentTimeMillis();
        }
    }

    public LocationalAudioChannel getChannel(Location radioLocation, UUID streamId) {
        VoicechatServerApi api = SimpleVoiceAddon.getApi();
        if (api == null || radioLocation.getWorld() == null) return null;

        ChannelKey key = new ChannelKey(DataManager.BlockKey.of(radioLocation), streamId);
        Entry entry = channels.computeIfAbsent(key, k -> {
            LocationalAudioChannel channel = api.createLocationalAudioChannel(
                    UUID.randomUUID(),
                    api.fromServerLevel(radioLocation.getWorld()),
                    api.createPosition(
                            radioLocation.getBlockX() + 0.5,
                            radioLocation.getBlockY() + 0.5,
                            radioLocation.getBlockZ() + 0.5
                    )
            );
            if (channel == null) return null;
            channel.setDistance((float) config.outputRadius());
            channel.setCategory(SimpleVoiceAddon.getRadioCategory());
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
