package dev.nybikyt.simpleVoiceRadio.api.events;

import dev.nybikyt.simpleVoiceRadio.api.RadioBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when radio is removed from world, not only by player.
 * Radio is already gone at this moment, so {@link RadioBlock#exists()} gives false
 */
public class RadioBreakEvent extends RadioEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    public RadioBreakEvent(RadioBlock radio, @Nullable Player player) {
        super(radio);
        this.player = player;
    }

    /** Player who broke radio or null if it wasn't player */
    @Nullable
    public Player getPlayer() {
        return player;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
