package org.nyt.simpleVoiceRadio.Bridges;

import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;
import org.nyt.simpleVoiceRadio.Utils.DataManager;
import org.nyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import org.nyt.simpleVoiceRadio.VoiceAddon;
import space.subkek.customdiscs.api.CustomDiscsAPI;
import space.subkek.customdiscs.api.event.CustomDiscInsertEvent;
import space.subkek.customdiscs.api.event.CustomDiscStopPlayingEvent;

public class CustomDiscs implements Listener {
    private final CustomDiscsAPI api;
    private final SimpleVoiceRadio plugin;
    private final DataManager dataManager;
    private final DisplayEntityManager displayEntityManager;
    private final VoiceAddon voiceAddon;

    public CustomDiscs(SimpleVoiceRadio plugin, DataManager dataManager, DisplayEntityManager displayEntityManager, VoiceAddon voiceAddon) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.displayEntityManager = displayEntityManager;
        this.voiceAddon = voiceAddon;
        this.api = CustomDiscsAPI.get();
    }

    @EventHandler
    public void onDiscInsert(CustomDiscInsertEvent event) {
        if (event.getPlayer() == null) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Block jukeboxBlock = event.getBlock();
            Location aboveLocation = jukeboxBlock.getLocation().clone().add(0, 1, 0);

            DataManager.RadioData blockData = dataManager.getBlock(aboveLocation);
            if (blockData == null) return;

            AudioChannel channel = api.getLavaPlayerManager().getAudioChannel(jukeboxBlock);
            if (channel == null) return;

            blockData.setState("listen");
            displayEntityManager.setStateSkin(blockData.getTextures(), blockData.getState());

            if (voiceAddon != null) {
                voiceAddon.startDiscBroadcast(aboveLocation, channel.getId());
            }
        }, 1L);
    }

    @EventHandler
    public void onDiscStop(CustomDiscStopPlayingEvent event) {
        Block jukeboxBlock = event.getBlock();
        Location aboveLocation = jukeboxBlock.getLocation().clone().add(0, 1, 0);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            DataManager.RadioData blockData = dataManager.getBlock(aboveLocation);
            if (blockData == null) return;

            if (voiceAddon != null) {
                voiceAddon.stopDiscBroadcast(aboveLocation);
            }

            blockData.setState("output");
            displayEntityManager.setStateSkin(blockData.getTextures(), blockData.getState());
        });
    }
}