package dev.nybikyt.simpleVoiceRadio.Audio;

import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.BlockKey;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.RadioState;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.Utils.Scheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AntennaManager {

    private static final Set<Material> LIGHTNING_RODS = buildLightningRods();

    private final SimpleVoiceRadio plugin;
    private final PluginConfig config;
    private final DataManager dataManager;
    private final Map<BlockKey, Antenna> antennas = new ConcurrentHashMap<>();
    private Scheduler.TaskHandle refreshTask;

    private record Antenna(int rods, boolean obstructed) {
    }

    private static Set<Material> buildLightningRods() {
        Set<Material> rods = EnumSet.noneOf(Material.class);
        String[] names = {
                "LIGHTNING_ROD",
                "EXPOSED_LIGHTNING_ROD",
                "WEATHERED_LIGHTNING_ROD",
                "OXIDIZED_LIGHTNING_ROD",
                "WAXED_LIGHTNING_ROD",
                "WAXED_EXPOSED_LIGHTNING_ROD",
                "WAXED_WEATHERED_LIGHTNING_ROD",
                "WAXED_OXIDIZED_LIGHTNING_ROD"
        };
        for (String name : names) {
            Material material = Material.getMaterial(name);
            if (material != null) rods.add(material);
        }
        return rods;
    }

    public static boolean isLightningRod(Material material) {
        return LIGHTNING_RODS.contains(material);
    }

    public AntennaManager(SimpleVoiceRadio plugin, PluginConfig config, DataManager dataManager) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;

        if (config.chunkMode()) {
            long interval = config.antennaUpdateIntervalTicks();
            refreshTask = Scheduler.runGlobalTimer(plugin, this::refreshAll, interval, interval);
        }
    }

    private void refreshAll() {
        for (RadioState state : new RadioState[]{RadioState.INPUT, RadioState.LISTEN}) {
            dataManager.getRadios(state).forEach((location, radio) -> {
                if (!location.isChunkLoaded()) return;
                Scheduler.runAt(plugin, location, () -> antennas.put(BlockKey.of(location), scan(location)));
            });
        }
    }

    private Antenna scan(Location radioLocation) {
        World world = radioLocation.getWorld();
        int x = radioLocation.getBlockX();
        int z = radioLocation.getBlockZ();
        int y = radioLocation.getBlockY() + 1;
        int maxRods = config.antennaRange().lastKey();

        int rods = 0;
        while (rods < maxRods && y <= world.getMaxHeight()) {
            Block block = world.getBlockAt(x, y, z);
            if (!isLightningRod(block.getType())) break;
            if (!(block.getBlockData() instanceof Directional directional) || directional.getFacing() != BlockFace.UP) break;
            rods++;
            y++;
        }

        boolean obstructed = false;
        if (config.antennaObstructionCheck()) {
            int highest = world.getHighestBlockYAt(x, z);
            for (int checkY = y; checkY <= highest; checkY++) {
                if (world.getBlockAt(x, checkY, z).getType().isOccluding()) {
                    obstructed = true;
                    break;
                }
            }
        }

        return new Antenna(rods, obstructed);
    }

    public void forget(Location location) {
        antennas.remove(BlockKey.of(location));
    }

    public boolean inRange(BlockKey transmitter, BlockKey receiver) {
        if (!config.chunkMode()) return true;
        if (!transmitter.world().equals(receiver.world())) return false;

        Antenna antenna = antennas.getOrDefault(transmitter, new Antenna(0, false));
        if (antenna.obstructed()) return false;

        Map.Entry<Integer, Integer> entry = config.antennaRange().floorEntry(antenna.rods());
        int range = entry != null ? entry.getValue() : 0;

        int chunkDistanceX = Math.abs((transmitter.x() >> 4) - (receiver.x() >> 4));
        int chunkDistanceZ = Math.abs((transmitter.z() >> 4) - (receiver.z() >> 4));
        return Math.max(chunkDistanceX, chunkDistanceZ) <= range;
    }

    public void shutdown() {
        if (refreshTask != null) refreshTask.cancel();
        antennas.clear();
    }
}
