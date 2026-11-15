package dev.nybikyt.simpleVoiceRadio.Voice;

import dev.nybikyt.simpleVoiceRadio.Audio.AudioRouter;
import dev.nybikyt.simpleVoiceRadio.Audio.ChannelManager;
import dev.nybikyt.simpleVoiceRadio.Bridges.CustomDiscs;

public interface VoiceAddon {

    AudioRouter getAudioRouter();

    ChannelManager getChannelManager();

    CustomDiscs getCustomDiscs();

    void shutdown();
}
