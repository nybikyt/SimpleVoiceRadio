package org.nyt.simpleVoiceRadio.Handlers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Jukebox;
import org.mineskin.data.Skin;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;
import org.nyt.simpleVoiceRadio.Utils.SkinManager;

public class PacketHandler {
    private final SimpleVoiceRadio plugin;
    private ProtocolManager protocolManager;

    public PacketHandler(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
        this.protocolManager = plugin.getProtocolManager();
    }

    public void registerSoundListener() {
        protocolManager.addPacketListener(new PacketAdapter(plugin,
                ListenerPriority.NORMAL,
                PacketType.Play.Server.WORLD_EVENT) {

            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPacket().getIntegers().read(0).equals(1010)) {
                    Jukebox jukebox = (Jukebox) event.getPacket().getBlockPositionModifier().read(0).toLocation(event.getPlayer().getWorld()).getBlock().getState();
                    if (jukebox.getRecord().getPersistentDataContainer().has(NamespacedKey.fromString("simple_voice_radio_disc"))) {
                        event.setCancelled(true);
                    }
                }
            }
        });
    }
}
