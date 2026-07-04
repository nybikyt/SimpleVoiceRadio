package dev.nybikyt.simpleVoiceRadio.Misc.WindChargeFix;

import dev.nybikyt.simpleVoiceRadio.Handlers.EventHandler;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.Radio;
import org.bukkit.ExplosionResult;
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

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        boolean windCharge = event.getExplosionResult() == ExplosionResult.TRIGGER_BLOCK;

        event.blockList().removeIf(block -> {
            Radio radio = dataManager.get(block.getLocation());
            if (radio == null) return false;

            if (!windCharge) {
                eventHandler.breakRadio(block, radio, true, false);
            }
            return true;
        });
    }
}
