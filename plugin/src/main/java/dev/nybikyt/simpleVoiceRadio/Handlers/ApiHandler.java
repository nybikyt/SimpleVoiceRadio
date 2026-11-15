package dev.nybikyt.simpleVoiceRadio.Handlers;

import dev.nybikyt.simpleVoiceRadio.Audio.AudioStreamer;
import dev.nybikyt.simpleVoiceRadio.Misc.Item;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.Radio;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.Utils.Scheduler;
import dev.nybikyt.simpleVoiceRadio.Voice.VoiceAddon;
import dev.nybikyt.simpleVoiceRadio.api.RadioApi;
import dev.nybikyt.simpleVoiceRadio.api.RadioBlock;
import dev.nybikyt.simpleVoiceRadio.api.RadioState;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiHandler implements RadioApi {

    private final PluginConfig config;
    private final DataManager dataManager;
    private final EventHandler eventHandler;
    private final Item item;
    private final VoiceAddon voiceAddon;

    public ApiHandler(PluginConfig config, DataManager dataManager, EventHandler eventHandler, Item item, VoiceAddon voiceAddon) {
        this.config = config;
        this.dataManager = dataManager;
        this.eventHandler = eventHandler;
        this.item = item;
        this.voiceAddon = voiceAddon;
    }

    @Override
    public ItemStack getRadioItem() {
        return item.getItem();
    }

    @Override
    public boolean isRadioItem(ItemStack stack) {
        return stack != null && Item.isRadioItem(stack.getItemMeta());
    }

    @Override
    public boolean isRadio(Block block) {
        return dataManager.get(block.getLocation()) != null;
    }

    @Override
    public RadioBlock getRadio(Block block) {
        Radio radio = dataManager.get(block.getLocation());
        return radio == null ? null : eventHandler.wrap(block.getLocation(), radio);
    }

    @Override
    public List<RadioBlock> getRadios() {
        return wrap(dataManager.getRadios());
    }

    @Override
    public List<RadioBlock> getRadios(int frequency) {
        Map<Location, Radio> radios = new HashMap<>();
        for (RadioState state : RadioState.values()) {
            radios.putAll(dataManager.getRadios(state, frequency));
        }
        return wrap(radios);
    }

    @Override
    public List<RadioBlock> getRadiosNear(Location location, double radius) {
        return wrap(dataManager.findRadiosNear(location, radius, radio -> true));
    }

    @Override
    public int getMaxFrequency() {
        return config.maxFrequency();
    }

    @Override
    public double getInputRadius() {
        return config.inputSearchRadius();
    }

    @Override
    public double getOutputRadius() {
        return config.outputRadius();
    }

    private List<RadioBlock> wrap(Map<Location, Radio> radios) {
        List<RadioBlock> result = new ArrayList<>(radios.size());
        radios.forEach((location, radio) -> result.add(eventHandler.wrap(location, radio)));
        return result;
    }

    @Override
    public RadioBlock placeRadio(Location location, int frequency) {
        Scheduler.checkThread(location);
        Block block = location.getBlock();
        boolean redstoneMode = config.redstoneFrequency();
        if (dataManager.get(block.getLocation()) != null) return null;
        if (!redstoneMode && (frequency < 1 || frequency > config.maxFrequency())) return null;

        float yaw = Math.round(location.getYaw() / 90f) * 90f;
        return eventHandler.createRadio(block, yaw, redstoneMode ? block.getBlockPower() : frequency, null);
    }

    @Override
    public boolean playAudio(File file, int frequency, boolean loop) {
        AudioStreamer.StreamSource source = () -> AudioSystem.getAudioInputStream(file);
        try {
            AudioStreamer.probe(source);
        } catch (Exception e) {
            return false;
        }
        return play(source, frequency, loop);
    }

    @Override
    public boolean playAudio(URL url, int frequency, boolean loop) {
        return play(() -> AudioSystem.getAudioInputStream(url), frequency, loop);
    }

    private boolean play(AudioStreamer.StreamSource source, int frequency, boolean loop) {
        if (voiceAddon == null || voiceAddon.getAudioRouter() == null || AudioStreamer.isStreaming()) return false;

        return voiceAddon.getAudioRouter().playAudio(source, frequency > 0 ? frequency : null, loop);
    }

    @Override
    public boolean isPlayingAudio() {
        return AudioStreamer.isStreaming();
    }

    @Override
    public void stopAudio() {
        AudioStreamer.stopStreaming();
    }
}
