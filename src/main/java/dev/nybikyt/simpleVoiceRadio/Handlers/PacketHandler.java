package dev.nybikyt.simpleVoiceRadio.Handlers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.*;
import org.bukkit.block.Jukebox;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.JukeboxManager;

public class PacketHandler {
    private final SimpleVoiceRadio plugin;
    private ProtocolManager protocolManager;

    public PacketHandler(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
        this.protocolManager = plugin.getProtocolManager();
    }

    public void registerPacketListener() {
        protocolManager.addPacketListener(new PacketAdapter(plugin,
                ListenerPriority.NORMAL,
                PacketType.Play.Server.WORLD_EVENT) {

            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPacket().getIntegers().read(0).equals(1010)) {
                    Jukebox jukebox = (Jukebox) event.getPacket().getBlockPositionModifier().read(0).toLocation(event.getPlayer().getWorld()).getBlock().getState();
                    if (jukebox.getRecord().getPersistentDataContainer().has(JukeboxManager.CUSTOM_DISC_KEY)) {
                        event.setCancelled(true);
                    }
                }
            }
        });

        if (plugin.getConfig().getBoolean("radio-block.signal_output_system", false))
            protocolManager.addPacketListener(new PacketAdapter(plugin,
                ListenerPriority.NORMAL,
                PacketType.Play.Server.WORLD_PARTICLES) {

            @Override
            public void onPacketSending(PacketEvent event) {
                var particle = event.getPacket().getNewParticles().read(0);

                if (particle.getParticle() != Particle.NOTE) {
                    return;
                }

                double x = event.getPacket().getDoubles().read(0);
                double y = event.getPacket().getDoubles().read(1);
                double z = event.getPacket().getDoubles().read(2);

                var location = new Location(event.getPlayer().getWorld(), x, y - 1, z);

                if (location.getBlock().getType().equals(Material.JUKEBOX)) {

                    Jukebox jukebox = (Jukebox) location.getBlock().getState();

                    if (jukebox.getRecord().getPersistentDataContainer().has(JukeboxManager.CUSTOM_DISC_KEY)) {
                        event.setCancelled(true);
                    }
                }
            }
        });
    }
}
