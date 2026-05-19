package dev.nybikyt.simpleVoiceRadio.Handlers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.JukeboxManager;

public class PacketHandler extends PacketListenerAbstract {
    private final SimpleVoiceRadio plugin;

    public PacketHandler(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
    }

    public void registerPacketListener() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.EFFECT) {
            WrapperPlayServerEffect wrapper = new WrapperPlayServerEffect(event);
            if (wrapper.getType() == 1010) {
                if (isCustomDisc(event, wrapper.getPosition())) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        if (!plugin.getConfig().getBoolean("radio-block.signal_output_system")) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.PARTICLE) {
            WrapperPlayServerParticle wrapper = new WrapperPlayServerParticle(event);
            if (wrapper.getParticle().getType() != ParticleTypes.NOTE) {
                return;
            }
            Vector3d pos = wrapper.getPosition();
            if (isCustomDisc(
                    event,
                    new Vector3i(
                            (int) pos.getX(),
                            (int) pos.getY() - 1,
                            (int) pos.getZ()
                    )
            )) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isCustomDisc(PacketSendEvent event, Vector3i pos) {
        Player player = event.getPlayer();

        Block block = player.getWorld().getBlockAt(
                pos.getX(),
                pos.getY(),
                pos.getZ()
        );

        if (block.getType() != Material.JUKEBOX) {
            return false;
        }

        Jukebox jukebox = (Jukebox) block.getState();

        ItemStack record = jukebox.getRecord();
        if (record.getType().isAir()) {
            return false;
        }

        ItemMeta meta = record.getItemMeta();

        return meta != null
                && meta.getPersistentDataContainer().has(
                JukeboxManager.CUSTOM_DISC_KEY,
                PersistentDataType.BYTE
        );
    }
}