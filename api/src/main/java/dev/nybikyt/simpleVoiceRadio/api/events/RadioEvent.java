package dev.nybikyt.simpleVoiceRadio.api.events;

import dev.nybikyt.simpleVoiceRadio.api.RadioBlock;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

/**
 * Base for all radio events
 * <p>
 * Events are called in thread where it happens. On folia it's region thread of radio,
 * and voice, discs and audio come from their own threads, so check {@link #isAsynchronous()} if it matters for you.
 * In async event dont touch world directly, do it through scheduler like in {@link RadioBlock}
 * <pre>{@code
 * Location location = event.getRadio().getLocation();
 * Bukkit.getRegionScheduler().run(plugin, location, task -> location.getWorld().strikeLightningEffect(location));
 * }</pre>
 */
public abstract class RadioEvent extends Event {

    private final RadioBlock radio;

    protected RadioEvent(RadioBlock radio) {
        super(!Bukkit.isPrimaryThread());
        this.radio = radio;
    }

    public RadioBlock getRadio() {
        return radio;
    }
}
