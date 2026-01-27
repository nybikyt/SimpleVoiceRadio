package org.nyt.simpleVoiceRadio;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.nyt.simpleVoiceRadio.Handlers.CommandHandler;
import org.nyt.simpleVoiceRadio.Handlers.EventHandler;
import org.nyt.simpleVoiceRadio.Handlers.PacketHandler;
import org.nyt.simpleVoiceRadio.Misc.Item;
import org.nyt.simpleVoiceRadio.Misc.Metrics;
import org.nyt.simpleVoiceRadio.Utils.DataManager;
import org.nyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import org.nyt.simpleVoiceRadio.Utils.JukeboxManager;
import javax.annotation.Nullable;

public final class SimpleVoiceRadio extends JavaPlugin {

    public static final Logger LOGGER = LogManager.getLogger(SimpleVoiceRadio.class.getSimpleName());
    private final DataManager dataManager = new DataManager(this);
    private final DisplayEntityManager displayEntityManager = new DisplayEntityManager(this);
    private final JukeboxManager jukeboxManager = new JukeboxManager(this);
    private final Item item = new Item(this, displayEntityManager);

    public ProtocolManager getProtocolManager() { return protocolManager; }
    private ProtocolManager protocolManager;

    @Nullable
    private VoiceAddon voiceAddon;

    @Override
    public void onLoad() {
        protocolManager = ProtocolLibrary.getProtocolManager();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        item.registerCraft();

        new Metrics(this, 28921);
        LOGGER.info("bStats metrics initialized");

        dataManager.load();
        dataManager.startAutoSave();

        PacketHandler packetHandler = new PacketHandler(this);
        packetHandler.registerActionBarListener();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    "simple_voice_radio",
                    "SimpleVoiceRadio command",
                    new CommandHandler(this, item)
            );
        });

        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            voiceAddon = new VoiceAddon(dataManager, this, jukeboxManager);
            service.registerPlugin(voiceAddon);
        } else {
            LOGGER.error("Error while loading addon! Bye :(");
        }

        getServer().getPluginManager().registerEvents(new EventHandler(this, dataManager, displayEntityManager, voiceAddon, item), this);
    }


    @Override
    public void onDisable() {
        if (voiceAddon != null) getServer().getServicesManager().unregister(voiceAddon);
        dataManager.shutdown();
    }
}