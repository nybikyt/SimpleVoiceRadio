package dev.nybikyt.simpleVoiceRadio;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import dev.nybikyt.simpleVoiceRadio.Audio.AudioRouter;
import dev.nybikyt.simpleVoiceRadio.Audio.ChannelManager;
import dev.nybikyt.simpleVoiceRadio.Bridges.CustomDiscs;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.RadioState;
import dev.nybikyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class SimpleVoiceAddon implements VoicechatPlugin {

    private static final String RADIO_CATEGORY = "sv_radio";

    private static volatile VoicechatServerApi api = null;

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

    public static VoicechatServerApi getApi() {
        return api;
    }

    public static String getRadioCategory() {
        return RADIO_CATEGORY;
    }

    public CustomDiscs getCustomDiscs() {
        return customDiscs;
    }

    public AudioRouter getAudioRouter() {
        return audioRouter;
    }

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

        channelManager = new ChannelManager(config);
        audioRouter = new AudioRouter(plugin, config, dataManager, displayEntityManager, channelManager);
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
                customDiscs = new CustomDiscs(plugin, config, dataManager, displayEntityManager, audioRouter);
                plugin.getServer().getPluginManager().registerEvents(customDiscs, plugin);
                SimpleVoiceRadio.LOGGER.info("CustomDiscs integration enabled! Additional features included");
            } catch (Exception e) {
                SimpleVoiceRadio.LOGGER.error("CustomDiscs found but there is an error", e);
            }
        }
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

    public void shutdown() {
        if (audioRouter != null) audioRouter.shutdown();
    }
}
