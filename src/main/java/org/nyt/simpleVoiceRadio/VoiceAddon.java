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

public class VoiceAddon implements VoicechatPlugin {
    public static VoicechatServerApi api = null;
    private final DataManager dataManager;
    private final SimpleVoiceRadio plugin;
    private final JukeboxManager jukeboxManager;

    private final Map<Location, LocationalAudioChannel> outputChannels = new ConcurrentHashMap<>();
    private final Set<Location> activeOutputs = ConcurrentHashMap.newKeySet();
    private final Map<Location, Set<Location>> routeCache = new ConcurrentHashMap<>();

    private final Set<Location> repeaterLocations = ConcurrentHashMap.newKeySet();

    private double maxTransmissionDistance;
    private double maxTransmissionDistanceSq;
    private double repeaterDistance;
    private double repeaterDistanceSq;
    private final int maxHops;

    public VoiceAddon(DataManager dataManager, SimpleVoiceRadio plugin, JukeboxManager jukeboxManager) {
        this.dataManager = dataManager;
        this.plugin = plugin;
        this.jukeboxManager = jukeboxManager;

        this.maxTransmissionDistance = plugin.getConfig().getDouble("radio-block.transmission_distance", 100.0);
        this.maxTransmissionDistanceSq = maxTransmissionDistance * maxTransmissionDistance;

        this.repeaterDistance = plugin.getConfig().getDouble("radio-block.repeat_distance", 200.0);
        this.repeaterDistanceSq = repeaterDistance * repeaterDistance;

        this.maxHops = plugin.getConfig().getInt("radio-block.max_hops", 15);
        if (this.maxHops > 20) {
            SimpleVoiceRadio.LOGGER.warn("Max hops is set to {}. Values higher than 20 may cause serious server load. Make sure you know what you're doing.", this.maxHops);
        }
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

        repeaterLocations.clear();
        repeaterLocations.addAll(dataManager.getAllRepeaters());

        createOutputChannels();
        recalculateRoutesAsync();
    }

    public void addRepeater(Location location) {
        dataManager.addRepeater(location);
        repeaterLocations.add(location);
        recalculateRoutesAsync();
    }

    public void removeRepeater(Location location) {
        dataManager.removeRepeater(location);
        repeaterLocations.remove(location);
        recalculateRoutesAsync();
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
        recalculateRoutesAsync();
    }

    public void deleteChannel(Location location) {
        LocationalAudioChannel channel = outputChannels.get(location);
        if (channel != null) channel.flush();
        outputChannels.remove(location);
        recalculateRoutesAsync();
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
                activeOutputs.forEach(loc -> jukeboxManager.updateJukeboxDisc(loc, 0));
                activeOutputs.clear();
            }
            return;
        }

        Set<Location> newActiveOutputs = new HashSet<>();

        for (Map.Entry<Location, DataManager.RadioData> inputEntry : nearbyInputRadios) {
            Location inputLoc = inputEntry.getKey();
            Set<Location> reachableOutputs = routeCache.get(inputLoc);

            if (reachableOutputs == null || reachableOutputs.isEmpty()) continue;

            double distance = location.distance(inputLoc);
            int signalLevel = JukeboxManager.calculateSignalLevel(distance, inputRadius);

            for (Location outputLoc : reachableOutputs) {
                if (!outputLoc.isChunkLoaded()) continue;

                LocationalAudioChannel channel = outputChannels.computeIfAbsent(outputLoc, this::createChannel);
                if (channel == null) continue;

                jukeboxManager.updateJukeboxDisc(outputLoc, signalLevel);
                newActiveOutputs.add(outputLoc);

                ServerLevel serverLevel = api.fromServerLevel(outputLoc.getWorld());
                Collection<ServerPlayer> nearbyPlayers = api.getPlayersInRange(
                        serverLevel,
                        api.createPosition(outputLoc.getBlockX() + 0.5, outputLoc.getBlockY() + 0.5, outputLoc.getBlockZ() + 0.5),
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

    public void recalculateRoutesAsync() {
        new Thread(this::recalculateRoutes).start();
    }

    private synchronized void recalculateRoutes() {
        routeCache.clear();

        Map<Location, DataManager.RadioData> inputs = dataManager.getAllRadiosByState("input");
        Map<Location, DataManager.RadioData> outputs = dataManager.getAllRadiosByState("output");

        Map<Integer, List<Location>> outputsByFreq = new HashMap<>();
        for (Map.Entry<Location, DataManager.RadioData> entry : outputs.entrySet()) {
            outputsByFreq.computeIfAbsent(entry.getValue().getFrequency(), k -> new ArrayList<>()).add(entry.getKey());
        }

        Map<World, List<Location>> repeatersByWorld = new HashMap<>();
        for (Location loc : repeaterLocations) {
            repeatersByWorld.computeIfAbsent(loc.getWorld(), k -> new ArrayList<>()).add(loc);
        }

        for (Map.Entry<Location, DataManager.RadioData> inputEntry : inputs.entrySet()) {
            Location startNode = inputEntry.getKey();
            int frequency = inputEntry.getValue().getFrequency();

            List<Location> potentialOutputs = outputsByFreq.get(frequency);
            if (potentialOutputs == null || potentialOutputs.isEmpty()) continue;

            Set<Location> reachable = findReachableOutputs(startNode, potentialOutputs, repeatersByWorld.get(startNode.getWorld()));
            if (!reachable.isEmpty()) {
                routeCache.put(startNode, reachable);
            }
        }
    }

    private Set<Location> findReachableOutputs(Location start, List<Location> targets, List<Location> worldRepeaters) {
        Set<Location> reachedTargets = new HashSet<>();
        if (worldRepeaters == null) worldRepeaters = Collections.emptyList();

        List<Location> validTargets = new ArrayList<>();
        for (Location target : targets) {
            if (target.getWorld().equals(start.getWorld())) {
                validTargets.add(target);
            }
        }
        if (validTargets.isEmpty()) return reachedTargets;

        Queue<Location> queue = new LinkedList<>();
        Map<Location, Integer> hopCount = new HashMap<>();

        queue.add(start);
        hopCount.put(start, 0);

        while (!queue.isEmpty()) {
            Location current = queue.poll();
            int currentHops = hopCount.get(current);

            if (currentHops >= maxHops) continue;

            double currentRangeSq = (current.equals(start)) ? maxTransmissionDistanceSq : repeaterDistanceSq;

            Iterator<Location> it = validTargets.iterator();
            while (it.hasNext()) {
                Location target = it.next();
                if (current.distanceSquared(target) <= currentRangeSq) {
                    reachedTargets.add(target);
                    it.remove();
                }
            }

            if (validTargets.isEmpty()) break;

            for (Location repeater : worldRepeaters) {
                if (hopCount.containsKey(repeater)) continue;

                if (current.distanceSquared(repeater) <= currentRangeSq) {
                    hopCount.put(repeater, currentHops + 1);
                    queue.add(repeater);
                }
            }
        }

        return reachedTargets;
    }
}