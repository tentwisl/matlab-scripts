package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.nation.Nation;
import net.mca.nation.NationManager;
import net.mca.nation.congress.CongressData;
import net.mca.nation.congress.CongressMember;
import net.mca.network.s2c.DiplomacyTableDataResponse;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.io.Serial;
import java.util.Optional;
import java.util.UUID;

/**
 * C2S: Add an MCA villager (by UUID) as a Congress member of the player's nation.
 */
public class AppointCongressPacket implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID villagerEntityId;

    public AppointCongressPacket(UUID villagerEntityId) {
        this.villagerEntityId = villagerEntityId;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        ServerWorld    world  = player.getServerWorld();
        NationManager  nm     = NationManager.get(world);
        Optional<Nation> opt  = nm.getPlayerNation(player.getUuid());
        if (opt.isEmpty()) return;

        Nation nation = opt.get();
        CongressData congress = nation.getCongress();

        if (congress.size() >= CongressData.DEFAULT_SEATS) return;  // Congress is full
        if (congress.isMember(villagerEntityId))             return;  // Already a member

        Entity entity = world.getEntityByUuid(villagerEntityId);
        if (entity == null) return;

        congress.addMember(new CongressMember(villagerEntityId, false));
        nm.markDirty();

        NetworkHandler.sendToPlayer(new DiplomacyTableDataResponse(nm, player.getUuid()), player);
    }
}
