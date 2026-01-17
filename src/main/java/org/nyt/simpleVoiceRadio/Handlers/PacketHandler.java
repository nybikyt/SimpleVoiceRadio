package org.nyt.simpleVoiceRadio.Handlers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
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
                    if (PlainTextComponentSerializer.plainText().serialize(jukebox.getRecord().displayName()).equalsIgnoreCase("[SimpleVoiceRadio]")) {
                        event.setCancelled(true);
                    }
                }
            }
        });
    }
}
