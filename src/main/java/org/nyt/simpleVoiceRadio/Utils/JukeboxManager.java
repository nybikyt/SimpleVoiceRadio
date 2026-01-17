package org.nyt.simpleVoiceRadio.Utils;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.JukeboxPlayableComponent;
import org.nyt.simpleVoiceRadio.Misc.SimpleVoiceRadioBootstrap;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;
// backup

public class JukeboxManager {
    private final SimpleVoiceRadio plugin;

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
            return null;
        }

        TypedKey<JukeboxSong> songKey = SimpleVoiceRadioBootstrap.SIGNAL_TO_SONG.get(signalLevel);
        if (songKey == null) {
            Bukkit.getLogger().warning("Song key not found for signal level: " + signalLevel);
            return null;
        }

        JukeboxSong song = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.JUKEBOX_SONG)
                .get(songKey);

        if (song == null) {
            Bukkit.getLogger().warning("Song not found in registry for key: " + songKey);
            return null;
        }

        ItemStack musicDisc = new ItemStack(Material.MUSIC_DISC_STAL);
        ItemMeta meta = musicDisc.getItemMeta();

        JukeboxPlayableComponent component = meta.getJukeboxPlayable();
        component.setSong(song);
        meta.setHideTooltip(true);
        meta.setJukeboxPlayable(component);

        musicDisc.setItemMeta(meta);

        return musicDisc;
    }
}