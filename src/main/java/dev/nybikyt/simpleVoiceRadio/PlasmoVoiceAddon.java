package dev.nybikyt.simpleVoiceRadio;

import dev.nybikyt.simpleVoiceRadio.Audio.AudioRouter;
import dev.nybikyt.simpleVoiceRadio.Audio.ChannelManager;
import dev.nybikyt.simpleVoiceRadio.Bridges.CustomDiscs;
import dev.nybikyt.simpleVoiceRadio.Bridges.PlasmoDiscs;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.RadioState;
import dev.nybikyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.Voice.VoiceAddon;
import dev.nybikyt.simpleVoiceRadio.Voice.VoiceBackend;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import su.plo.slib.api.server.entity.player.McServerPlayer;
import su.plo.slib.api.server.position.ServerPos3d;
import su.plo.slib.api.server.world.McServerWorld;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.audio.codec.AudioDecoder;
import su.plo.voice.api.audio.codec.AudioEncoder;
import su.plo.voice.api.audio.codec.CodecException;
import su.plo.voice.api.encryption.Encryption;
import su.plo.voice.api.encryption.EncryptionException;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerStaticSource;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.player.VoiceServerPlayer;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

@Addon(
        id = "simple-voice-radio",
        scope = AddonLoaderScope.SERVER,
        version = "0.0.8",
        authors = {"Nybik_YT"}
)
public class PlasmoVoiceAddon implements AddonInitializer, VoiceAddon, VoiceBackend {

    private static final String SOURCE_LINE_NAME = "sv_radio";
    private static final int SOURCE_LINE_WEIGHT = 10;
    private static final String FALLBACK_ICON = "plasmovoice:textures/icons/speaker.png";

    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;

    private final SimpleVoiceRadio plugin;

    private volatile PluginConfig config;
    private volatile ServerSourceLine sourceLine;
    private volatile ChannelManager channelManager;
    private volatile AudioRouter audioRouter;

    public PlasmoVoiceAddon(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onAddonInitialize() {
        registerSourceLine();
    }

    @Override
    public void onAddonShutdown() {
    }

    public void enable(PluginConfig config, DataManager dataManager, DisplayEntityManager displayEntityManager) {
        if (voiceServer == null || sourceLine == null) {
            SimpleVoiceRadio.LOGGER.error("Plasmo Voice addon was not initialized! Bye :(");
            return;
        }

        this.config = config;
        channelManager = new ChannelManager(this);
        audioRouter = new AudioRouter(plugin, config, dataManager, displayEntityManager, channelManager, this);
        audioRouter.resetRadios(RadioState.BROADCAST);
        audioRouter.resetRadios(RadioState.LISTEN);

        if (config.customDiscsIntegration()) {
            if (voiceServer.getAddonManager().isLoaded("pv-addon-discs")) {
                try {
                    PlasmoDiscs plasmoDiscs = new PlasmoDiscs(config, dataManager, displayEntityManager, audioRouter, this, voiceServer.getDefaultEncryption());
                    voiceServer.getEventBus().register(this, plasmoDiscs);
                    SimpleVoiceRadio.LOGGER.info("pv-addon-discs integration enabled! Additional features included");
                } catch (Exception e) {
                    SimpleVoiceRadio.LOGGER.error("pv-addon-discs found but there is an error", e);
                }
            }
            if (plugin.getServer().getPluginManager().getPlugin("CustomDiscs") != null) {
                SimpleVoiceRadio.LOGGER.warn("CustomDiscs integration requires Simple Voice Chat and is not available with Plasmo Voice");
            }
        }
    }

    private void registerSourceLine() {
        try (InputStream icon = SimpleVoiceRadio.class.getClassLoader().getResourceAsStream("assets/logo.png")) {
            if (icon != null) {
                sourceLine = voiceServer.getSourceLineManager()
                        .createBuilder(this, SOURCE_LINE_NAME, "Radio", icon, SOURCE_LINE_WEIGHT)
                        .build();
            } else {
                sourceLine = voiceServer.getSourceLineManager()
                        .createBuilder(this, SOURCE_LINE_NAME, "Radio", FALLBACK_ICON, SOURCE_LINE_WEIGHT)
                        .build();
            }
        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Failed to register Plasmo Voice source line", e);
        }
    }

    @EventSubscribe(ignoreCancelled = false)
    public void onPlayerSpeak(PlayerSpeakEvent event) {
        try {
            AudioRouter router = audioRouter;
            if (router == null) return;
            if (!(event.getPlayer() instanceof VoiceServerPlayer voicePlayer)) return;

            Player player = voicePlayer.getInstance().getInstance();
            if (!player.hasPermission("simple_voice_radio.can_broadcast")) return;

            byte[] audioData = voiceServer.getDefaultEncryption().decrypt(event.getPacket().getData());
            router.handleMicPacket(player.getUniqueId(), player.getLocation(), audioData);
        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Error processing microphone packet", e);
        }
    }

    @Override
    public VoiceBackend.Encoder createEncoder() {
        AudioEncoder encoder = voiceServer.createOpusEncoder(false);
        return new VoiceBackend.Encoder() {
            @Override
            public byte[] encode(short[] samples) {
                try {
                    return encoder.encode(samples);
                } catch (CodecException e) {
                    throw new RuntimeException("Failed to encode audio frame", e);
                }
            }

            @Override
            public void close() {
                encoder.close();
            }
        };
    }

    @Override
    public VoiceBackend.Decoder createDecoder() {
        AudioDecoder decoder = voiceServer.createOpusDecoder(false);
        return new VoiceBackend.Decoder() {
            @Override
            public short[] decode(byte[] data) {
                try {
                    return decoder.decode(data);
                } catch (CodecException e) {
                    throw new RuntimeException("Failed to decode audio frame", e);
                }
            }

            @Override
            public void close() {
                decoder.close();
            }
        };
    }

    @Override
    public VoiceBackend.Channel createChannel(Location location) {
        ServerSourceLine line = sourceLine;
        if (line == null || location.getWorld() == null) return null;

        McServerWorld world = voiceServer.getMinecraftServer().getWorld(location.getWorld());
        ServerPos3d position = new ServerPos3d(
                world,
                location.getBlockX() + 0.5,
                location.getBlockY() + 0.5,
                location.getBlockZ() + 0.5
        );

        ServerStaticSource source = line.createStaticSource(position, false);
        source.setName("Radio");

        short distance = (short) Math.ceil(config.outputRadius());
        Encryption encryption = voiceServer.getDefaultEncryption();
        AtomicLong sequenceNumber = new AtomicLong();

        return new VoiceBackend.Channel() {
            @Override
            public void send(byte[] opusData) {
                try {
                    source.sendAudioFrame(encryption.encrypt(opusData), sequenceNumber.incrementAndGet(), distance);
                } catch (EncryptionException e) {
                    SimpleVoiceRadio.LOGGER.error("Failed to encrypt audio frame: {}", e.getMessage());
                }
            }

            @Override
            public void flush() {
                source.sendAudioEnd(sequenceNumber.get(), distance);
                source.remove();
            }
        };
    }

    @Override
    public boolean hasPlayersInRange(Location location, double radius) {
        McServerWorld world = voiceServer.getMinecraftServer().getWorld(location.getWorld());
        double radiusSquared = radius * radius;
        double centerX = location.getBlockX() + 0.5;
        double centerY = location.getBlockY() + 0.5;
        double centerZ = location.getBlockZ() + 0.5;

        for (VoiceServerPlayer voicePlayer : voiceServer.getPlayerManager().getPlayers()) {
            McServerPlayer mcPlayer = voicePlayer.getInstance();
            if (!world.getName().equals(mcPlayer.getWorld().getName())) continue;

            ServerPos3d position = mcPlayer.getServerPosition();
            double dx = position.getX() - centerX;
            double dy = position.getY() - centerY;
            double dz = position.getZ() - centerZ;
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) return true;
        }
        return false;
    }

    @Override
    public AudioRouter getAudioRouter() {
        return audioRouter;
    }

    @Override
    public ChannelManager getChannelManager() {
        return channelManager;
    }

    @Override
    public CustomDiscs getCustomDiscs() {
        return null;
    }

    @Override
    public void shutdown() {
        if (audioRouter != null) audioRouter.shutdown();
        PlasmoVoiceServer.getAddonsLoader().unload(this);
    }
}
