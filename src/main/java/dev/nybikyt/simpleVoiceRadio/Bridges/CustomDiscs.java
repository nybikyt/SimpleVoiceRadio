package dev.nybikyt.simpleVoiceRadio.Bridges;

import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Misc.RadioAudioEffect;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import dev.nybikyt.simpleVoiceRadio.VoiceAddon;
import dev.nybikyt.simpleVoiceRadio.VoiceChat.Utils;
import space.subkek.customdiscs.api.CustomDiscsAPI;
import space.subkek.customdiscs.api.event.CustomDiscInsertEvent;
import space.subkek.customdiscs.api.event.LavaPlayerStopPlayingEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CustomDiscs implements Listener {
    private final CustomDiscsAPI api;
    private final SimpleVoiceRadio plugin;
    private final DataManager dataManager;
    private final DisplayEntityManager displayEntityManager;
    private final Utils utils;

    private record DiscProcessor(OpusDecoder decoder, OpusEncoder encoder, RadioAudioEffect effect) {}
    private final Map<Location, DiscProcessor> discProcessors = new ConcurrentHashMap<>();

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
            Location radioLocation = block.getLocation().add(0, 1, 0);
            DataManager.RadioData radioData = dataManager.getBlock(radioLocation);
            if (radioData != null) {
                if (!radioData.getState().equals("listen")) {
                    radioData.setState("listen");
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            displayEntityManager.setStateSkin(radioData.getTextures(), radioData.getState()));
                }
                DiscProcessor processor = discProcessors.computeIfAbsent(radioLocation, k ->
                        new DiscProcessor(VoiceAddon.getApi().createDecoder(), VoiceAddon.getApi().createEncoder(), new RadioAudioEffect(plugin)));
                utils.handleDiscPacket(radioLocation, data, processor.effect(), processor.encoder(), processor.decoder());
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

        DiscProcessor old = discProcessors.remove(radioLocation);
        if (old != null) {
            old.decoder().close();
            old.encoder().close();
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            DataManager.RadioData blockData = dataManager.getBlock(radioLocation);
            if (blockData == null) return;

            blockData.setState("output");
            displayEntityManager.setStateSkin(blockData.getTextures(), blockData.getState());
        });
    }
}