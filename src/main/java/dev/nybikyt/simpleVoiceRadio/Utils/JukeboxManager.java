package dev.nybikyt.simpleVoiceRadio.Utils;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.JukeboxPlayableComponent;
import org.bukkit.persistence.PersistentDataType;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import java.util.Map;

public class JukeboxManager {
    private final SimpleVoiceRadio plugin;
    public static final NamespacedKey CUSTOM_DISC_KEY = NamespacedKey.fromString("simple_voice_radio_disc");
    private static final Map<Integer, JukeboxSong> SIGNAL_TO_SONG = Map.ofEntries(
            Map.entry(1, JukeboxSong.THIRTEEN),
            Map.entry(2, JukeboxSong.CAT),
            Map.entry(3, JukeboxSong.BLOCKS),
            Map.entry(4, JukeboxSong.CHIRP),
            Map.entry(5, JukeboxSong.FAR),
            Map.entry(6, JukeboxSong.MALL),
            Map.entry(7, JukeboxSong.MELLOHI),
            Map.entry(8, JukeboxSong.STAL),
            Map.entry(9, JukeboxSong.STRAD),
            Map.entry(10, JukeboxSong.WARD),
            Map.entry(11, JukeboxSong.ELEVEN),
            Map.entry(12, JukeboxSong.WAIT),
            Map.entry(13, JukeboxSong.PRECIPICE),
            Map.entry(14, JukeboxSong.OTHERSIDE),
            Map.entry(15, JukeboxSong.FIVE)
    );

    public JukeboxManager(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
    }

    public static int calculateSignalLevel(double distance, double maxRadius) {
        if (distance > maxRadius) return 0;

        int signalLevel = 15 - (int) Math.floor(distance / maxRadius * 15);

        return Math.max(0, Math.min(15, signalLevel));
    }

    public void updateJukeboxDisc(Location location, int signalLevel) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Block block = location.getBlock();

            if (!(block.getState() instanceof Jukebox jukebox)) return;

            ItemStack currentDisc = jukebox.getRecord();
            ItemStack newDisc = (signalLevel > 0 && signalLevel <= 15) ? createCustomMusicDisc(signalLevel) : null;

            if (currentDisc.isSimilar(newDisc)) return;

            jukebox.setRecord(newDisc);
            jukebox.stopPlaying();
            jukebox.update();
        });
    }

    public static ItemStack createCustomMusicDisc(int signalLevel) {
        if (signalLevel <= 0 || signalLevel > 15) {
            return ItemStack.of(Material.AIR);
        }

        ItemStack musicDisc = new ItemStack(Material.MUSIC_DISC_STAL);
        ItemMeta meta = musicDisc.getItemMeta();

        JukeboxPlayableComponent component = meta.getJukeboxPlayable();
        component.setSong(SIGNAL_TO_SONG.get(signalLevel));
        meta.getPersistentDataContainer().set(
                CUSTOM_DISC_KEY,
                PersistentDataType.BOOLEAN,
                true);
        meta.setHideTooltip(true);
        meta.setJukeboxPlayable(component);

        musicDisc.setItemMeta(meta);

        return musicDisc;
    }
}