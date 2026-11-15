package dev.nybikyt.simpleVoiceRadio.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Radio placed in world
 * <p>
 * You can read it from any thread, but methods which change radio work only in main thread
 * (on folia in region thread of radio), from other threads they throw IllegalStateException.
 * So from async code go to radio thread first, region scheduler does it on paper 1.20+ and folia
 * <pre>{@code
 * Bukkit.getRegionScheduler().run(plugin, radio.getLocation(), task -> radio.setFrequency(3));
 * }</pre>
 * On 1.19.4 there is no region scheduler, use {@code Bukkit.getScheduler().runTask} there
 * After radio is broken {@link #exists()} gives false and other methods change nothing
 */
public interface RadioBlock {

    Location getLocation();

    /** 0 is possible only with redstone_frequency and means radio is off */
    int getFrequency();

    /**
     * @param frequency from 1 to max_frequency
     * @return false if frequency is wrong, radio is destroyed or redstone_frequency is enabled
     */
    boolean setFrequency(int frequency);

    /** Plugin can change it from audio threads, so dont cache it */
    RadioState getState();

    /**
     * @param state {@link RadioState#INPUT}, {@link RadioState#OUTPUT} or {@link RadioState#DESTROYED},
     *              other states are set only by plugin
     * @return false if state can't be changed now, for example radio is destroyed or is playing audio
     */
    boolean setState(RadioState state);

    /**
     * True if some sound goes through radio right now. For input radio it means somebody speaks into it,
     * for output radio that it plays something. It stays true for about 0.3 sec after sound ended
     */
    boolean isPlaying();

    /** Same as {@link #isPlaying()} but only for one kind of sound, radio can play several kinds at once */
    boolean isPlaying(AudioType type);

    /**
     * Players whose voice goes through radio right now.
     * For input radio its who speaks into it, for output radio who is heard from it
     */
    List<Player> getSpeakers();

    /** Output radios which get sound from this radio now, empty if this radio is not input and has no disc */
    List<RadioBlock> getReceivers();

    /** Redstone signal which comparator reads from radio, from 0 to 15. Always 0 without signal_output_system */
    int getSignalLevel();

    /**
     * How many chunks around radio its sound reaches output radios, it depends on lightning rods on top of radio.
     * Gives -1 if mode in config is global, then there is no limit.
     * Plugin looks at rods once in update_interval_ticks, so range changes not right after rod was placed
     */
    int getRange();

    /** @return false if radio is already removed */
    boolean remove(boolean dropItem);

    /** False if radio was broken or removed */
    boolean exists();
}
