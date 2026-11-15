package dev.nybikyt.simpleVoiceRadio.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.util.List;

/**
 * API of SimpleVoiceRadio
 * <p>
 * Add SimpleVoiceRadio to depend or softdepend in your plugin.yml and get api with {@link #get()}
 * <pre>{@code
 * RadioApi api = RadioApi.get();
 * if (api == null) return;
 *
 * RadioBlock radio = api.getRadio(block);
 * if (radio != null) radio.setFrequency(5);
 * }</pre>
 */
public interface RadioApi {

    /** Gives api or null if SimpleVoiceRadio is not enabled */
    @Nullable
    static RadioApi get() {
        return Bukkit.getServicesManager().load(RadioApi.class);
    }

    /** New radio item, same as from craft or give command */
    ItemStack getRadioItem();

    boolean isRadioItem(@Nullable ItemStack item);

    boolean isRadio(Block block);

    /** Gives radio at this block or null if there is no radio */
    @Nullable
    RadioBlock getRadio(Block block);

    /** Gives all radios in loaded worlds */
    List<RadioBlock> getRadios();

    List<RadioBlock> getRadios(int frequency);

    /** Gives radios not further than radius blocks from location */
    List<RadioBlock> getRadiosNear(Location location, double radius);

    /** max_frequency from config */
    int getMaxFrequency();

    /** How far in blocks input radio hears players, input_search_radius from config */
    double getInputRadius();

    /** How far in blocks players hear output radio, output_radius from config */
    double getOutputRadius();

    /**
     * Places radio at location, block which was there gets replaced
     * <p>
     * Yaw of location is rotation of radio, same as yaw of player who places it.
     * Call it only from main thread, on folia from region thread of this location.
     * If you are in other thread go there through region scheduler, it works on paper 1.20+ and folia
     * <pre>{@code
     * Bukkit.getRegionScheduler().run(plugin, location, task -> api.placeRadio(location, 1));
     * }</pre>
     *
     * @param frequency from 1 to max_frequency, ignored if redstone_frequency is enabled
     * @return placed radio or null if there is already radio or frequency is wrong
     */
    @Nullable
    RadioBlock placeRadio(Location location, int frequency);

    /**
     * Plays audio file on radios, works same as play_audio command
     *
     * @param file mp3, ogg or wav file
     * @param frequency frequency of radios or 0 to play on all of them
     * @param loop play again when it ends
     * @return false if file cant be read, something is already playing or voice chat is not loaded yet
     */
    boolean playAudio(File file, int frequency, boolean loop);

    /** Same as {@link #playAudio(File, int, boolean)} but from url, url is not checked before playing */
    boolean playAudio(URL url, int frequency, boolean loop);

    /** Only audio from playAudio or play_audio command, not voice or discs */
    boolean isPlayingAudio();

    void stopAudio();
}
