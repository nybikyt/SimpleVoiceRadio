package dev.nybikyt.simpleVoiceRadio.Utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class MiniMessageSerializer {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public static Component parse(String text) {

        if (text.contains("&")) {
            return LEGACY.deserialize(text);
        }
        return MINI.deserialize(text);
    }
}