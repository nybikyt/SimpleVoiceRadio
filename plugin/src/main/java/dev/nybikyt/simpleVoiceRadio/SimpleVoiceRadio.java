package dev.nybikyt.simpleVoiceRadio;

import com.github.retrooper.packetevents.PacketEvents;
import dev.nybikyt.simpleVoiceRadio.Handlers.ApiHandler;
import dev.nybikyt.simpleVoiceRadio.Handlers.CommandHandler;
import dev.nybikyt.simpleVoiceRadio.Handlers.EventHandler;
import dev.nybikyt.simpleVoiceRadio.Handlers.PacketHandler;
import dev.nybikyt.simpleVoiceRadio.Misc.Item;
import dev.nybikyt.simpleVoiceRadio.Misc.Metrics;
import dev.nybikyt.simpleVoiceRadio.Misc.WindChargeFix.DefaultEntityExplodeListener;
import dev.nybikyt.simpleVoiceRadio.Misc.WindChargeFix.WindChargeEntityExplodeListener;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import dev.nybikyt.simpleVoiceRadio.Utils.Messages;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.Utils.SkinManager;
import dev.nybikyt.simpleVoiceRadio.Audio.AudioRouter;
import dev.nybikyt.simpleVoiceRadio.Audio.AudioStreamer;
import dev.nybikyt.simpleVoiceRadio.Voice.PlasmoVoiceLoader;
import dev.nybikyt.simpleVoiceRadio.Voice.SimpleVoiceLoader;
import dev.nybikyt.simpleVoiceRadio.Voice.VoiceAddon;
import dev.nybikyt.simpleVoiceRadio.api.RadioApi;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

public final class SimpleVoiceRadio extends JavaPlugin {

    public static final Logger LOGGER = LogManager.getLogger(SimpleVoiceRadio.class.getSimpleName());

    private PluginConfig pluginConfig;
    private DataManager dataManager;
    private SkinManager skinManager;
    private DisplayEntityManager displayEntityManager;
    private Item item;
    private PacketHandler packetHandler;
    private EventHandler eventHandler;

    @Nullable
    private VoiceAddon voiceAddon;

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

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public EventHandler getEventHandler() {
        return eventHandler;
    }

    @Nullable
    public AudioRouter getAudioRouter() {
        return voiceAddon == null ? null : voiceAddon.getAudioRouter();
    }

    @Override
    public void onLoad() {
        if (getServer().getPluginManager().getPlugin("voicechat") == null
                && getServer().getPluginManager().getPlugin("PlasmoVoice") != null) {
            voiceAddon = PlasmoVoiceLoader.load(this);
        }
    }

    @Override
    public void onEnable() {
        if (!isPaper()) {
            Bukkit.getConsoleSender().sendMessage("Simple Voice Radio requires Paper server!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

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

        packetHandler = new PacketHandler(dataManager);
        packetHandler.registerPacketListener();

        if (getServer().getPluginManager().getPlugin("voicechat") != null) {
            voiceAddon = SimpleVoiceLoader.load(this, pluginConfig, dataManager, displayEntityManager);
            if (voiceAddon != null) {
                LOGGER.info("Simple Voice Chat detected, using it as voice backend");
            } else {
                LOGGER.error("Error while loading addon! Bye :(");
            }
        } else if (voiceAddon != null) {
            PlasmoVoiceLoader.enable(voiceAddon, pluginConfig, dataManager, displayEntityManager);
            LOGGER.info("Plasmo Voice detected, using it as voice backend");
        } else {
            LOGGER.error("No supported voice chat plugin found! Install Simple Voice Chat or Plasmo Voice.");
        }

        eventHandler = new EventHandler(this, pluginConfig, dataManager, displayEntityManager, voiceAddon, item);

        if (classExists("org.bukkit.ExplosionResult")) {
            getServer().getPluginManager().registerEvents(new WindChargeEntityExplodeListener(dataManager, eventHandler), this);
        } else {
            getServer().getPluginManager().registerEvents(new DefaultEntityExplodeListener(dataManager, eventHandler), this);
        }

        getServer().getPluginManager().registerEvents(eventHandler, this);

        ApiHandler apiHandler = new ApiHandler(pluginConfig, dataManager, eventHandler, item, voiceAddon);
        getServer().getServicesManager().register(RadioApi.class, apiHandler, this, ServicePriority.Normal);

        CommandHandler commandHandler = new CommandHandler(this, pluginConfig, new Messages(this), apiHandler, item, skinManager, voiceAddon);
        PluginCommand command = Objects.requireNonNull(getCommand("simple_voice_radio"));
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
    }

    @Override
    public void onDisable() {
        AudioStreamer.stopStreaming();
        try {
            if (voiceAddon != null) {
                getServer().getServicesManager().unregister(voiceAddon);
                if (voiceAddon.getCustomDiscs() != null) voiceAddon.getCustomDiscs().unregisterPacketHandler();
                voiceAddon.shutdown();
            }
        } finally {
            if (dataManager != null) dataManager.shutdown();
            if (packetHandler != null) PacketEvents.getAPI().getEventManager().unregisterListener(packetHandler);
        }
    }
}
