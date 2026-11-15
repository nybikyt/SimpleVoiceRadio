package dev.nybikyt.simpleVoiceRadio.Utils;

import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class PluginConfig {

    private final SimpleVoiceRadio plugin;
    private volatile Snapshot snapshot;

    private record Snapshot(
            double inputSearchRadius,
            double outputRadius,
            double viewRange,
            int maxFrequency,
            int blocksPerChunkLimit,
            boolean redstoneFrequency,
            boolean signalOutputSystem,
            boolean customDiscsIntegration,
            boolean chunkMode,
            boolean lightningDestroysRadio,
            long antennaUpdateIntervalTicks,
            int requiredClearance,
            NavigableMap<Integer, Integer> antennaRange,
            boolean effectsEnabled,
            boolean effectsForDiscs,
            boolean effectsForFiles,
            float effectSeverity,
            float effectCenterFrequency,
            float effectBandwidth
    ) {
    }

    public PluginConfig(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
        this.snapshot = read(plugin.getConfig());
    }

    public void reload() {
        plugin.reloadConfig();
        snapshot = read(plugin.getConfig());
    }

    private static Snapshot read(FileConfiguration config) {
        TreeMap<Integer, Integer> antennaRange = new TreeMap<>();
        ConfigurationSection rangeSection = config.getConfigurationSection("radio-block.antenna.range");
        if (rangeSection != null) {
            for (String key : rangeSection.getKeys(false)) {
                try {
                    antennaRange.put(Integer.parseInt(key), rangeSection.getInt(key));
                } catch (NumberFormatException e) {
                    SimpleVoiceRadio.LOGGER.warn("Invalid antenna range entry: {}", key);
                }
            }
        }
        if (antennaRange.isEmpty()) {
            antennaRange.put(0, 5);
            antennaRange.put(1, 10);
            antennaRange.put(2, 15);
        }

        return new Snapshot(
                config.getDouble("radio-block.input_search_radius", 15.0),
                config.getDouble("radio-block.output_radius", 15.0),
                config.getDouble("radio-block.view_range", 64.0),
                config.getInt("radio-block.max_frequency", 15),
                config.getInt("radio-block.blocks_per_chunk_limit", 10),
                config.getBoolean("radio-block.redstone_frequency", false),
                config.getBoolean("radio-block.signal_output_system", false),
                config.getBoolean("radio-block.custom_discs_integration", true),
                "chunk".equalsIgnoreCase(config.getString("radio-block.mode", "global")),
                config.getBoolean("radio-block.lightning_destroys_radio", true),
                Math.max(1L, config.getLong("radio-block.antenna.update_interval_ticks", 40L)),
                config.getInt("radio-block.antenna.required_clearance", 3),
                Collections.unmodifiableNavigableMap(antennaRange),
                config.getBoolean("audio-effects.enabled", true),
                config.getBoolean("audio-effects.apply_to_custom_discs", true),
                config.getBoolean("audio-effects.apply_to_files", true),
                (float) config.getDouble("audio-effects.severity", 0.05),
                (float) config.getDouble("audio-effects.center_frequency", 750.0),
                (float) config.getDouble("audio-effects.bandwidth", 4000.0)
        );
    }

    public double inputSearchRadius() {
        return snapshot.inputSearchRadius();
    }

    public double outputRadius() {
        return snapshot.outputRadius();
    }

    public double viewRange() {
        return snapshot.viewRange();
    }

    public int maxFrequency() {
        return snapshot.maxFrequency();
    }

    public int blocksPerChunkLimit() {
        return snapshot.blocksPerChunkLimit();
    }

    public boolean redstoneFrequency() {
        return snapshot.redstoneFrequency();
    }

    public boolean signalSystemActive() {
        return snapshot.signalOutputSystem() && !snapshot.redstoneFrequency();
    }

    public boolean customDiscsIntegration() {
        return snapshot.customDiscsIntegration();
    }

    public boolean chunkMode() {
        return snapshot.chunkMode();
    }

    public boolean lightningDestroysRadio() {
        return snapshot.lightningDestroysRadio();
    }

    public int requiredClearance() {
        return snapshot.requiredClearance();
    }

    public long antennaUpdateIntervalTicks() {
        return snapshot.antennaUpdateIntervalTicks();
    }

    public NavigableMap<Integer, Integer> antennaRange() {
        return snapshot.antennaRange();
    }

    public boolean effectsEnabled() {
        return snapshot.effectsEnabled();
    }

    public boolean effectsForDiscs() {
        return snapshot.effectsForDiscs();
    }

    public boolean effectsForFiles() {
        return snapshot.effectsForFiles();
    }

    public float effectSeverity() {
        return snapshot.effectSeverity();
    }

    public float effectCenterFrequency() {
        return snapshot.effectCenterFrequency();
    }

    public float effectBandwidth() {
        return snapshot.effectBandwidth();
    }
}
