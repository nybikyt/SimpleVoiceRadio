package dev.nybikyt.simpleVoiceRadio.Utils;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;

import java.util.Map;

public class JukeboxManager {
    private final SimpleVoiceRadio plugin;
    public static final NamespacedKey CUSTOM_DISC_KEY = NamespacedKey.fromString("simple_voice_radio_disc");

    private static final Map<Integer, Material> SIGNAL_TO_DISC = Map.ofEntries(
            Map.entry(1,  Material.MUSIC_DISC_13),
            Map.entry(2,  Material.MUSIC_DISC_CAT),
            Map.entry(3,  Material.MUSIC_DISC_BLOCKS),
            Map.entry(4,  Material.MUSIC_DISC_CHIRP),
            Map.entry(5,  Material.MUSIC_DISC_FAR),
            Map.entry(6,  Material.MUSIC_DISC_MALL),
            Map.entry(7,  Material.MUSIC_DISC_MELLOHI),
            Map.entry(8,  Material.MUSIC_DISC_STAL),
            Map.entry(9,  Material.MUSIC_DISC_STRAD),
            Map.entry(10, Material.MUSIC_DISC_WARD),
            Map.entry(11, Material.MUSIC_DISC_11),
            Map.entry(12, Material.MUSIC_DISC_WAIT),
            Map.entry(13, Material.MUSIC_DISC_OTHERSIDE),
            Map.entry(14, Material.MUSIC_DISC_PIGSTEP),
            Map.entry(15, Material.MUSIC_DISC_OTHERSIDE)
    );

    public JukeboxManager(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
    }

    public static int calculateSignalLevel(double distance, double maxRadius) {
        if (distance > maxRadius) return 0;
        return Math.max(0, Math.min(15, 15 - (int) Math.floor(distance / maxRadius * 15)));
    }

    public void updateJukeboxDisc(Location location, int signalLevel) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Block block = location.getBlock();
            if (!(block.getState() instanceof Jukebox jukebox)) return;

            ItemStack currentDisc = jukebox.getRecord();
            ItemStack newDisc = (signalLevel > 0 && signalLevel <= 15)
                    ? createCustomMusicDisc(signalLevel)
                    : new ItemStack(Material.AIR);

            if (currentDisc.isSimilar(newDisc)) return;

            jukebox.setRecord(newDisc);
            jukebox.stopPlaying();
            jukebox.update();
        });
    }

    public static ItemStack createCustomMusicDisc(int signalLevel) {
        if (signalLevel <= 0 || signalLevel > 15) return new ItemStack(Material.AIR);

        ItemStack disc = new ItemStack(SIGNAL_TO_DISC.get(signalLevel));
        ItemMeta meta = disc.getItemMeta();
        if (meta == null) return disc;

        meta.getPersistentDataContainer().set(CUSTOM_DISC_KEY, PersistentDataType.BYTE, (byte) 1);
        disc.setItemMeta(meta);

        return disc;
    }
}