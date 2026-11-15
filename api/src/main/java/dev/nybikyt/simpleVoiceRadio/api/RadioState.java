package dev.nybikyt.simpleVoiceRadio.api;

public enum RadioState {

    /** Hears players nearby and sends their voice to its frequency */
    INPUT,

    /** Plays everything what comes to its frequency */
    OUTPUT,

    /** Plays audio from play_audio command or api, plugin sets this state itself */
    BROADCAST,

    /** Sends music from jukebox below it, plugin sets this state itself */
    LISTEN,

    /** Was hit by lightning and dont work anymore */
    DESTROYED
}
