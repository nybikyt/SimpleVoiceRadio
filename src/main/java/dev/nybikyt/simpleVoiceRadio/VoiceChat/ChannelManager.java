package dev.nybikyt.simpleVoiceRadio.VoiceChat;

import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import org.bukkit.Location;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.VoiceAddon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelManager {
    private final SimpleVoiceRadio plugin;
    private final DataManager dataManager;

    private final Map<Location, LocationalAudioChannel> outputChannels = new ConcurrentHashMap<>();

    public ChannelManager(SimpleVoiceRadio plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public Map<Location, LocationalAudioChannel> getOutputChannels() {
        return outputChannels;
    }

    public LocationalAudioChannel createChannel(Location location) {
        ServerLevel serverLevel = VoiceAddon.getApi().fromServerLevel(location.getWorld());
        LocationalAudioChannel channel = VoiceAddon.getApi().createLocationalAudioChannel(
                UUID.randomUUID(),
                serverLevel,
                VoiceAddon.getApi().createPosition(location.getBlockX() + 0.5, location.getBlockY() + 0.5, location.getBlockZ() + 0.5)
        );

        if (channel == null) return null;

        float radius = (float) plugin.getConfig().getDouble("radio-block.output_radius", 15);

        channel.setDistance(radius);
        channel.setCategory(CategoryRegistration.getRadioCategory());

        outputChannels.put(location, channel);

        return channel;
    }

    public void createOutputChannels() {
        outputChannels.clear();
        Map<Location, DataManager.RadioData> outputRadios = dataManager.getAllRadiosByState("output");
        outputRadios.keySet().forEach(this::createChannel);
    }

    public void updateOutputChannels() {
        Map<Location, DataManager.RadioData> outputRadios = dataManager.getAllRadiosByState("output");
        outputChannels.entrySet().removeIf(entry -> {
            if (!outputRadios.containsKey(entry.getKey())) {
                entry.getValue().flush();
                return true;
            }
            return false;
        });
        outputRadios.keySet().forEach(loc -> outputChannels.putIfAbsent(loc, createChannel(loc)));
    }

    public void deleteChannel(Location location) {
        LocationalAudioChannel channel = outputChannels.get(location);
        if (channel != null) channel.flush();
        outputChannels.remove(location);
    }
}
