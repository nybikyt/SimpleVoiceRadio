package dev.nybikyt.simpleVoiceRadio.Utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Objects;

public final class JukeboxManager {

    public static final NamespacedKey CUSTOM_DISC_KEY = Objects.requireNonNull(NamespacedKey.fromString("simplevoiceradio:signal_disc"));

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
            Map.entry(13, Material.MUSIC_DISC_PIGSTEP),
            Map.entry(14, Material.MUSIC_DISC_OTHERSIDE),
            Map.entry(15, Material.MUSIC_DISC_5)
    );

    private JukeboxManager() {
    }

    public static void applyDisc(Location location, int signalLevel) {
        Block block = location.getBlock();
        if (!(block.getState() instanceof Jukebox jukebox)) return;

        ItemStack currentDisc = jukebox.getRecord();
        ItemStack newDisc = createCustomMusicDisc(signalLevel);

        if (currentDisc.isSimilar(newDisc)) return;

        jukebox.setRecord(newDisc);
        jukebox.stopPlaying();
        jukebox.update();
    }

    private static ItemStack createCustomMusicDisc(int signalLevel) {
        if (signalLevel <= 0 || signalLevel > 15) return new ItemStack(Material.AIR);

        ItemStack disc = new ItemStack(SIGNAL_TO_DISC.get(signalLevel));
        ItemMeta meta = disc.getItemMeta();
        if (meta == null) return disc;

        meta.getPersistentDataContainer().set(CUSTOM_DISC_KEY, PersistentDataType.BYTE, (byte) 1);
        disc.setItemMeta(meta);

        return disc;
    }
}
