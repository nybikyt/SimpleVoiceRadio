package org.nyt.simpleVoiceRadio;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.nyt.simpleVoiceRadio.Bridges.CustomDiscs;
import org.nyt.simpleVoiceRadio.Utils.DataManager;
import org.nyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import org.nyt.simpleVoiceRadio.Utils.JukeboxManager;
import org.nyt.simpleVoiceRadio.VoiceChat.CategoryRegistration;
import org.nyt.simpleVoiceRadio.VoiceChat.ChannelManager;
import org.nyt.simpleVoiceRadio.VoiceChat.Utils;

public class VoiceAddon implements VoicechatPlugin {
    private static VoicechatServerApi api = null;
    private final SimpleVoiceRadio plugin;
    private final DataManager dataManager;
    private final JukeboxManager jukeboxManager;
    private final DisplayEntityManager displayEntityManager;

    private ChannelManager channelManager;
    private CustomDiscs customDiscs;
    private Utils utils;

    public VoiceAddon(DataManager dataManager, SimpleVoiceRadio plugin, JukeboxManager jukeboxManager, DisplayEntityManager displayEntityManager) {
        this.dataManager = dataManager;
        this.plugin = plugin;
        this.jukeboxManager = jukeboxManager;
        this.displayEntityManager = displayEntityManager;
    }

    public CustomDiscs getCustomDiscs() {
        return customDiscs;
    }

    public Utils getUtils() {
        return utils;
    }

    public static VoicechatServerApi getApi() {
        return api;
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

        channelManager = new ChannelManager(plugin, dataManager);
        utils = new Utils(plugin, dataManager, jukeboxManager, displayEntityManager, channelManager);

    }

    @Override
    public void registerEvents(EventRegistration eventRegistration) {
        eventRegistration.registerEvent(MicrophonePacketEvent.class, MicrophonePacketEvent -> {
            try {
                VoicechatConnection connection = MicrophonePacketEvent.getSenderConnection();
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) connection.getPlayer().getPlayer();

                if (!player.hasPermission("simple_voice_radio.can_broadcast")) return;

                World world = (World) connection.getPlayer().getServerLevel().getServerLevel();
                Position position = connection.getPlayer().getPosition();

                Location location = new Location(world, position.getX(), position.getY(), position.getZ());

                utils.handlePacket(location, MicrophonePacketEvent.getPacket().getOpusEncodedData());

            } catch (Exception e) {
                SimpleVoiceRadio.LOGGER.error("Error processing microphone packet: " + e);
            }
        });

        eventRegistration.registerEvent(VoicechatServerStartedEvent.class, voicechatServerStartedEvent -> {
            channelManager.createOutputChannels();
            utils.resetBroadCastingRadios();
            new CategoryRegistration().registerVolumeCategory();

            if (plugin.getConfig().getBoolean("radio-block.custom_discs_integration", false)
                    && plugin.getServer().getPluginManager().getPlugin("CustomDiscs") != null) {

                try {
                    customDiscs = new CustomDiscs(plugin, dataManager, displayEntityManager, utils);
                    plugin.getServer().getPluginManager().registerEvents(customDiscs, plugin);

                    SimpleVoiceRadio.LOGGER.info("CustomDiscs integration enabled! Additional features included");

                } catch (Exception e) {
                    SimpleVoiceRadio.LOGGER.error("CustomDiscs found but there is an error: " + e);
                }
            }
        });
    }
}