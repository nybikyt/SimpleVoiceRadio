package dev.nybikyt.simpleVoiceRadio.Misc.WindChargeFix;

import dev.nybikyt.simpleVoiceRadio.Handlers.EventHandler;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

public class DefaultEntityExplodeListener implements Listener {

    private final DataManager dataManager;
    private final EventHandler eventHandler;

    public DefaultEntityExplodeListener(DataManager dataManager, EventHandler eventHandler) {
        this.dataManager = dataManager;
        this.eventHandler = eventHandler;
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.LOWEST)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            DataManager.RadioData blockData = dataManager.getBlock(block.getLocation());
            if (blockData != null) {
                eventHandler.breakRadio(block, blockData, false, false);
                return true;
            }
            return false;
        });
    }
}
