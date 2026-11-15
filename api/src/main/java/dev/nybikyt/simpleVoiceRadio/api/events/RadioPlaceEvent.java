package dev.nybikyt.simpleVoiceRadio.api.events;

import dev.nybikyt.simpleVoiceRadio.api.RadioApi;
import dev.nybikyt.simpleVoiceRadio.api.RadioBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called after radio was placed
 * <p>
 * If you need to cancel placing use BlockPlaceEvent with {@link RadioApi#isRadioItem}
 * <pre>{@code
 * if (api.isRadioItem(event.getItemInHand())) event.setCancelled(true);
 * }</pre>
 */
public class RadioPlaceEvent extends RadioEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    public RadioPlaceEvent(RadioBlock radio, @Nullable Player player) {
        super(radio);
        this.player = player;
    }

    /** Player who placed radio or null if it was placed by api or command */
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
