package org.nyt.simpleVoiceRadio.VoiceChat;

import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import org.nyt.simpleVoiceRadio.Misc.RadioAudioEffect;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;
import org.nyt.simpleVoiceRadio.Utils.DataManager;
import org.nyt.simpleVoiceRadio.Utils.JukeboxManager;
import org.nyt.simpleVoiceRadio.VoiceAddon;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Utils {
    private final SimpleVoiceRadio plugin;
    private final DataManager dataManager;

    private final JukeboxManager jukeboxManager;
    private final ChannelManager channelManager;

    private final Map<Integer, Integer> lastActivityTick = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lastDiscActivityTick = new ConcurrentHashMap<>();

    private RadioAudioEffect radioEffect;
    private OpusDecoder opusDecoder;
    private OpusEncoder opusEncoder;

    public Utils(SimpleVoiceRadio plugin, DataManager dataManager, JukeboxManager jukeboxManager, ChannelManager channelManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.jukeboxManager = jukeboxManager;
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

                LocationalAudioChannel channel = channelManager.getOutputChannels().computeIfAbsent(loc, channelManager::createChannel);

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

            LocationalAudioChannel channel = channelManager.getOutputChannels().computeIfAbsent(loc, channelManager::createChannel);

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
                if ( plugin.getConfig().getBoolean("audio-effects.apply_to_custom_discs", false) ) processedData = applyRadioEffects(audioData);
                channel.send(processedData);
            }
        });
    }

    private byte[] applyRadioEffects(byte[] opusData) {
        if (radioEffect == null
                || opusData.length == 0
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