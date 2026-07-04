package dev.nybikyt.simpleVoiceRadio.Bridges;

import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.Radio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.RadioState;
import dev.nybikyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceAddon;
import dev.nybikyt.simpleVoiceRadio.Audio.AudioRouter;
import dev.nybikyt.simpleVoiceRadio.Audio.RadioAudioEffect;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import space.subkek.customdiscs.api.CustomDiscsAPI;
import space.subkek.customdiscs.api.event.CustomDiscInsertEvent;
import space.subkek.customdiscs.api.event.LavaPlayerStopPlayingEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomDiscs implements Listener {

    private final CustomDiscsAPI api;
    private final SimpleVoiceRadio plugin;
    private final PluginConfig config;
    private final DataManager dataManager;
    private final DisplayEntityManager displayEntityManager;
    private final AudioRouter audioRouter;

    private record DiscProcessor(OpusDecoder decoder, OpusEncoder encoder, RadioAudioEffect effect, UUID streamId) {
    }

    private final Map<Location, DiscProcessor> discProcessors = new ConcurrentHashMap<>();

    public CustomDiscs(SimpleVoiceRadio plugin, PluginConfig config, DataManager dataManager, DisplayEntityManager displayEntityManager, AudioRouter audioRouter) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;
        this.displayEntityManager = displayEntityManager;
        this.audioRouter = audioRouter;
        this.api = CustomDiscsAPI.get();

        registerPacketHandler();
    }

    private void registerPacketHandler() {
        api.getLavaPlayerManager().registerPacketHandler(plugin, (handler, block, data) -> {
            Location radioLocation = block.getLocation().add(0, 1, 0);
            Radio radio = dataManager.get(radioLocation);
            if (radio != null) {
                if (radio.getState() == RadioState.DESTROYED) return true;
                if (radio.getState() != RadioState.LISTEN) {
                    dataManager.updateState(radioLocation, RadioState.LISTEN);
                    displayEntityManager.scheduleStateSkin(radioLocation, radio);
                }
                DiscProcessor processor = discProcessors.computeIfAbsent(radioLocation, k ->
                        new DiscProcessor(SimpleVoiceAddon.getApi().createDecoder(), SimpleVoiceAddon.getApi().createEncoder(), new RadioAudioEffect(config), UUID.randomUUID()));
                audioRouter.handleDiscPacket(radioLocation, data, processor.effect(), processor.encoder(), processor.decoder(), processor.streamId());
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
        if (dataManager.get(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDiscStop(LavaPlayerStopPlayingEvent event) {
        Location radioLocation = event.getBlock().getLocation().clone().add(0, 1, 0);

        DiscProcessor old = discProcessors.remove(radioLocation);
        if (old != null) {
            old.decoder().close();
            old.encoder().close();
        }

        Radio radio = dataManager.get(radioLocation);
        if (radio == null || radio.getState() != RadioState.LISTEN) return;

        dataManager.updateState(radioLocation, RadioState.OUTPUT);
        displayEntityManager.scheduleStateSkin(radioLocation, radio);
    }
}
