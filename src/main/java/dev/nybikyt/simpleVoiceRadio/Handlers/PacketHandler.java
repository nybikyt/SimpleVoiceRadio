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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;

public class PacketHandler extends PacketListenerAbstract {
    private final SimpleVoiceRadio plugin;
    private final DataManager dataManager;

    public PacketHandler(SimpleVoiceRadio plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public void registerPacketListener() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.EFFECT) {
            WrapperPlayServerEffect wrapper = new WrapperPlayServerEffect(event);
            if (wrapper.getType() == 1010) {
                if (isRadioJukebox(event, wrapper.getPosition())) {
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
            if (isRadioJukebox(
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

    private boolean isRadioJukebox(PacketSendEvent event, Vector3i pos) {
        Player player = event.getPlayer();
        if (player == null) {
            return false;
        }

        World world = player.getWorld();
        Location loc = new Location(world, pos.getX(), pos.getY(), pos.getZ());

        return dataManager.getBlock(loc) != null;
    }
}
