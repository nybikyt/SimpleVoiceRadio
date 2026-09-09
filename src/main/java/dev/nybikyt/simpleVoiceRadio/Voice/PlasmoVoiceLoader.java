package dev.nybikyt.simpleVoiceRadio.Voice;

import dev.nybikyt.simpleVoiceRadio.PlasmoVoiceAddon;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import su.plo.voice.api.server.PlasmoVoiceServer;

public final class PlasmoVoiceLoader {

    private PlasmoVoiceLoader() {
    }

    public static VoiceAddon load(SimpleVoiceRadio plugin) {
        PlasmoVoiceAddon addon = new PlasmoVoiceAddon(plugin);
        PlasmoVoiceServer.getAddonsLoader().load(addon);
        return addon;
    }

    public static void enable(VoiceAddon addon, PluginConfig config, DataManager dataManager, DisplayEntityManager displayEntityManager) {
        ((PlasmoVoiceAddon) addon).enable(config, dataManager, displayEntityManager);
    }
}
