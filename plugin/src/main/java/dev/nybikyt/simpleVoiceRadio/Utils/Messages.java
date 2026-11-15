package dev.nybikyt.simpleVoiceRadio.Utils;

import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class Messages {

    private static final String FILE_NAME = "messages.yml";

    private final SimpleVoiceRadio plugin;
    private volatile YamlConfiguration messages;

    public Messages(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) plugin.saveResource(FILE_NAME, false);

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        InputStream defaults = plugin.getResource(FILE_NAME);
        if (defaults != null) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
        messages = loaded;
    }

    public Component get(String key, String... replacements) {
        Component message = MiniMessageSerializer.parse(Objects.requireNonNullElse(messages.getString(key), key));
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String placeholder = "{" + replacements[i] + "}";
            String value = replacements[i + 1];
            message = message.replaceText(config -> config.matchLiteral(placeholder).replacement(value));
        }
        return message;
    }
}
