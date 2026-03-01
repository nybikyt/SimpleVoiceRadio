package dev.nybikyt.simpleVoiceRadio;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import dev.nybikyt.simpleVoiceRadio.Handlers.CommandHandler;
import dev.nybikyt.simpleVoiceRadio.Handlers.EventHandler;
import dev.nybikyt.simpleVoiceRadio.Handlers.PacketHandler;
import dev.nybikyt.simpleVoiceRadio.Misc.Item;
import dev.nybikyt.simpleVoiceRadio.Misc.Metrics;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import dev.nybikyt.simpleVoiceRadio.Utils.JukeboxManager;
import dev.nybikyt.simpleVoiceRadio.Utils.SkinManager;
import javax.annotation.Nullable;
import java.io.File;

public final class SimpleVoiceRadio extends JavaPlugin {

    public static final Logger LOGGER = LogManager.getLogger(SimpleVoiceRadio.class.getSimpleName());
    private final DataManager dataManager = new DataManager(this);
    private final SkinManager skinManager = new SkinManager(this);
    private final DisplayEntityManager displayEntityManager = new DisplayEntityManager(this, skinManager);
    private final JukeboxManager jukeboxManager = new JukeboxManager(this);
    private final Item item = new Item(this, displayEntityManager, skinManager);

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
        new File(getDataFolder(), "audio").mkdirs();

        new Metrics(this, 28921);
        LOGGER.info("bStats metrics initialized");

        skinManager.setup();
        item.registerCraft();

        dataManager.load();
        dataManager.startAutoSave();

        PacketHandler packetHandler = new PacketHandler(this);
        packetHandler.registerPacketListener();


        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            voiceAddon = new VoiceAddon(dataManager, this, jukeboxManager, displayEntityManager);
            service.registerPlugin(voiceAddon);
        } else {
            LOGGER.error("Error while loading addon! Bye :(");
        }

        EventHandler eventHandler = new EventHandler(this, dataManager, displayEntityManager, voiceAddon, skinManager, jukeboxManager, item);
        getServer().getPluginManager().registerEvents(eventHandler, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    new CommandHandler(this, item, skinManager, voiceAddon).createCommand(),
                    "Simple Voice Radio plugin commands"
            );
        });
    }

    @Override
    public void onDisable() {
        if (voiceAddon != null) {
            getServer().getServicesManager().unregister(voiceAddon);
            if (voiceAddon.getCustomDiscs() != null) voiceAddon.getCustomDiscs().unregisterPacketHandler();
        }
        dataManager.shutdown();
    }
}