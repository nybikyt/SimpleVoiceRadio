package dev.nybikyt.simpleVoiceRadio.Audio;

import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.BlockKey;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.Radio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.RadioState;
import dev.nybikyt.simpleVoiceRadio.Utils.JukeboxManager;
import dev.nybikyt.simpleVoiceRadio.Utils.Scheduler;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SignalManager {

    private static final long SOURCE_HOLD_MILLIS = 300L;
    private static final long TICK_PERIOD = 2L;

    private final SimpleVoiceRadio plugin;
    private final DataManager dataManager;
    private final AntennaManager antennaManager;

    private final Map<SourceKey, Source> frequencySources = new ConcurrentHashMap<>();
    private final Map<SourceKey, Source> directSources = new ConcurrentHashMap<>();
    private final Map<BlockKey, Integer> appliedLevels = new ConcurrentHashMap<>();
    private final Scheduler.TaskHandle tickTask;

    private record SourceKey(BlockKey transmitter, UUID streamId) {
    }

    private record Source(int frequency, int level, long expiresAt) {
    }

    public SignalManager(SimpleVoiceRadio plugin, DataManager dataManager, AntennaManager antennaManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.antennaManager = antennaManager;
        this.tickTask = Scheduler.runGlobalTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public static int calculateLevel(double distance, double maxRadius) {
        if (maxRadius <= 0 || distance > maxRadius) return 0;
        int level = (int) Math.floor((1.0 - distance / maxRadius) * 15.0) + 1;
        return Math.max(1, Math.min(15, level));
    }

    public void report(int frequency, BlockKey transmitter, UUID streamId, int level) {
        if (level <= 0 || frequency <= 0) return;
        frequencySources.put(new SourceKey(transmitter, streamId), new Source(frequency, level, System.currentTimeMillis() + SOURCE_HOLD_MILLIS));
    }

    public void reportDirect(BlockKey radio, UUID streamId, int level) {
        if (level <= 0) return;
        directSources.put(new SourceKey(radio, streamId), new Source(0, level, System.currentTimeMillis() + SOURCE_HOLD_MILLIS));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        frequencySources.values().removeIf(source -> source.expiresAt() < now);
        directSources.values().removeIf(source -> source.expiresAt() < now);

        Map<Integer, Map<Location, Radio>> outputsByFrequency = new HashMap<>();
        Map<BlockKey, Integer> desired = new HashMap<>();

        for (Map.Entry<SourceKey, Source> entry : frequencySources.entrySet()) {
            Source source = entry.getValue();
            Map<Location, Radio> outputs = outputsByFrequency.computeIfAbsent(source.frequency(),
                    frequency -> dataManager.getRadios(RadioState.OUTPUT, frequency));

            for (Location location : outputs.keySet()) {
                if (!location.isChunkLoaded()) continue;
                BlockKey outputKey = BlockKey.of(location);
                if (!antennaManager.inRange(entry.getKey().transmitter(), outputKey)) continue;
                desired.merge(outputKey, source.level(), Math::max);
            }
        }

        for (Map.Entry<SourceKey, Source> entry : directSources.entrySet()) {
            BlockKey radioKey = entry.getKey().transmitter();
            Location location = radioKey.toLocation();
            if (location == null || !location.isChunkLoaded()) continue;
            desired.merge(radioKey, entry.getValue().level(), Math::max);
        }

        for (BlockKey outputKey : appliedLevels.keySet()) {
            if (!desired.containsKey(outputKey)) {
                apply(outputKey, 0);
            }
        }
        desired.forEach((outputKey, level) -> {
            Integer applied = appliedLevels.get(outputKey);
            if (applied == null || applied != level) {
                apply(outputKey, level);
            }
        });
    }

    private void apply(BlockKey outputKey, int level) {
        if (level <= 0) {
            appliedLevels.remove(outputKey);
        } else {
            appliedLevels.put(outputKey, level);
        }
        Location location = outputKey.toLocation();
        if (location == null || !location.isChunkLoaded()) return;
        Scheduler.runAt(plugin, location, () -> JukeboxManager.applyDisc(location, level));
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        for (BlockKey outputKey : appliedLevels.keySet()) {
            apply(outputKey, 0);
        }
        frequencySources.clear();
        directSources.clear();
    }
}
