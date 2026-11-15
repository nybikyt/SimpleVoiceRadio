package dev.nybikyt.simpleVoiceRadio.Handlers;

import dev.nybikyt.simpleVoiceRadio.Audio.AudioRouter;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.BlockKey;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.Radio;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.Utils.Scheduler;
import dev.nybikyt.simpleVoiceRadio.api.AudioType;
import dev.nybikyt.simpleVoiceRadio.api.RadioBlock;
import dev.nybikyt.simpleVoiceRadio.api.RadioState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ApiRadio implements RadioBlock {

    private final SimpleVoiceRadio plugin;
    private final Location location;
    private final Radio radio;

    public ApiRadio(SimpleVoiceRadio plugin, Location location, Radio radio) {
        this.plugin = plugin;
        this.location = location;
        this.radio = radio;
    }

    @Override
    public Location getLocation() {
        return location.clone();
    }

    @Override
    public int getFrequency() {
        return radio.getFrequency();
    }

    @Override
    public boolean setFrequency(int frequency) {
        if (!exists()) return false;
        Scheduler.checkThread(location);
        PluginConfig config = plugin.getPluginConfig();
        if (radio.getState() == RadioState.DESTROYED || config.redstoneFrequency()) return false;
        if (frequency < 1 || frequency > config.maxFrequency()) return false;

        plugin.getEventHandler().updateRadioData(location, radio, frequency);
        return true;
    }

    @Override
    public RadioState getState() {
        return radio.getState();
    }

    @Override
    public boolean setState(RadioState state) {
        if (!exists()) return false;
        Scheduler.checkThread(location);
        RadioState current = radio.getState();
        if (current == state || current == RadioState.DESTROYED) return false;
        if (state == RadioState.DESTROYED) return plugin.getEventHandler().destroyRadio(location, radio);

        boolean switchable = current == RadioState.INPUT || current == RadioState.OUTPUT;
        boolean allowed = state == RadioState.OUTPUT || state == RadioState.INPUT && radio.getFrequency() > 0;
        return switchable && allowed && plugin.getEventHandler().switchState(location, radio, state, null);
    }

    @Override
    public boolean isPlaying() {
        return radio.getPlayback().isPlaying();
    }

    @Override
    public boolean isPlaying(AudioType type) {
        return radio.getPlayback().isPlaying(type);
    }

    @Override
    public List<Player> getSpeakers() {
        List<Player> speakers = new ArrayList<>();
        for (UUID id : radio.getPlayback().getSpeakers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) speakers.add(player);
        }
        return speakers;
    }

    @Override
    public List<RadioBlock> getReceivers() {
        List<RadioBlock> receivers = new ArrayList<>();
        AudioRouter router = plugin.getAudioRouter();
        if (router == null || !exists()) return receivers;

        router.getReceivers(location, radio).forEach((receiverLocation, receiver) ->
                receivers.add(new ApiRadio(plugin, receiverLocation, receiver)));
        return receivers;
    }

    @Override
    public int getSignalLevel() {
        return radio.getSignalLevel();
    }

    @Override
    public int getRange() {
        AudioRouter router = plugin.getAudioRouter();
        if (router == null || !location.isWorldLoaded()) return plugin.getPluginConfig().chunkMode() ? 0 : -1;
        return router.getAntennaManager().getRange(BlockKey.of(location));
    }

    @Override
    public boolean remove(boolean dropItem) {
        if (!exists()) return false;
        Scheduler.checkThread(location);

        plugin.getEventHandler().breakRadio(location.getBlock(), radio, true, dropItem);
        return true;
    }

    @Override
    public boolean exists() {
        return location.isWorldLoaded() && plugin.getDataManager().get(location) == radio;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ApiRadio apiRadio && apiRadio.radio == radio;
    }

    @Override
    public int hashCode() {
        return radio.hashCode();
    }
}
