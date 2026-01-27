package org.nyt.simpleVoiceRadio.Utils;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DataManager {
    private final SimpleVoiceRadio plugin;
    private final File dataFile;
    private final Map<String, Map<String, RadioData>> data = new ConcurrentHashMap<>();
    private Set<String> repeaters = ConcurrentHashMap.newKeySet();
    private BukkitTask saveTask;

    public DataManager(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "radio-blocks.dat");
    }

    public void startAutoSave() {
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveSync, 600L, 600L);
    }

    public void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        saveSync();
    }

    public void addRepeater(Location loc) {
        repeaters.add(serializeLocationWithWorld(loc));
    }

    public void removeRepeater(Location loc) {
        repeaters.remove(serializeLocationWithWorld(loc));
    }

    public Set<Location> getAllRepeaters() {
        Set<Location> locations = new HashSet<>();
        for (String s : repeaters) {
            Location loc = deserializeLocationWithWorld(s);
            if (loc != null) {
                locations.add(loc);
            }
        }
        return locations;
    }

    public void removeBlock(Location loc) {
        String chunkKey = getChunkKey(loc);
        Map<String, RadioData> chunkData = data.get(chunkKey);

        if (chunkData != null) {
            chunkData.remove(serializeLocation(loc));
            if (chunkData.isEmpty()) data.remove(chunkKey);
        }
    }

    public void setBlock(Location loc, int frequency, String state, List<ItemDisplay> textures, TextDisplay frequencyDisplay) {
        List<UUID> texturesUUID = textures.stream()
                .map(ItemDisplay::getUniqueId)
                .collect(Collectors.toList());
        UUID freqDisplayUUID = frequencyDisplay.getUniqueId();

        String chunkKey = getChunkKey(loc);
        String locKey = serializeLocation(loc);

        data.putIfAbsent(chunkKey, new ConcurrentHashMap<>());
        Map<String, RadioData> chunkData = data.get(chunkKey);

        chunkData.put(locKey, new RadioData(frequency, state, texturesUUID, freqDisplayUUID));
    }

    @SuppressWarnings("unchecked")
    public void load() {
        if (!dataFile.exists()) return;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(dataFile))) {
            Object objData = in.readObject();
            if (objData instanceof Map) {
                data.clear();
                Map<String, Map<String, RadioData>> loadedData = (Map<String, Map<String, RadioData>>) objData;
                for (Map.Entry<String, Map<String, RadioData>> entry : loadedData.entrySet()) {
                    data.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
                }
                SimpleVoiceRadio.LOGGER.info("Loaded {} chunks with radio blocks", data.size());
            }

            try {
                Object objRepeaters = in.readObject();
                if (objRepeaters instanceof Set) {
                    repeaters.clear();
                    repeaters.addAll((Set<String>) objRepeaters);
                    SimpleVoiceRadio.LOGGER.info("Loaded {} lightning rod repeaters", repeaters.size());
                }
            } catch (EOFException e) {
                SimpleVoiceRadio.LOGGER.info("No repeaters found in save file (most likely legacy format).");
            }

        } catch (IOException | ClassNotFoundException e) {
            SimpleVoiceRadio.LOGGER.error("Error loading data: {}", e.getMessage());
        }
    }

    private void saveSync() {
        try {
            if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(dataFile))) {
                out.writeObject(data);
                out.writeObject(new HashSet<>(repeaters));
            }
        } catch (IOException e) {
            SimpleVoiceRadio.LOGGER.error("Error saving data: {}", e.getMessage());
        }
    }

    private String getChunkKey(Location loc) {
        Chunk chunk = loc.getChunk();
        return chunk.getWorld().getName() + "," +
                chunk.getX() + "," +
                chunk.getZ();
    }

    public RadioData getBlock(Location loc) {
        String chunkKey = getChunkKey(loc);
        Map<String, RadioData> chunkData = data.get(chunkKey);
        if (chunkData == null) return null;
        return chunkData.get(serializeLocation(loc));
    }

    public int getRadioCountInChunk(Location loc) {
        String chunkKey = getChunkKey(loc);
        Map<String, RadioData> chunkData = data.get(chunkKey);
        return chunkData != null ? chunkData.size() : 0;
    }

    public Map<Location, RadioData> getAllRadiosByState(String state) {
        Map<Location, RadioData> result = new HashMap<>();

        for (Map.Entry<String, Map<String, RadioData>> chunkEntry : data.entrySet()) {
            String chunkKey = chunkEntry.getKey();
            String[] chunkParts = chunkKey.split(",");
            String worldName = chunkParts[0];
            World world = Bukkit.getWorld(worldName);

            if (world == null) continue;

            for (Map.Entry<String, RadioData> radioEntry : chunkEntry.getValue().entrySet()) {
                RadioData radioData = radioEntry.getValue();

                if (radioData.getState().equals(state)) {
                    Location loc = deserializeLocation(radioEntry.getKey(), world);
                    result.put(loc, radioData);
                }
            }
        }

        return result;
    }

    public Map<Location, RadioData> getAllRadiosByStateAndFrequency(String state, int frequency) {
        Map<Location, RadioData> result = new HashMap<>();

        for (Map.Entry<String, Map<String, RadioData>> chunkEntry : data.entrySet()) {
            String chunkKey = chunkEntry.getKey();
            String[] chunkParts = chunkKey.split(",");
            String worldName = chunkParts[0];
            World world = Bukkit.getWorld(worldName);

            if (world == null) continue;

            for (Map.Entry<String, RadioData> radioEntry : chunkEntry.getValue().entrySet()) {
                RadioData radioData = radioEntry.getValue();

                if (radioData.getState().equals(state) && radioData.getFrequency() == frequency) {
                    Location loc = deserializeLocation(radioEntry.getKey(), world);
                    result.put(loc, radioData);
                }
            }
        }

        return result;
    }

    private String serializeLocation(Location loc) {
        return loc.getBlockX() + "," +
                loc.getBlockY() + "," +
                loc.getBlockZ();
    }

    private String serializeLocationWithWorld(Location loc) {
        World world = loc.getWorld();
        if (world == null) return null;
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }


    private Location deserializeLocation(String serialized, World world) {
        String[] parts = serialized.split(",");
        int x = Integer.parseInt(parts[0]);
        int y = Integer.parseInt(parts[1]);
        int z = Integer.parseInt(parts[2]);
        return new Location(world, x, y, z);
    }

    private Location deserializeLocationWithWorld(String serialized) {
        try {
            String[] parts = serialized.split(",");
            if (parts.length != 4) return null;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }

    public static class RadioData implements Serializable {

        public RadioData(int frequency, String state, List<UUID> texturesUUID, UUID frequencyDisplayUUID) {
            this.frequency = frequency;
            this.state = state;
            this.texturesUUID = texturesUUID;
            this.frequencyDisplayUUID = frequencyDisplayUUID;
        }

        private int frequency;
        private String state;
        private int SignalPropagationRadius;
        private List<UUID> texturesUUID;
        private UUID frequencyDisplayUUID;

        @Serial
        private static final long serialVersionUID = 1L;


        public int getFrequency() {
            return frequency;
        }

        public void setFrequency(int frequency) {
            this.frequency = frequency;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public List<ItemDisplay> getTextures() {
            return texturesUUID.stream()
                    .map(Bukkit::getEntity)
                    .filter(e -> e instanceof ItemDisplay)
                    .map(e -> (ItemDisplay) e)
                    .collect(Collectors.toList());
        }

        public TextDisplay getFrequencyDisplay() {
            var entity = Bukkit.getEntity(frequencyDisplayUUID);
            return (entity instanceof TextDisplay) ? (TextDisplay) entity : null;
        }
    }
}