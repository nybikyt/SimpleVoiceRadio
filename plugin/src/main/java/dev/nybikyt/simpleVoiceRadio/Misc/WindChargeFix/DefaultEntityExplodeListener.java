package dev.nybikyt.simpleVoiceRadio.Misc.WindChargeFix;

import dev.nybikyt.simpleVoiceRadio.Handlers.EventHandler;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.Radio;
import org.bukkit.block.Block;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

public class DefaultEntityExplodeListener implements Listener {

    private final DataManager dataManager;
    private final EventHandler eventHandler;

    public DefaultEntityExplodeListener(DataManager dataManager, EventHandler eventHandler) {
        this.dataManager = dataManager;
        this.eventHandler = eventHandler;
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(List<Block> blocks) {
        blocks.removeIf(block -> {
            Radio radio = dataManager.get(block.getLocation());
            if (radio != null) {
                eventHandler.breakRadio(block, radio, true, false);
                return true;
            }
            return false;
        });
    }
}
