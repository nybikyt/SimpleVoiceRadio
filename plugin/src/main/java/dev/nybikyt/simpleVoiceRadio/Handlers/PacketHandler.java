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
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class PacketHandler extends PacketListenerAbstract {

    private static final int EFFECT_RECORD_PLAY = 1010;

    private final DataManager dataManager;

    public PacketHandler(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public void registerPacketListener() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.EFFECT) {
            WrapperPlayServerEffect wrapper = new WrapperPlayServerEffect(event);
            if (wrapper.getType() != EFFECT_RECORD_PLAY) return;

            Vector3i pos = wrapper.getPosition();
            if (isRadioAt(event, pos.getX(), pos.getY(), pos.getZ())) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.PARTICLE) {
            WrapperPlayServerParticle wrapper = new WrapperPlayServerParticle(event);
            if (wrapper.getParticle().getType() != ParticleTypes.NOTE) return;

            Vector3d pos = wrapper.getPosition();
            int x = (int) Math.floor(pos.getX());
            int y = (int) Math.floor(pos.getY());
            int z = (int) Math.floor(pos.getZ());

            if (isRadioAt(event, x, y - 1, z) || isRadioAt(event, x, y, z)) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isRadioAt(PacketSendEvent event, int x, int y, int z) {
        Player player = event.getPlayer();
        if (player == null) return false;
        return dataManager.get(new Location(player.getWorld(), x, y, z)) != null;
    }
}
