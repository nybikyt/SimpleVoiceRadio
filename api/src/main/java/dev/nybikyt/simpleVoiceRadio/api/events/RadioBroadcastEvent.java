package dev.nybikyt.simpleVoiceRadio.api.events;

import dev.nybikyt.simpleVoiceRadio.api.AudioType;
import dev.nybikyt.simpleVoiceRadio.api.RadioBlock;

/**
 * Called when audio from play_audio command or api is played on radio, for every radio which plays it
 */
public class RadioBroadcastEvent extends RadioAudioEvent {

    public RadioBroadcastEvent(RadioBlock radio, byte[] opusData) {
        super(radio, opusData);
    }

    @Override
    public AudioType getType() {
        return AudioType.BROADCAST;
    }
}
