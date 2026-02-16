package org.nyt.simpleVoiceRadio.VoiceChat;

import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import org.nyt.simpleVoiceRadio.Bridges.JavaZoom;
import org.nyt.simpleVoiceRadio.Misc.RadioAudioEffect;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;
import org.nyt.simpleVoiceRadio.Utils.DataManager;
import org.nyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import org.nyt.simpleVoiceRadio.Utils.JukeboxManager;
import org.nyt.simpleVoiceRadio.VoiceAddon;

import javax.sound.sampled.AudioInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Utils {
    private final SimpleVoiceRadio plugin;
    private final DataManager dataManager;

    private final JukeboxManager jukeboxManager;
    private final DisplayEntityManager displayEntityManager;
    private final ChannelManager channelManager;

    private final Map<Integer, Integer> lastActivityTick = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lastDiscActivityTick = new ConcurrentHashMap<>();

    private final RadioAudioEffect radioEffect;
    private final OpusDecoder opusDecoder;
    private final OpusEncoder opusEncoder;

    public Utils(SimpleVoiceRadio plugin, DataManager dataManager, JukeboxManager jukeboxManager, DisplayEntityManager displayEntityManager, ChannelManager channelManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.jukeboxManager = jukeboxManager;
        this.displayEntityManager = displayEntityManager;
        this.channelManager = channelManager;

        this.radioEffect = new RadioAudioEffect(plugin);
        this.opusDecoder = VoiceAddon.getApi().createDecoder();
        this.opusEncoder = VoiceAddon.getApi().createEncoder();

        if (plugin.getConfig().getBoolean("radio-block.signal_output_system", false)
                && !plugin.getConfig().getBoolean("radio-block.redstone_frequency", false)) startFrequencyCleanup();
    }


    private void startFrequencyCleanup() {
        BukkitRunnable checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                int currentTick = plugin.getServer().getCurrentTick();

                lastActivityTick.entrySet().removeIf(entry -> {
                    int frequency = entry.getKey();
                    int lastTick = entry.getValue();

                    if (currentTick - lastTick > 1) {
                        Map<Location, DataManager.RadioData> outputRadios =
                                dataManager.getAllRadiosByStateAndFrequency("output", frequency);

                        outputRadios.keySet().stream()
                                .filter(Location::isChunkLoaded)
                                .forEach(loc -> jukeboxManager.updateJukeboxDisc(loc, 0));

                        return true;
                    }
                    return false;
                });

                lastDiscActivityTick.entrySet().removeIf(entry -> {
                    int frequency = entry.getKey();
                    int lastTick = entry.getValue();

                    if (currentTick - lastTick > 1) {
                        Map<Location, DataManager.RadioData> outputRadios =
                                dataManager.getAllRadiosByStateAndFrequency("output", frequency);

                        outputRadios.keySet().stream()
                                .filter(Location::isChunkLoaded)
                                .forEach(loc -> jukeboxManager.updateJukeboxDisc(loc, 0));

                        return true;
                    }
                    return false;
                });
            }
        };

        checkTask.runTaskTimer(plugin, 1L, 1L);
    }

    public void handlePacket(Location location, byte[] audioData) {
        double inputRadius = plugin.getConfig().getDouble("radio-block.input_search_radius", 15.0);
        double inputRadiusSquared = inputRadius * inputRadius;

        List<Map.Entry<Location, DataManager.RadioData>> nearbyInputRadios =
                dataManager.getAllRadiosByState("input").entrySet().stream()
                        .filter(map ->
                                map.getKey().getWorld().equals(location.getWorld())
                                        && map.getKey().distanceSquared(location) <= inputRadiusSquared
                                        && map.getValue().getFrequency() > 0
                        )
                        .toList();

        if (audioData == null || audioData.length == 0 || nearbyInputRadios.isEmpty()) {
            return;
        }

        Set<Integer> frequencies = nearbyInputRadios.stream()
                .map(entry -> entry.getValue().getFrequency())
                .collect(Collectors.toSet());

        frequencies.forEach(frequency -> {

            if (plugin.getConfig().getBoolean("radio-block.signal_output_system", false)
                    && !plugin.getConfig().getBoolean("radio-block.redstone_frequency", false)) lastActivityTick.put(frequency, plugin.getServer().getCurrentTick());

            Map<Location, DataManager.RadioData> outputRadios = dataManager.getAllRadiosByStateAndFrequency("output", frequency);

            Optional<Map.Entry<Location, DataManager.RadioData>> inputForFrequency =
                    nearbyInputRadios.stream()
                            .filter(e -> e.getValue().getFrequency() == frequency)
                            .findFirst();

            if (inputForFrequency.isEmpty()) return;

            Location inputLocation = inputForFrequency.get().getKey().toCenterLocation();
            int signalLevel = JukeboxManager.calculateSignalLevel(location.distance(inputLocation), inputRadius);

            outputRadios.keySet().stream().filter(Location::isChunkLoaded).forEach(loc -> {

                ServerLevel serverLevel = VoiceAddon.getApi().fromServerLevel(loc.getWorld());

                LocationalAudioChannel channel = getOrCreateChannel(loc);

                if (channel == null) return;

                if (plugin.getConfig().getBoolean("radio-block.signal_output_system", false)
                        && !plugin.getConfig().getBoolean("radio-block.redstone_frequency", false)) jukeboxManager.updateJukeboxDisc(loc, signalLevel);

                Collection<ServerPlayer> nearbyPlayers = VoiceAddon.getApi().getPlayersInRange(
                        serverLevel,
                        VoiceAddon.getApi().createPosition(loc.getBlockX() + 0.5, loc.getBlockY() + 0.5, loc.getBlockZ() + 0.5),
                        channel.getDistance()
                );

                if (!nearbyPlayers.isEmpty()) {
                    channel.send(applyRadioEffects(audioData));
                }
            });
        });
    }


    public void resetBroadCastingRadios() {
        dataManager.getAllRadiosByState("broadcast").forEach((location, radioData) -> {
            radioData.setState("output");
            Bukkit.getScheduler().runTask(plugin, () -> displayEntityManager.setStateSkin(radioData.getTextures(), radioData.getState()));
        });
    }

    public void playFile(AudioInputStream audioInputStream) {
        boolean applyEffect = plugin.getConfig().getBoolean("audio-effects.apply_to_files", true);
        JavaZoom.streamAudio(audioInputStream, opusEncoder, this::broadcastAudioToAll, new RadioAudioEffect(plugin), applyEffect)
                .thenRun(this::resetBroadCastingRadios)
                .exceptionally(ex -> {
                    SimpleVoiceRadio.LOGGER.error("Audio playback failed: ", ex);
                    resetBroadCastingRadios();
                    return null;
                });
    }

    public void broadcastAudioToAll(byte[] audioData) {
        if (audioData == null || audioData.length == 0) return;

        Map<Location, DataManager.RadioData> allRadios = new ConcurrentHashMap<>();
        allRadios.putAll(dataManager.getAllRadiosByState("output"));
        allRadios.putAll(dataManager.getAllRadiosByState("broadcast"));

        if (allRadios.isEmpty()) return;

        allRadios.entrySet().stream()
                .filter(entry -> entry.getKey().isChunkLoaded())
                .forEach(entry -> {
                    Location location = entry.getKey();

                    ServerLevel serverLevel = VoiceAddon.getApi().fromServerLevel(location.getWorld());

                    LocationalAudioChannel channel = getOrCreateChannel(location);

                    if (channel == null) return;

                    if (entry.getValue().getState().equals("output")) {
                        entry.getValue().setState("broadcast");
                        Bukkit.getScheduler().runTask(plugin, () -> displayEntityManager.setStateSkin(entry.getValue().getTextures(), entry.getValue().getState()));
                    }

                    Collection<ServerPlayer> nearbyPlayers = VoiceAddon.getApi().getPlayersInRange(
                            serverLevel,
                            VoiceAddon.getApi().createPosition(
                                    location.getBlockX() + 0.5,
                                    location.getBlockY() + 0.5,
                                    location.getBlockZ() + 0.5
                            ),
                            channel.getDistance()
                    );

                    if (!nearbyPlayers.isEmpty()) {
                        channel.send(audioData);
                    }
                });
    }

    public void handleDiscPacket(Location radioLocation, byte[] audioData) {
        if (audioData == null || audioData.length == 0) return;

        DataManager.RadioData blockData = dataManager.getBlock(radioLocation);
        if (blockData == null || blockData.getFrequency() <= 0) return;

        int frequency = blockData.getFrequency();

        if (plugin.getConfig().getBoolean("radio-block.signal_output_system", false)
                && !plugin.getConfig().getBoolean("radio-block.redstone_frequency", false)) lastDiscActivityTick.put(frequency, plugin.getServer().getCurrentTick());

        Map<Location, DataManager.RadioData> outputRadios = dataManager.getAllRadiosByStateAndFrequency("output", frequency);

        outputRadios.keySet().stream().filter(Location::isChunkLoaded).forEach(loc -> {
            ServerLevel serverLevel = VoiceAddon.getApi().fromServerLevel(loc.getWorld());

            LocationalAudioChannel channel = getOrCreateChannel(loc);

            if (channel == null) return;

            if (plugin.getConfig().getBoolean("radio-block.signal_output_system", false)
                    && !plugin.getConfig().getBoolean("radio-block.redstone_frequency", false)) jukeboxManager.updateJukeboxDisc(loc, 15);

            Collection<ServerPlayer> nearbyPlayers = VoiceAddon.getApi().getPlayersInRange(
                    serverLevel,
                    VoiceAddon.getApi().createPosition(loc.getBlockX() + 0.5, loc.getBlockY() + 0.5, loc.getBlockZ() + 0.5),
                    channel.getDistance()
            );

            if (!nearbyPlayers.isEmpty()) {
                byte[] processedData = audioData;
                if (plugin.getConfig().getBoolean("audio-effects.apply_to_custom_discs", true)) processedData = applyRadioEffects(audioData);
                channel.send(processedData);
            }
        });
    }

    private LocationalAudioChannel getOrCreateChannel(Location loc) {
        Map<Location, LocationalAudioChannel> channels = channelManager.getOutputChannels();
        LocationalAudioChannel existing = channels.get(loc);
        if (existing != null) return existing;

        LocationalAudioChannel created = channelManager.createChannel(loc);
        if (created == null) return null;

        LocationalAudioChannel raced = channels.putIfAbsent(loc, created);
        return raced != null ? raced : created;
    }

    private byte[] applyRadioEffects(byte[] opusData) {
        if (opusData.length == 0
                || !plugin.getConfig().getBoolean("audio-effects.enabled", true))
            return opusData;

        try {
            short[] pcmData = opusDecoder.decode(opusData);

            if (pcmData == null || pcmData.length != 960) return opusData;

            radioEffect.apply(pcmData);

            try {
                return opusEncoder.encode(pcmData);
            } catch (AssertionError e) {
                return opusData;
            }
        } catch (Exception e) {
            return opusData;
        }
    }
}