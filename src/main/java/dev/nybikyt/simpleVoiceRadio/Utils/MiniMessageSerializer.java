package dev.nybikyt.simpleVoiceRadio.Utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class MiniMessageSerializer {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();
    private static final java.util.regex.Pattern LEGACY_CODE = java.util.regex.Pattern.compile("&[0-9a-fk-orA-FK-OR#x]");

    public static Component parse(String text) {
        if (LEGACY_CODE.matcher(text).find()) {
            return LEGACY.deserialize(text);
        }
        return MINI.deserialize(text);
    }
}