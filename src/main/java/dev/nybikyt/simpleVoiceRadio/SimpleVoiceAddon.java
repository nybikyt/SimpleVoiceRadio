package dev.nybikyt.simpleVoiceRadio;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import dev.nybikyt.simpleVoiceRadio.Audio.AudioRouter;
import dev.nybikyt.simpleVoiceRadio.Audio.ChannelManager;
import dev.nybikyt.simpleVoiceRadio.Bridges.CustomDiscs;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.RadioState;
import dev.nybikyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.Voice.VoiceAddon;
import dev.nybikyt.simpleVoiceRadio.Voice.VoiceBackend;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.UUID;

public class SimpleVoiceAddon implements VoicechatPlugin, VoiceAddon, VoiceBackend {

    private static final String RADIO_CATEGORY = "sv_radio";

    private volatile VoicechatServerApi api = null;

    private final SimpleVoiceRadio plugin;
    private final PluginConfig config;
    private final DataManager dataManager;
    private final DisplayEntityManager displayEntityManager;

    private ChannelManager channelManager;
    private CustomDiscs customDiscs;
    private AudioRouter audioRouter;

    public SimpleVoiceAddon(SimpleVoiceRadio plugin, PluginConfig config, DataManager dataManager, DisplayEntityManager displayEntityManager) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;
        this.displayEntityManager = displayEntityManager;
    }

    @Override
    public CustomDiscs getCustomDiscs() {
        return customDiscs;
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
    public String getPluginId() {
        return SimpleVoiceRadio.class.getSimpleName();
    }

    @Override
    public void initialize(VoicechatApi voicechatApi) {
        api = (VoicechatServerApi) voicechatApi;

        channelManager = new ChannelManager(this);
        audioRouter = new AudioRouter(plugin, config, dataManager, displayEntityManager, channelManager, this);
    }

    @Override
    public void registerEvents(EventRegistration eventRegistration) {
        eventRegistration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
        eventRegistration.registerEvent(VoicechatServerStartedEvent.class, this::onVoicechatServerStarted);
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        try {
            VoicechatConnection connection = event.getSenderConnection();
            if (connection == null || audioRouter == null) return;

            Player player = (Player) connection.getPlayer().getPlayer();
            if (!player.hasPermission("simple_voice_radio.can_broadcast")) return;

            World world = (World) connection.getPlayer().getServerLevel().getServerLevel();
            Position position = connection.getPlayer().getPosition();
            Location location = new Location(world, position.getX(), position.getY(), position.getZ());

            audioRouter.handleMicPacket(player.getUniqueId(), location, event.getPacket().getOpusEncodedData());
        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Error processing microphone packet", e);
        }
    }

    private void onVoicechatServerStarted(VoicechatServerStartedEvent event) {
        audioRouter.resetRadios(RadioState.BROADCAST);
        audioRouter.resetRadios(RadioState.LISTEN);
        registerVolumeCategory();

        if (config.customDiscsIntegration() && plugin.getServer().getPluginManager().getPlugin("CustomDiscs") != null) {
            try {
                customDiscs = new CustomDiscs(plugin, config, dataManager, displayEntityManager, audioRouter, this);
                plugin.getServer().getPluginManager().registerEvents(customDiscs, plugin);
                SimpleVoiceRadio.LOGGER.info("CustomDiscs integration enabled! Additional features included");
            } catch (Exception e) {
                SimpleVoiceRadio.LOGGER.error("CustomDiscs found but there is an error", e);
            }
        }
    }

    @Override
    public VoiceBackend.Encoder createEncoder() {
        OpusEncoder encoder = api.createEncoder();
        return new VoiceBackend.Encoder() {
            @Override
            public byte[] encode(short[] samples) {
                return encoder.encode(samples);
            }

            @Override
            public void close() {
                encoder.close();
            }
        };
    }

    @Override
    public VoiceBackend.Decoder createDecoder() {
        OpusDecoder decoder = api.createDecoder();
        return new VoiceBackend.Decoder() {
            @Override
            public short[] decode(byte[] data) {
                return decoder.decode(data);
            }

            @Override
            public void close() {
                decoder.close();
            }
        };
    }

    @Override
    public VoiceBackend.Channel createChannel(Location location) {
        if (api == null || location.getWorld() == null) return null;

        LocationalAudioChannel channel = api.createLocationalAudioChannel(
                UUID.randomUUID(),
                api.fromServerLevel(location.getWorld()),
                api.createPosition(
                        location.getBlockX() + 0.5,
                        location.getBlockY() + 0.5,
                        location.getBlockZ() + 0.5
                )
        );
        if (channel == null) return null;
        channel.setDistance((float) config.outputRadius());
        channel.setCategory(RADIO_CATEGORY);

        return new VoiceBackend.Channel() {
            @Override
            public void send(byte[] opusData) {
                channel.send(opusData);
            }

            @Override
            public void flush() {
                channel.flush();
            }
        };
    }

    @Override
    public boolean hasPlayersInRange(Location location, double radius) {
        ServerLevel serverLevel = api.fromServerLevel(location.getWorld());
        Collection<ServerPlayer> nearbyPlayers = api.getPlayersInRange(
                serverLevel,
                api.createPosition(location.getBlockX() + 0.5, location.getBlockY() + 0.5, location.getBlockZ() + 0.5),
                (float) radius
        );
        return !nearbyPlayers.isEmpty();
    }

    private void registerVolumeCategory() {
        try {
            VolumeCategory radioCategory = api.volumeCategoryBuilder()
                    .setId(RADIO_CATEGORY)
                    .setName("Radio")
                    .setNameTranslationKey("simple_voice_radio.category.name")
                    .setDescription("The volume of all radio-blocks")
                    .setDescriptionTranslationKey("simple_voice_radio.category.description")
                    .setIcon(loadIcon("assets/logo.png"))
                    .build();
            api.registerVolumeCategory(radioCategory);
        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Failed to register volume category {}", e.getMessage());
        }
    }

    private static int[][] loadIcon(String path) {
        try (InputStream stream = SimpleVoiceRadio.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) return null;

            BufferedImage image = ImageIO.read(stream);
            if (image == null || image.getWidth() != 16 || image.getHeight() != 16) {
                return null;
            }

            int[][] pixels = new int[16][16];
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    pixels[x][y] = image.getRGB(x, y);
                }
            }
            return pixels;

        } catch (IOException e) {
            SimpleVoiceRadio.LOGGER.error("Failed to load icon: {}", path, e);
            return null;
        }
    }

    @Override
    public void shutdown() {
        if (audioRouter != null) audioRouter.shutdown();
    }
}
