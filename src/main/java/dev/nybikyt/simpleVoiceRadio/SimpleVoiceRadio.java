package dev.nybikyt.simpleVoiceRadio;

import com.github.retrooper.packetevents.PacketEvents;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import dev.nybikyt.simpleVoiceRadio.Handlers.CommandHandler;
import dev.nybikyt.simpleVoiceRadio.Handlers.EventHandler;
import dev.nybikyt.simpleVoiceRadio.Handlers.PacketHandler;
import dev.nybikyt.simpleVoiceRadio.Misc.Item;
import dev.nybikyt.simpleVoiceRadio.Misc.Metrics;
import dev.nybikyt.simpleVoiceRadio.Misc.WindChargeFix.DefaultEntityExplodeListener;
import dev.nybikyt.simpleVoiceRadio.Misc.WindChargeFix.WindChargeEntityExplodeListener;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.Utils.SkinManager;
import dev.nybikyt.simpleVoiceRadio.Audio.AudioStreamer;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Objects;

public final class SimpleVoiceRadio extends JavaPlugin {

    public static final Logger LOGGER = LogManager.getLogger(SimpleVoiceRadio.class.getSimpleName());

    private PluginConfig pluginConfig;
    private DataManager dataManager;
    private SkinManager skinManager;
    private DisplayEntityManager displayEntityManager;
    private Item item;

    @Nullable
    private SimpleVoiceAddon voiceAddon;

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean isPaper() {
        return classExists("com.destroystokyo.paper.PaperConfig")
                || classExists("io.papermc.paper.configuration.Configuration");
    }

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
        if (!isPaper()) {
            Bukkit.getConsoleSender().sendMessage("Simple Voice Radio requires Paper server!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        PacketEvents.getAPI().init();

        saveDefaultConfig();
        new File(getDataFolder(), "audio").mkdirs();

        new Metrics(this, 28921);
        LOGGER.info("bStats metrics initialized");

        pluginConfig = new PluginConfig(this);
        skinManager = new SkinManager(this);
        displayEntityManager = new DisplayEntityManager(this, pluginConfig, skinManager);
        item = new Item(this, displayEntityManager, skinManager);
        dataManager = new DataManager(this);

        skinManager.setup();
        item.registerCraft();

        dataManager.load();
        dataManager.startAutoSave();

        PacketHandler packetHandler = new PacketHandler(dataManager);
        packetHandler.registerPacketListener();

        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            voiceAddon = new SimpleVoiceAddon(this, pluginConfig, dataManager, displayEntityManager);
            service.registerPlugin(voiceAddon);
        } else {
            LOGGER.error("Error while loading addon! Bye :(");
        }

        EventHandler eventHandler = new EventHandler(this, pluginConfig, dataManager, displayEntityManager, voiceAddon, item);

        if (classExists("org.bukkit.ExplosionResult")) {
            getServer().getPluginManager().registerEvents(new WindChargeEntityExplodeListener(dataManager, eventHandler), this);
        } else {
            getServer().getPluginManager().registerEvents(new DefaultEntityExplodeListener(dataManager, eventHandler), this);
        }

        getServer().getPluginManager().registerEvents(eventHandler, this);

        CommandHandler commandHandler = new CommandHandler(this, pluginConfig, item, skinManager, voiceAddon);
        PluginCommand command = Objects.requireNonNull(getCommand("simple_voice_radio"));
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
    }

    @Override
    public void onDisable() {
        AudioStreamer.stopStreaming();
        if (voiceAddon != null) {
            getServer().getServicesManager().unregister(voiceAddon);
            if (voiceAddon.getCustomDiscs() != null) voiceAddon.getCustomDiscs().unregisterPacketHandler();
            voiceAddon.shutdown();
        }
        if (dataManager != null) dataManager.shutdown();
        PacketEvents.getAPI().terminate();
    }
}
