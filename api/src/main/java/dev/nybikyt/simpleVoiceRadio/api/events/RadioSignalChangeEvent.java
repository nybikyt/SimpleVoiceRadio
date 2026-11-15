package dev.nybikyt.simpleVoiceRadio.api.events;

import dev.nybikyt.simpleVoiceRadio.api.RadioBlock;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when radio is going to change redstone signal which comparator reads from it.
 * It happens only with signal_output_system in config, radio gives signal while it plays something
 * <p>
 * If you cancel it radio keeps old signal. But when radio stops being output (player switched it
 * or lightning destroyed it) signal is always removed and cancel is ignored.
 * Its called in thread of radio, so you can change radio right in listener
 */
public class RadioSignalChangeEvent extends RadioEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int oldLevel;
    private final int newLevel;
    private boolean cancelled;

    public RadioSignalChangeEvent(RadioBlock radio, int oldLevel, int newLevel) {
        super(radio);
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    /** From 0 to 15, 0 means no signal */
    public int getOldLevel() {
        return oldLevel;
    }

    /** From 0 to 15, 0 means radio became silent */
    public int getNewLevel() {
        return newLevel;
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
