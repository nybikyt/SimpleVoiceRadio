package org.nyt.simpleVoiceRadio;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.*;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.nyt.simpleVoiceRadio.Utils.DataManager;
import org.nyt.simpleVoiceRadio.Utils.JukeboxManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceAddon implements VoicechatPlugin {
    public static VoicechatServerApi api = null;
    private final DataManager dataManager;
    private final SimpleVoiceRadio plugin;
    private final JukeboxManager jukeboxManager;
    private final Map<Location, LocationalAudioChannel> outputChannels = new ConcurrentHashMap<>();

    public VoiceAddon(DataManager dataManager, SimpleVoiceRadio plugin, JukeboxManager jukeboxManager) {
        this.dataManager = dataManager;
        this.plugin = plugin;
        this.jukeboxManager = jukeboxManager;
    }

    @Override
    public String getPluginId() {
        return SimpleVoiceRadio.class.getSimpleName();
    }

    @Override
    public void initialize(VoicechatApi voicechatApi) {}

    @Override
    public void registerEvents(EventRegistration eventRegistration) {
        eventRegistration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStart);
        eventRegistration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    private void onServerStart(VoicechatServerStartedEvent event) {
        api = event.getVoicechat();
        createOutputChannels();
    }

    public LocationalAudioChannel createChannel(Location location) {
        ServerLevel serverLevel = api.fromServerLevel(location.getWorld());
        LocationalAudioChannel channel = api.createLocationalAudioChannel(
                UUID.randomUUID(),
                serverLevel,
                api.createPosition(location.getBlockX() + 0.5, location.getBlockY() + 0.5, location.getBlockZ() + 0.5)
        );

        if ( channel == null ) return null;

        float radius = (float) plugin.getConfig().getDouble("radio-block.output_radius", 16);
        channel.setDistance(radius);
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
        if ( channel != null ) channel.flush();
        outputChannels.remove(location);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        try {
            VoicechatConnection connection = event.getSenderConnection();

            World world = (World) connection.getPlayer().getServerLevel().getServerLevel();
            Position position = connection.getPlayer().getPosition();

            Location location = new Location(world, position.getX(), position.getY(), position.getZ());

            sendPacket(location, event.getPacket().getOpusEncodedData());

        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Error processing microphone packet: {}", e.getMessage());
        }
    }

    private void sendPacket(Location location, byte[] audioData) {

        double inputRadius = plugin.getConfig().getDouble("radio-block.input_search_radius", 15.0);
        double inputRadiusSq = inputRadius * inputRadius;

        Optional<Map.Entry<Location, DataManager.RadioData>> inputOpt =
                dataManager.getAllRadiosByState("input").entrySet().stream()
                        .filter(e -> e.getKey().getWorld().equals(location.getWorld()) && e.getKey().distanceSquared(location) <= inputRadiusSq)
                        .findFirst();

        if (inputOpt.isEmpty()) return;

        int frequency = inputOpt.get().getValue().getFrequency();
        if (frequency == 0) return;

        Map<Location, DataManager.RadioData> outputRadios = dataManager.getAllRadiosByStateAndFrequency("output", frequency);

        for (Map.Entry<Location, DataManager.RadioData> entry : outputRadios.entrySet()) {
            Location loc = entry.getKey();

            if (!loc.isChunkLoaded()) continue;

            ServerLevel serverLevel = api.fromServerLevel(loc.getWorld());
            LocationalAudioChannel channel = outputChannels.computeIfAbsent(loc, this::createChannel);

            if (channel == null) continue;

            Collection<ServerPlayer> nearbyPlayers = api.getPlayersInRange(
                    serverLevel,
                    api.createPosition(loc.getBlockX() + 0.5, loc.getBlockY() + 0.5, loc.getBlockZ() + 0.5),
                    channel.getDistance()
            );

            if (nearbyPlayers.isEmpty()) continue;

            if (audioData == null || audioData.length == 0) {
                jukeboxManager.updateJukeboxDisc(loc, 0);
            } else {
                double distance = location.distance(inputOpt.get().getKey());
                jukeboxManager.updateJukeboxDisc(loc, JukeboxManager.calculateSignalLevel(distance, inputRadius));
                channel.send(audioData);
            }
        }
    }

}