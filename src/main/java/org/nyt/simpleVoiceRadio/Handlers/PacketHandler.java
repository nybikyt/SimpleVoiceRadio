package org.nyt.simpleVoiceRadio.Handlers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import org.bukkit.block.Jukebox;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;

public class PacketHandler {
    private final SimpleVoiceRadio plugin;
    private ProtocolManager protocolManager;

    public PacketHandler(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
        this.protocolManager = plugin.getProtocolManager();
    }

    public void registerActionBarListener() {
        protocolManager.addPacketListener(new PacketAdapter(plugin,
                ListenerPriority.NORMAL,
                PacketType.Play.Server.WORLD_EVENT) {

            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPacket().getIntegers().read(0).equals(1010)) {
                    Jukebox jukebox = (Jukebox) event.getPacket().getBlockPositionModifier().read(0).toLocation(event.getPlayer().getWorld()).getBlock().getState();
                    if (jukebox.getRecord().getItemMeta().getJukeboxPlayable().getSong().getKey().getNamespace().equals("simple_voice_radio")) {
                        event.setCancelled(true);
                    }
                }
            }
        });
    }
}
