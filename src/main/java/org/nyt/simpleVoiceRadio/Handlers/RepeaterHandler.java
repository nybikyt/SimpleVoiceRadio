package org.nyt.simpleVoiceRadio.Handlers;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.nyt.simpleVoiceRadio.VoiceAddon;

public class RepeaterHandler implements Listener {

    private final VoiceAddon voiceAddon;

    public RepeaterHandler(VoiceAddon voiceAddon) {
        this.voiceAddon = voiceAddon;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() == Material.LIGHTNING_ROD) {
            voiceAddon.addRepeater(event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.LIGHTNING_ROD) {
            voiceAddon.removeRepeater(event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getType() == Material.LIGHTNING_ROD) {
                voiceAddon.removeRepeater(block.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getType() == Material.LIGHTNING_ROD) {
                voiceAddon.removeRepeater(block.getLocation());
            }
        }
    }
}