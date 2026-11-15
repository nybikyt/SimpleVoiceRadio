package dev.nybikyt.simpleVoiceRadio.api.events;

import dev.nybikyt.simpleVoiceRadio.api.AudioType;
import dev.nybikyt.simpleVoiceRadio.api.RadioBlock;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called for every piece of sound which goes through radio, like voice of player, music from disc
 * or audio from play_audio. Its called very often (every 20 ms of sound) and not from main thread,
 * so keep listener fast and use thread safe collections there
 * <p>
 * Listen to it if you need all sounds, or to {@link RadioVoiceEvent}, {@link RadioDiscEvent}
 * and {@link RadioBroadcastEvent} if you need only one kind. If you cancel it this piece of sound is skipped.
 * For example you can mute some players on radios like this (with @EventHandler on method as usual)
 * <pre>{@code
 * Set<UUID> muted = ConcurrentHashMap.newKeySet();
 *
 * public void onVoice(RadioVoiceEvent event) {
 *     if (muted.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
 * }
 * }</pre>
 */
public abstract class RadioAudioEvent extends RadioEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final byte[] opusData;
    private boolean cancelled;

    protected RadioAudioEvent(RadioBlock radio, byte[] opusData) {
        super(radio);
        this.opusData = opusData;
    }

    public abstract AudioType getType();

    /** Opus frame with this piece of sound (20 ms) */
    public byte[] getOpusData() {
        return opusData.clone();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
