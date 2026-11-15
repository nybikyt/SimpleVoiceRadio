package dev.nybikyt.simpleVoiceRadio.api;

/** Kind of sound which goes through radio */
public enum AudioType {

    /** Voice of player who speaks near input radio */
    VOICE,

    /** Music from disc in jukebox below radio */
    DISC,

    /** Audio from play_audio command or api */
    BROADCAST
}
