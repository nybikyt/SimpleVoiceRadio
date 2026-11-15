package dev.nybikyt.simpleVoiceRadio.api.events;

import dev.nybikyt.simpleVoiceRadio.api.RadioBlock;
import dev.nybikyt.simpleVoiceRadio.api.RadioState;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when state of radio is going to change, for example when player clicks on it or lightning hits it
 * <p>
 * If you cancel it radio keeps old state. But plugin itself switches radios to {@link RadioState#BROADCAST}
 * or {@link RadioState#LISTEN} and back when audio or disc starts and ends, usually from audio thread,
 * and such changes cant be cancelled
 */
public class RadioStateChangeEvent extends RadioEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final RadioState oldState;
    private final RadioState newState;
    private boolean cancelled;

    public RadioStateChangeEvent(RadioBlock radio, @Nullable Player player, RadioState oldState, RadioState newState) {
        super(radio);
        this.player = player;
        this.oldState = oldState;
        this.newState = newState;
    }

    /** Player who clicked on radio or null if state changes by other reason */
    @Nullable
    public Player getPlayer() {
        return player;
    }

    public RadioState getOldState() {
        return oldState;
    }

    public RadioState getNewState() {
        return newState;
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
