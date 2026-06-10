package dev.nybikyt.simpleVoiceRadio;

import com.github.retrooper.packetevents.PacketEvents;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import dev.nybikyt.simpleVoiceRadio.Misc.WindChargeFix.DefaultEntityExplodeListener;
import dev.nybikyt.simpleVoiceRadio.Misc.WindChargeFix.WindChargeEntityExplodeListener;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
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

    @Nullable
    private VoiceAddon voiceAddon;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .reEncodeByDefault(false)
                .checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        // Fuck Spigot.
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
        } catch (ClassNotFoundException e) {
            Bukkit.getConsoleSender().sendMessage("Simple Voice Radio requires Paper server!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        PacketEvents.getAPI().init();

        saveDefaultConfig();
        new File(getDataFolder(), "audio").mkdirs();

        new Metrics(this, 28921);
        LOGGER.info("bStats metrics initialized");

        skinManager.setup();
        item.registerCraft();

        dataManager.load();
        dataManager.startAutoSave();

        PacketHandler packetHandler = new PacketHandler(this, dataManager);
        packetHandler.registerPacketListener();

        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            voiceAddon = new VoiceAddon(dataManager, this, jukeboxManager, displayEntityManager);
            service.registerPlugin(voiceAddon);
        } else {
            LOGGER.error("Error while loading addon! Bye :(");
        }

        EventHandler eventHandler = new EventHandler(this, dataManager, displayEntityManager, voiceAddon, skinManager, jukeboxManager, item);

        try {
            Class.forName("org.bukkit.entity.WindCharge");
            getServer().getPluginManager().registerEvents(
                    new WindChargeEntityExplodeListener(dataManager, eventHandler),
                    this
            );

        } catch (ClassNotFoundException e) {
            getServer().getPluginManager().registerEvents(
                    new DefaultEntityExplodeListener(dataManager, eventHandler),
                    this
            );
        }

        getServer().getPluginManager().registerEvents(
                eventHandler,
                this
        );

        CommandHandler commandHandler = new CommandHandler(this, item, skinManager, voiceAddon);
        getCommand("simple_voice_radio").setExecutor(commandHandler);
        getCommand("simple_voice_radio").setTabCompleter(commandHandler);
    }

    @Override
    public void onDisable() {
        if (voiceAddon != null) {
            getServer().getServicesManager().unregister(voiceAddon);
            if (voiceAddon.getCustomDiscs() != null) voiceAddon.getCustomDiscs().unregisterPacketHandler();
        }
        dataManager.shutdown();
        PacketEvents.getAPI().terminate();
    }
}