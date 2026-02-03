package org.nyt.simpleVoiceRadio;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.*;
import org.bukkit.Bukkit;
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
    private final Map<Location, UUID> activeDiscBroadcasts = new ConcurrentHashMap<>();
    private final Set<Location> discActiveOutputs = ConcurrentHashMap.newKeySet();

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
        eventRegistration.registerEvent(LocationalSoundPacketEvent.class, this::onLocationalPacket);
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

    public void startDiscBroadcast(Location radioLocation, UUID discChannelId) {
        activeDiscBroadcasts.put(radioLocation, discChannelId);
    }

    public void stopDiscBroadcast(Location radioLocation) {
        activeDiscBroadcasts.remove(radioLocation);

        if (!discActiveOutputs.isEmpty()) {
            if (plugin.getConfig().getBoolean("radio-block.signal_output_system", true)) {
                discActiveOutputs.forEach(loc -> jukeboxManager.updateJukeboxDisc(loc, 0));
            }
            discActiveOutputs.clear();
        }
    }

    private void onLocationalPacket(LocationalSoundPacketEvent event) {
        try {
            UUID channelId = event.getPacket().getChannelId();
            byte[] audioData = event.getPacket().getOpusEncodedData();

            if (audioData == null || audioData.length == 0) return;

            for (Map.Entry<Location, UUID> entry : activeDiscBroadcasts.entrySet()) {
                if (!entry.getValue().equals(channelId)) continue;

                Location radioLoc = entry.getKey();
                DataManager.RadioData radioData = dataManager.getBlock(radioLoc);

                if (radioData == null || !radioData.getState().equals("listen")) {
                    stopDiscBroadcast(radioLoc);
                    return;
                }

                sendDiscToOutputs(audioData, radioData.getFrequency());
                return;
            }

        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Error processing locational packet: {}", e.getMessage());
        }
    }

    private void sendDiscToOutputs(byte[] audioData, int frequency) {
        if (frequency <= 0) return;

        Map<Location, DataManager.RadioData> outputRadios =
                dataManager.getAllRadiosByStateAndFrequency("output", frequency);

        Set<Location> newDiscActiveOutputs = ConcurrentHashMap.newKeySet();

        for (Location outputLoc : outputRadios.keySet()) {
            if (!outputLoc.isChunkLoaded()) continue;

            LocationalAudioChannel channel = outputChannels.computeIfAbsent(outputLoc, this::createChannel);
            if (channel == null) continue;

            newDiscActiveOutputs.add(outputLoc);

            Collection<ServerPlayer> nearbyPlayers = api.getPlayersInRange(
                    api.fromServerLevel(outputLoc.getWorld()),
                    api.createPosition(outputLoc.getBlockX() + 0.5, outputLoc.getBlockY() + 0.5, outputLoc.getBlockZ() + 0.5),
                    channel.getDistance()
            );

            if (!nearbyPlayers.isEmpty()) {
                channel.send(audioData);
            }
        }

        Set<Location> locationsToActivate = new HashSet<>(newDiscActiveOutputs);
        Set<Location> locationsToDeactivate = discActiveOutputs.stream()
                .filter(loc -> !newDiscActiveOutputs.contains(loc))
                .collect(Collectors.toSet());

        if (plugin.getConfig().getBoolean("radio-block.signal_output_system", true)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                locationsToActivate.forEach(loc -> jukeboxManager.updateJukeboxDisc(loc, 15));
                locationsToDeactivate.forEach(loc -> jukeboxManager.updateJukeboxDisc(loc, 0));
            });
        }

        discActiveOutputs.clear();
        discActiveOutputs.addAll(newDiscActiveOutputs);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        try {
            VoicechatConnection connection = event.getSenderConnection();
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) connection.getPlayer().getPlayer();

            if (!player.hasPermission("simple_voice_radio.can_broadcast")) return;

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
                Set<Location> locationsToDeactivate = new HashSet<>(activeOutputs);
                if (plugin.getConfig().getBoolean("radio-block.signal_output_system", true)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        locationsToDeactivate.forEach(loc -> jukeboxManager.updateJukeboxDisc(loc, 0));
                    });
                }
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
                Set<Location> locationsToDeactivate = new HashSet<>(activeOutputs);
                if (plugin.getConfig().getBoolean("radio-block.signal_output_system", true)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        locationsToDeactivate.forEach(loc -> jukeboxManager.updateJukeboxDisc(loc, 0));
                    });
                }
                activeOutputs.clear();
            }
            return;
        }

        Set<Location> newActiveOutputs = ConcurrentHashMap.newKeySet();
        Map<Location, Integer> signalLevels = new ConcurrentHashMap<>();

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

                signalLevels.put(loc, signalLevel);
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

        Set<Location> locationsToDeactivate = activeOutputs.stream()
                .filter(loc -> !newActiveOutputs.contains(loc))
                .collect(Collectors.toSet());

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.getConfig().getBoolean("radio-block.signal_output_system", true)) {
                signalLevels.forEach(jukeboxManager::updateJukeboxDisc);
                locationsToDeactivate.forEach(loc -> jukeboxManager.updateJukeboxDisc(loc, 0));
            }
        });

        activeOutputs.clear();
        activeOutputs.addAll(newActiveOutputs);
    }
}