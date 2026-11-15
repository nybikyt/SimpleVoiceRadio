package dev.nybikyt.simpleVoiceRadio.api.events;

import dev.nybikyt.simpleVoiceRadio.api.AudioType;
import dev.nybikyt.simpleVoiceRadio.api.RadioBlock;
import org.bukkit.entity.Player;

/**
 * Called when voice of player goes through input radio
 * <p>
 * Its called from voice thread, so with player do only simple things like getUniqueId or getName.
 * For anything else use {@code player.getScheduler()}
 */
public class RadioVoiceEvent extends RadioAudioEvent {

    private final Player player;

    public RadioVoiceEvent(RadioBlock radio, Player player, byte[] opusData) {
        super(radio, opusData);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public AudioType getType() {
        return AudioType.VOICE;
    }
}
