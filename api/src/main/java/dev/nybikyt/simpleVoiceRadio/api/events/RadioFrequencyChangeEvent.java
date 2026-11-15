package dev.nybikyt.simpleVoiceRadio.api.events;

import dev.nybikyt.simpleVoiceRadio.api.RadioBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when player or redstone is going to change frequency of radio
 */
public class RadioFrequencyChangeEvent extends RadioEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final int oldFrequency;
    private final int newFrequency;
    private boolean cancelled;

    public RadioFrequencyChangeEvent(RadioBlock radio, @Nullable Player player, int oldFrequency, int newFrequency) {
        super(radio);
        this.player = player;
        this.oldFrequency = oldFrequency;
        this.newFrequency = newFrequency;
    }

    /** Player who changes frequency or null if it's redstone */
    @Nullable
    public Player getPlayer() {
        return player;
    }

    public int getOldFrequency() {
        return oldFrequency;
    }

    public int getNewFrequency() {
        return newFrequency;
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
