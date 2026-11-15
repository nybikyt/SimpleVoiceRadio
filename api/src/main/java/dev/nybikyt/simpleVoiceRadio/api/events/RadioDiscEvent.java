package dev.nybikyt.simpleVoiceRadio.api.events;

import dev.nybikyt.simpleVoiceRadio.api.AudioType;
import dev.nybikyt.simpleVoiceRadio.api.RadioBlock;

/**
 * Called when music from disc in jukebox below goes through radio
 */
public class RadioDiscEvent extends RadioAudioEvent {

    public RadioDiscEvent(RadioBlock radio, byte[] opusData) {
        super(radio, opusData);
    }

    @Override
    public AudioType getType() {
        return AudioType.DISC;
    }
}
