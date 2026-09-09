package dev.nybikyt.simpleVoiceRadio.Voice;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceAddon;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;

import javax.annotation.Nullable;

public final class SimpleVoiceLoader {

    private SimpleVoiceLoader() {
    }

    @Nullable
    public static VoiceAddon load(SimpleVoiceRadio plugin, PluginConfig config, DataManager dataManager, DisplayEntityManager displayEntityManager) {
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) return null;

        SimpleVoiceAddon addon = new SimpleVoiceAddon(plugin, config, dataManager, displayEntityManager);
        service.registerPlugin(addon);
        return addon;
    }
}
