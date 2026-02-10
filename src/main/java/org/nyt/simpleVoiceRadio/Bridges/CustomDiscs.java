package org.nyt.simpleVoiceRadio.Bridges;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;
import org.nyt.simpleVoiceRadio.Utils.DataManager;
import org.nyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import org.nyt.simpleVoiceRadio.VoiceChat.Utils;
import space.subkek.customdiscs.api.CustomDiscsAPI;
import space.subkek.customdiscs.api.event.CustomDiscInsertEvent;
import space.subkek.customdiscs.api.event.LavaPlayerStopPlayingEvent;

public class CustomDiscs implements Listener {
    private final CustomDiscsAPI api;
    private final SimpleVoiceRadio plugin;
    private final DataManager dataManager;
    private final DisplayEntityManager displayEntityManager;
    private final Utils utils;

    public CustomDiscs(SimpleVoiceRadio plugin, DataManager dataManager, DisplayEntityManager displayEntityManager, Utils utils) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.displayEntityManager = displayEntityManager;
        this.utils = utils;
        this.api = CustomDiscsAPI.get();

        registerPacketHandler();
    }

    private void registerPacketHandler() {
        api.getLavaPlayerManager().registerPacketHandler(plugin, (handler, block, data) -> {
            Location radioLocation = block.getLocation().add(0,1,0);
            DataManager.RadioData radioData = dataManager.getBlock(radioLocation);
            if (radioData != null) {
                if (!radioData.getState().equals("listen")) {
                    radioData.setState("listen");
                    plugin.getServer().getScheduler().runTask(plugin, () -> displayEntityManager.setStateSkin(radioData.getTextures(), radioData.getState()));
                }
                utils.handleDiscPacket(radioLocation, data);
            }
            return true;
        });
    }

    public void unregisterPacketHandler() {
        try {
            api.getLavaPlayerManager().unregisterPacketHandlers(plugin);
        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Failed to unregister CustomDiscs PacketHandler: {}", e.getMessage());
        }
    }

    @EventHandler
    public void onDiscInsert(CustomDiscInsertEvent event) {
        if (dataManager.getBlock(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDiscStop(LavaPlayerStopPlayingEvent event) {
        Block jukeboxBlock = event.getBlock();
        Location radioLocation = jukeboxBlock.getLocation().clone().add(0, 1, 0);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            DataManager.RadioData blockData = dataManager.getBlock(radioLocation);
            if (blockData == null) return;

            blockData.setState("output");
            displayEntityManager.setStateSkin(blockData.getTextures(), blockData.getState());
        });
    }
}