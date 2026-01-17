package org.nyt.simpleVoiceRadio;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.nyt.simpleVoiceRadio.Utils.DataManager;
import org.nyt.simpleVoiceRadio.Utils.JukeboxManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class VoiceAddon implements VoicechatPlugin {
    public static VoicechatServerApi api = null;
    private final DataManager dataManager;
    private final SimpleVoiceRadio plugin;
    private final JukeboxManager jukeboxManager;
    private final Map<Location, LocationalAudioChannel> outputChannels = new ConcurrentHashMap<>();
    private final Set<Location> activeOutputs = ConcurrentHashMap.newKeySet();

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

        if (channel == null) return null;

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
        if (channel != null) channel.flush();
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

        List<Map.Entry<Location, DataManager.RadioData>> nearbyInputRadios =
                dataManager.getAllRadiosByState("input").entrySet().stream()
                        .filter(e -> e.getKey().getWorld().equals(location.getWorld())
                                && e.getKey().distanceSquared(location) <= inputRadiusSq)
                        .toList();

        if (audioData == null || audioData.length == 0 || nearbyInputRadios.isEmpty()) {
            if (!activeOutputs.isEmpty()) {
                activeOutputs.forEach(loc -> jukeboxManager.updateJukeboxDisc(loc, 0));
                activeOutputs.clear();
            }
            return;
        }

        Set<Integer> frequencies = nearbyInputRadios.stream()
                .map(e -> e.getValue().getFrequency())
                .filter(freq -> freq > 0)
                .collect(Collectors.toSet());

        if (frequencies.isEmpty()) {
            if (!activeOutputs.isEmpty()) {
                activeOutputs.forEach(loc -> jukeboxManager.updateJukeboxDisc(loc, 0));
                activeOutputs.clear();
            }
            return;
        }

        Set<Location> newActiveOutputs = ConcurrentHashMap.newKeySet();

        for (int frequency : frequencies) {
            Map<Location, DataManager.RadioData> outputRadios =
                    dataManager.getAllRadiosByStateAndFrequency("output", frequency);

            Optional<Map.Entry<Location, DataManager.RadioData>> inputForFreq =
                    nearbyInputRadios.stream()
                            .filter(e -> e.getValue().getFrequency() == frequency)
                            .findFirst();

            if (inputForFreq.isEmpty()) continue;

            Location inputLoc = inputForFreq.get().getKey().toCenterLocation();
            double distance = location.distance(inputLoc);
            int signalLevel = JukeboxManager.calculateSignalLevel(distance, inputRadius);

            for (Map.Entry<Location, DataManager.RadioData> entry : outputRadios.entrySet()) {
                Location loc = entry.getKey();

                if (!loc.isChunkLoaded()) continue;

                ServerLevel serverLevel = api.fromServerLevel(loc.getWorld());
                LocationalAudioChannel channel = outputChannels.computeIfAbsent(loc, this::createChannel);

                if (channel == null) continue;

                jukeboxManager.updateJukeboxDisc(loc, signalLevel);
                newActiveOutputs.add(loc);

                Collection<ServerPlayer> nearbyPlayers = api.getPlayersInRange(
                        serverLevel,
                        api.createPosition(loc.getBlockX() + 0.5, loc.getBlockY() + 0.5, loc.getBlockZ() + 0.5),
                        channel.getDistance()
                );

                if (!nearbyPlayers.isEmpty()) {
                    channel.send(audioData);
                }
            }
        }

        activeOutputs.stream()
                .filter(loc -> !newActiveOutputs.contains(loc))
                .forEach(loc -> jukeboxManager.updateJukeboxDisc(loc, 0));

        activeOutputs.clear();
        activeOutputs.addAll(newActiveOutputs);
    }
}