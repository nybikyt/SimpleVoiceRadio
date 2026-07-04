package dev.nybikyt.simpleVoiceRadio.Utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class DataManager {

    private static final int DATA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final SimpleVoiceRadio plugin;
    private final File jsonFile;
    private final File legacyFile;
    private final Map<ChunkKey, Map<BlockKey, Radio>> byChunk = new ConcurrentHashMap<>();
    private final Map<RadioState, Map<Integer, Set<BlockKey>>> byStateAndFrequency = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private Scheduler.TaskHandle saveTask;

    public DataManager(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
        this.jsonFile = new File(plugin.getDataFolder(), "radio-blocks.json");
        this.legacyFile = new File(plugin.getDataFolder(), "radio-blocks.dat");
    }

    public enum RadioState {
        INPUT, OUTPUT, BROADCAST, LISTEN, DESTROYED;

        private final String key = name().toLowerCase(Locale.ROOT);

        public String key() {
            return key;
        }

        public static RadioState fromKey(String key) {
            for (RadioState state : values()) {
                if (state.key.equals(key)) return state;
            }
            return OUTPUT;
        }
    }

    public record BlockKey(String world, int x, int y, int z) {

        public static BlockKey of(Location location) {
            return new BlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        public ChunkKey chunk() {
            return new ChunkKey(world, x >> 4, z >> 4);
        }

        public Location toLocation() {
            World bukkitWorld = Bukkit.getWorld(world);
            return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z);
        }
    }

    public record ChunkKey(String world, int cx, int cz) {
    }

    public static final class Radio {

        private volatile int frequency;
        private volatile RadioState state;
        private final List<UUID> textureIds;
        private final UUID frequencyDisplayId;

        Radio(int frequency, RadioState state, List<UUID> textureIds, UUID frequencyDisplayId) {
            this.frequency = frequency;
            this.state = state;
            this.textureIds = List.copyOf(textureIds);
            this.frequencyDisplayId = frequencyDisplayId;
        }

        public int getFrequency() {
            return frequency;
        }

        public RadioState getState() {
            return state;
        }

        public List<ItemDisplay> getTextures() {
            List<ItemDisplay> result = new ArrayList<>(textureIds.size());
            for (UUID id : textureIds) {
                if (Bukkit.getEntity(id) instanceof ItemDisplay display) result.add(display);
            }
            return result;
        }

        public TextDisplay getFrequencyDisplay() {
            return frequencyDisplayId != null && Bukkit.getEntity(frequencyDisplayId) instanceof TextDisplay display ? display : null;
        }
    }

    public Radio add(Location location, int frequency, RadioState state, List<ItemDisplay> textures, TextDisplay frequencyDisplay) {
        BlockKey key = BlockKey.of(location);
        List<UUID> textureIds = textures.stream().map(ItemDisplay::getUniqueId).toList();
        Radio radio = new Radio(frequency, state, textureIds, frequencyDisplay != null ? frequencyDisplay.getUniqueId() : null);
        byChunk.computeIfAbsent(key.chunk(), c -> new ConcurrentHashMap<>()).put(key, radio);
        index(key, state, frequency);
        markDirty();
        return radio;
    }

    public void remove(Location location) {
        BlockKey key = BlockKey.of(location);
        Map<BlockKey, Radio> chunkRadios = byChunk.get(key.chunk());
        if (chunkRadios == null) return;
        Radio radio = chunkRadios.remove(key);
        if (radio == null) return;
        if (chunkRadios.isEmpty()) byChunk.remove(key.chunk(), chunkRadios);
        unindex(key, radio.state, radio.frequency);
        markDirty();
    }

    public Radio get(Location location) {
        BlockKey key = BlockKey.of(location);
        Map<BlockKey, Radio> chunkRadios = byChunk.get(key.chunk());
        return chunkRadios == null ? null : chunkRadios.get(key);
    }

    public int getRadioCountInChunk(Location location) {
        Map<BlockKey, Radio> chunkRadios = byChunk.get(BlockKey.of(location).chunk());
        return chunkRadios == null ? 0 : chunkRadios.size();
    }

    public void updateFrequency(Location location, int frequency) {
        BlockKey key = BlockKey.of(location);
        Radio radio = get(location);
        if (radio == null || radio.frequency == frequency) return;
        unindex(key, radio.state, radio.frequency);
        radio.frequency = frequency;
        index(key, radio.state, frequency);
        markDirty();
    }

    public void updateState(Location location, RadioState state) {
        BlockKey key = BlockKey.of(location);
        Radio radio = get(location);
        if (radio == null || radio.state == state) return;
        unindex(key, radio.state, radio.frequency);
        radio.state = state;
        index(key, state, radio.frequency);
        markDirty();
    }

    public Map<Location, Radio> getRadios(RadioState state) {
        Map<Location, Radio> result = new HashMap<>();
        Map<Integer, Set<BlockKey>> byFrequency = byStateAndFrequency.get(state);
        if (byFrequency == null) return result;
        for (Set<BlockKey> keys : byFrequency.values()) {
            collect(keys, state, null, result);
        }
        return result;
    }

    public Map<Location, Radio> getRadios(RadioState state, int frequency) {
        Map<Location, Radio> result = new HashMap<>();
        Map<Integer, Set<BlockKey>> byFrequency = byStateAndFrequency.get(state);
        if (byFrequency == null) return result;
        collect(byFrequency.get(frequency), state, frequency, result);
        return result;
    }

    public Map<Location, Radio> findInputRadiosNear(Location center, double radius) {
        Map<Location, Radio> result = new HashMap<>();
        World world = center.getWorld();
        if (world == null) return result;
        double radiusSquared = radius * radius;
        int minCx = (int) Math.floor(center.getX() - radius) >> 4;
        int maxCx = (int) Math.floor(center.getX() + radius) >> 4;
        int minCz = (int) Math.floor(center.getZ() - radius) >> 4;
        int maxCz = (int) Math.floor(center.getZ() + radius) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                Map<BlockKey, Radio> chunkRadios = byChunk.get(new ChunkKey(world.getName(), cx, cz));
                if (chunkRadios == null) continue;
                for (Map.Entry<BlockKey, Radio> entry : chunkRadios.entrySet()) {
                    Radio radio = entry.getValue();
                    if (radio.state != RadioState.INPUT || radio.frequency <= 0) continue;
                    BlockKey key = entry.getKey();
                    double dx = key.x() + 0.5 - center.getX();
                    double dy = key.y() + 0.5 - center.getY();
                    double dz = key.z() + 0.5 - center.getZ();
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
                    Location location = key.toLocation();
                    if (location != null) result.put(location, radio);
                }
            }
        }
        return result;
    }

    public void forEachRadio(RadioState state, int frequency, java.util.function.BiConsumer<Location, Radio> consumer) {
        Map<Integer, Set<BlockKey>> byFrequency = byStateAndFrequency.get(state);
        if (byFrequency == null) return;
        Set<BlockKey> keys = byFrequency.get(frequency);
        if (keys == null) return;
        for (BlockKey key : keys) {
            Map<BlockKey, Radio> chunkRadios = byChunk.get(key.chunk());
            Radio radio = chunkRadios == null ? null : chunkRadios.get(key);
            if (radio == null || radio.state != state || radio.frequency != frequency) continue;
            Location location = key.toLocation();
            if (location != null) consumer.accept(location, radio);
        }
    }

    private void collect(Set<BlockKey> keys, RadioState state, Integer frequency, Map<Location, Radio> result) {
        if (keys == null) return;
        for (BlockKey key : keys) {
            Map<BlockKey, Radio> chunkRadios = byChunk.get(key.chunk());
            Radio radio = chunkRadios == null ? null : chunkRadios.get(key);
            if (radio == null || radio.state != state) continue;
            if (frequency != null && radio.frequency != frequency) continue;
            Location location = key.toLocation();
            if (location != null) result.put(location, radio);
        }
    }

    private void index(BlockKey key, RadioState state, int frequency) {
        byStateAndFrequency
                .computeIfAbsent(state, s -> new ConcurrentHashMap<>())
                .computeIfAbsent(frequency, f -> ConcurrentHashMap.newKeySet())
                .add(key);
    }

    private void unindex(BlockKey key, RadioState state, int frequency) {
        Map<Integer, Set<BlockKey>> byFrequency = byStateAndFrequency.get(state);
        if (byFrequency == null) return;
        Set<BlockKey> keys = byFrequency.get(frequency);
        if (keys == null) return;
        keys.remove(key);
        if (keys.isEmpty()) byFrequency.remove(frequency, keys);
    }

    private void markDirty() {
        dirty.set(true);
    }

    public void startAutoSave() {
        saveTask = Scheduler.runAsyncTimer(plugin, this::saveIfDirty, 600L, 600L);
    }

    public void shutdown() {
        if (saveTask != null) saveTask.cancel();
        save();
    }

    private void saveIfDirty() {
        if (dirty.getAndSet(false)) save();
    }

    public void load() {
        if (jsonFile.exists()) {
            loadJson();
        } else if (legacyFile.exists()) {
            migrateLegacy();
        }
    }

    private void loadJson() {
        try {
            String content = Files.readString(jsonFile.toPath(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            int loaded = 0;
            for (JsonElement element : root.getAsJsonArray("radios")) {
                JsonObject entry = element.getAsJsonObject();
                BlockKey key = new BlockKey(
                        entry.get("world").getAsString(),
                        entry.get("x").getAsInt(),
                        entry.get("y").getAsInt(),
                        entry.get("z").getAsInt()
                );
                List<UUID> textureIds = new ArrayList<>();
                for (JsonElement id : entry.getAsJsonArray("textures")) {
                    textureIds.add(UUID.fromString(id.getAsString()));
                }
                UUID displayId = entry.has("frequencyDisplay") && !entry.get("frequencyDisplay").isJsonNull()
                        ? UUID.fromString(entry.get("frequencyDisplay").getAsString())
                        : null;
                Radio radio = new Radio(
                        entry.get("frequency").getAsInt(),
                        RadioState.fromKey(entry.get("state").getAsString()),
                        textureIds,
                        displayId
                );
                byChunk.computeIfAbsent(key.chunk(), c -> new ConcurrentHashMap<>()).put(key, radio);
                index(key, radio.state, radio.frequency);
                loaded++;
            }
            SimpleVoiceRadio.LOGGER.info("Loaded {} radio blocks", loaded);
        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Error loading radio data", e);
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", DATA_VERSION);
        JsonArray radios = new JsonArray();
        for (Map<BlockKey, Radio> chunkRadios : byChunk.values()) {
            for (Map.Entry<BlockKey, Radio> entry : chunkRadios.entrySet()) {
                BlockKey key = entry.getKey();
                Radio radio = entry.getValue();
                JsonObject json = new JsonObject();
                json.addProperty("world", key.world());
                json.addProperty("x", key.x());
                json.addProperty("y", key.y());
                json.addProperty("z", key.z());
                json.addProperty("frequency", radio.frequency);
                json.addProperty("state", radio.state.key());
                JsonArray textures = new JsonArray();
                radio.textureIds.forEach(id -> textures.add(id.toString()));
                json.add("textures", textures);
                json.addProperty("frequencyDisplay", radio.frequencyDisplayId != null ? radio.frequencyDisplayId.toString() : null);
                radios.add(json);
            }
        }
        root.add("radios", radios);

        try {
            File folder = jsonFile.getParentFile();
            if (!folder.exists()) folder.mkdirs();
            Path target = jsonFile.toPath();
            Path temp = target.resolveSibling(jsonFile.getName() + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            SimpleVoiceRadio.LOGGER.error("Error saving radio data", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void migrateLegacy() {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(legacyFile))) {
            Object obj = in.readObject();
            if (!(obj instanceof Map)) return;
            int migrated = 0;
            for (Map.Entry<String, Map<String, RadioData>> chunkEntry : ((Map<String, Map<String, RadioData>>) obj).entrySet()) {
                String[] chunkParts = chunkEntry.getKey().split(",");
                if (chunkParts.length < 3) continue;
                String world = String.join(",", java.util.Arrays.copyOfRange(chunkParts, 0, chunkParts.length - 2));
                for (Map.Entry<String, RadioData> radioEntry : chunkEntry.getValue().entrySet()) {
                    String[] parts = radioEntry.getKey().split(",");
                    if (parts.length != 3) continue;
                    BlockKey key = new BlockKey(world, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    RadioData legacy = radioEntry.getValue();
                    Radio radio = new Radio(
                            legacy.frequency,
                            RadioState.fromKey(legacy.state),
                            legacy.texturesUUID != null ? legacy.texturesUUID : List.of(),
                            legacy.frequencyDisplayUUID
                    );
                    byChunk.computeIfAbsent(key.chunk(), c -> new ConcurrentHashMap<>()).put(key, radio);
                    index(key, radio.state, radio.frequency);
                    migrated++;
                }
            }
            save();
            Files.move(legacyFile.toPath(), legacyFile.toPath().resolveSibling("radio-blocks.dat.backup"), StandardCopyOption.REPLACE_EXISTING);
            SimpleVoiceRadio.LOGGER.info("Migrated {} radio blocks from legacy storage", migrated);
        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Error migrating legacy radio data", e);
        }
    }

    private static class RadioData implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private int frequency;
        private String state;
        private int SignalPropagationRadius;
        private List<UUID> texturesUUID;
        private UUID frequencyDisplayUUID;
    }
}
