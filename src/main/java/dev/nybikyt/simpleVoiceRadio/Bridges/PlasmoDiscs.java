package dev.nybikyt.simpleVoiceRadio.Bridges;

import dev.nybikyt.simpleVoiceRadio.Audio.AudioRouter;
import dev.nybikyt.simpleVoiceRadio.Audio.RadioAudioEffect;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.Radio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.RadioState;
import dev.nybikyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.Voice.VoiceBackend;
import org.bukkit.Location;
import org.bukkit.World;
import su.plo.slib.api.server.position.ServerPos3d;
import su.plo.voice.api.encryption.Encryption;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.audio.source.ServerAudioSource;
import su.plo.voice.api.server.audio.source.ServerProximitySource;
import su.plo.voice.api.server.event.audio.source.ServerSourceAudioPacketEvent;
import su.plo.voice.api.server.event.audio.source.ServerSourceRemovedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlasmoDiscs {

    private static final String DISCS_ADDON_ID = "pv-addon-discs";

    private final PluginConfig config;
    private final DataManager dataManager;
    private final DisplayEntityManager displayEntityManager;
    private final AudioRouter audioRouter;
    private final VoiceBackend backend;
    private final Encryption encryption;

    private record DiscProcessor(VoiceBackend.Decoder decoder, VoiceBackend.Encoder encoder, RadioAudioEffect effect, UUID streamId) {
    }

    private final Map<UUID, DiscProcessor> discProcessors = new ConcurrentHashMap<>();

    public PlasmoDiscs(PluginConfig config, DataManager dataManager, DisplayEntityManager displayEntityManager, AudioRouter audioRouter, VoiceBackend backend, Encryption encryption) {
        this.config = config;
        this.dataManager = dataManager;
        this.displayEntityManager = displayEntityManager;
        this.audioRouter = audioRouter;
        this.backend = backend;
        this.encryption = encryption;
    }

    @EventSubscribe
    public void onSourceAudioPacket(ServerSourceAudioPacketEvent event) {
        try {
            ServerAudioSource<?> source = event.getSource();
            if (!DISCS_ADDON_ID.equals(source.getAddon().getId())) return;
            if (!(source instanceof ServerProximitySource<?> proximitySource)) return;

            Location radioLocation = radioLocationAbove(proximitySource);
            if (radioLocation == null) return;

            Radio radio = dataManager.get(radioLocation);
            if (radio == null || radio.getState() == RadioState.DESTROYED) return;

            if (radio.getState() != RadioState.LISTEN) {
                dataManager.updateState(radioLocation, RadioState.LISTEN);
                displayEntityManager.scheduleStateSkin(radioLocation, radio);
            }

            DiscProcessor processor = discProcessors.computeIfAbsent(source.getId(), k ->
                    new DiscProcessor(backend.createDecoder(), backend.createEncoder(), new RadioAudioEffect(config), UUID.randomUUID()));

            byte[] audioData = encryption.decrypt(event.getPacket().getData());
            audioRouter.handleDiscPacket(radioLocation, audioData, processor.effect(), processor.encoder(), processor.decoder(), processor.streamId());
        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Error processing disc audio packet", e);
        }
    }

    @EventSubscribe
    public void onSourceRemoved(ServerSourceRemovedEvent event) {
        ServerAudioSource<?> source = event.getSource();
        if (!DISCS_ADDON_ID.equals(source.getAddon().getId())) return;

        DiscProcessor processor = discProcessors.remove(source.getId());
        if (processor != null) {
            processor.decoder().close();
            processor.encoder().close();
        }

        if (!(source instanceof ServerProximitySource<?> proximitySource)) return;
        Location radioLocation = radioLocationAbove(proximitySource);
        if (radioLocation == null) return;

        Radio radio = dataManager.get(radioLocation);
        if (radio == null || radio.getState() != RadioState.LISTEN) return;

        dataManager.updateState(radioLocation, RadioState.OUTPUT);
        displayEntityManager.scheduleStateSkin(radioLocation, radio);
    }

    private static Location radioLocationAbove(ServerProximitySource<?> source) {
        ServerPos3d position = source.getPosition();
        World world = position.getWorld().getInstance();
        if (world == null) return null;
        return new Location(world, Math.floor(position.getX()), Math.floor(position.getY()), Math.floor(position.getZ()));
    }
}
