package dev.nybikyt.simpleVoiceRadio.Utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nybikyt.simpleVoiceRadio.Audio.Playback;
import dev.nybikyt.simpleVoiceRadio.Handlers.ApiRadio;
import dev.nybikyt.simpleVoiceRadio.api.RadioState;
import dev.nybikyt.simpleVoiceRadio.api.events.RadioStateChangeEvent;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
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
import java.util.function.Predicate;

public class DataManager {

    private static final int DATA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final SimpleVoiceRadio plugin;
    private final File jsonFile;
    private final File legacyFile;
    private final Map<ChunkKey, Map<BlockKey, Radio>> byChunk = new ConcurrentHashMap<>();
    private final Map<RadioState, Map<Integer, Set<BlockKey>>> byStateAndFrequency = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private volatile boolean saveBlocked;
    private Scheduler.TaskHandle saveTask;

    public DataManager(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
        this.jsonFile = new File(plugin.getDataFolder(), "radio-blocks.json");
        this.legacyFile = new File(plugin.getDataFolder(), "radio-blocks.dat");
    }

    private static RadioState parseState(String key) {
        for (RadioState state : RadioState.values()) {
            if (state.name().equalsIgnoreCase(key)) return state;
        }
        return RadioState.OUTPUT;
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
        private volatile int signalLevel;
        private final List<UUID> textureIds;
        private final UUID frequencyDisplayId;
        private final Playback playback = new Playback();

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

        public List<UUID> getTextureIds() {
            return textureIds;
        }

        public UUID getFrequencyDisplayId() {
            return frequencyDisplayId;
        }

        public Playback getPlayback() {
            return playback;
        }

        public int getSignalLevel() {
            return signalLevel;
        }

        public void setSignalLevel(int signalLevel) {
            this.signalLevel = signalLevel;
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

    public boolean remove(Location location, Radio radio) {
        BlockKey key = BlockKey.of(location);
        Map<BlockKey, Radio> chunkRadios = byChunk.get(key.chunk());
        if (chunkRadios == null || !chunkRadios.remove(key, radio)) return false;
        if (chunkRadios.isEmpty()) byChunk.remove(key.chunk(), chunkRadios);
        synchronized (radio) {
            unindex(key, radio.state, radio.frequency);
        }
        markDirty();
        return true;
    }

    public Radio get(Location location) {
        return get(BlockKey.of(location));
    }

    public Radio get(BlockKey key) {
        Map<BlockKey, Radio> chunkRadios = byChunk.get(key.chunk());
        return chunkRadios == null ? null : chunkRadios.get(key);
    }

    public List<Radio> getRadiosInChunk(String world, int chunkX, int chunkZ) {
        Map<BlockKey, Radio> chunkRadios = byChunk.get(new ChunkKey(world, chunkX, chunkZ));
        return chunkRadios == null ? List.of() : List.copyOf(chunkRadios.values());
    }

    public int getRadioCountInChunk(Location location) {
        Map<BlockKey, Radio> chunkRadios = byChunk.get(BlockKey.of(location).chunk());
        return chunkRadios == null ? 0 : chunkRadios.size();
    }

    public void updateFrequency(Location location, int frequency) {
        BlockKey key = BlockKey.of(location);
        Radio radio = get(location);
        if (radio == null || radio.frequency == frequency) return;
        synchronized (radio) {
            unindex(key, radio.state, radio.frequency);
            radio.frequency = frequency;
            index(key, radio.state, frequency);
        }
        markDirty();
    }

    public boolean updateState(Location location, RadioState state, Player player) {
        return changeState(location, state, player, true);
    }

    public boolean forceState(Location location, RadioState state) {
        return changeState(location, state, null, false);
    }

    private boolean changeState(Location location, RadioState state, Player player, boolean cancellable) {
        Radio radio = get(location);
        if (radio == null) return false;
        RadioState oldState = radio.state;
        if (oldState == state) return false;

        RadioStateChangeEvent event = new RadioStateChangeEvent(new ApiRadio(plugin, location, radio), player, oldState, state);
        if (!event.callEvent() && cancellable) return false;

        BlockKey key = BlockKey.of(location);
        synchronized (radio) {
            if (get(location) != radio || radio.state != oldState) return false;
            unindex(key, oldState, radio.frequency);
            radio.state = state;
            index(key, state, radio.frequency);
        }
        markDirty();
        return true;
    }

    public Map<Location, Radio> getRadios() {
        Map<Location, Radio> result = new HashMap<>();
        for (Map<BlockKey, Radio> chunkRadios : byChunk.values()) {
            chunkRadios.forEach((key, radio) -> {
                Location location = key.toLocation();
                if (location != null) result.put(location, radio);
            });
        }
        return result;
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
        return findRadiosNear(center, radius, radio -> radio.state == RadioState.INPUT && radio.frequency > 0);
    }

    public Map<Location, Radio> findRadiosNear(Location center, double radius, Predicate<Radio> filter) {
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
                    if (!filter.test(radio)) continue;
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
        JsonArray entries;
        try {
            String content = Files.readString(jsonFile.toPath(), StandardCharsets.UTF_8);
            entries = JsonParser.parseString(content).getAsJsonObject().getAsJsonArray("radios");
            if (entries == null) throw new IllegalStateException("No radios list in " + jsonFile.getName());
        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Error loading radio data", e);
            backupUnreadableData();
            return;
        }

        int loaded = 0;
        int skipped = 0;
        for (JsonElement element : entries) {
            try {
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
                        parseState(entry.get("state").getAsString()),
                        textureIds,
                        displayId
                );
                byChunk.computeIfAbsent(key.chunk(), c -> new ConcurrentHashMap<>()).put(key, radio);
                index(key, radio.state, radio.frequency);
                loaded++;
            } catch (Exception e) {
                skipped++;
                SimpleVoiceRadio.LOGGER.error("Skipped broken radio entry {}", element, e);
            }
        }
        SimpleVoiceRadio.LOGGER.info("Loaded {} radio blocks", loaded);
        if (skipped > 0) backupUnreadableData();
    }

    private void backupUnreadableData() {
        Path backup = jsonFile.toPath().resolveSibling(jsonFile.getName() + ".broken-" + System.currentTimeMillis());
        try {
            Files.copy(jsonFile.toPath(), backup);
            SimpleVoiceRadio.LOGGER.warn("Unreadable radio data was kept as {}", backup.getFileName());
        } catch (IOException e) {
            saveBlocked = true;
            SimpleVoiceRadio.LOGGER.error("Failed to back up unreadable radio data, {} will not be overwritten until restart", jsonFile.getName(), e);
        }
    }

    private synchronized void save() {
        if (saveBlocked) return;

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
                json.addProperty("state", radio.state.name().toLowerCase(Locale.ROOT));
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
            dirty.set(true);
            SimpleVoiceRadio.LOGGER.error("Error saving radio data", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void migrateLegacy() {
        try (ObjectInputStream in = new LegacyObjectInputStream(new FileInputStream(legacyFile))) {
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
                            parseState(legacy.state),
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

    private static final class LegacyObjectInputStream extends ObjectInputStream {

        private LegacyObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            if (desc.getName().endsWith("DataManager$RadioData")) return RadioData.class;
            return super.resolveClass(desc);
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
