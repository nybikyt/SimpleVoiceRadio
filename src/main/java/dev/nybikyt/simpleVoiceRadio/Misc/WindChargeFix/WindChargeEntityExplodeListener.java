package dev.nybikyt.simpleVoiceRadio.Misc.WindChargeFix;

import dev.nybikyt.simpleVoiceRadio.Handlers.EventHandler;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.block.Block;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

public class WindChargeEntityExplodeListener implements Listener {

    private final DataManager dataManager;
    private final EventHandler eventHandler;

    public WindChargeEntityExplodeListener(DataManager dataManager, EventHandler eventHandler) {
        this.dataManager = dataManager;
        this.eventHandler = eventHandler;
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.LOWEST)
    public void onExplode(EntityExplodeEvent event) {
        boolean windCharge = event.getExplosionResult() == ExplosionResult.TRIGGER_BLOCK;

        event.blockList().removeIf(block -> {
            DataManager.RadioData data = dataManager.getBlock(block.getLocation());

            if (data == null) return false;

            if (!windCharge) {
                eventHandler.breakRadio(block, data, true, false);
            }

            return true;
        });
    }
}