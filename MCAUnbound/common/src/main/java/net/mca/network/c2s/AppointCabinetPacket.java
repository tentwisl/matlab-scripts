package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.nation.Nation;
import net.mca.nation.NationManager;
import net.mca.nation.cabinet.CabinetRole;
import net.mca.network.s2c.DiplomacyTableDataResponse;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.io.Serial;
import java.util.Optional;
import java.util.UUID;

/**
 * C2S: Appoint an MCA villager (by UUID) to a Cabinet role in the player's nation.
 */
public class AppointCabinetPacket implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String roleName;
    private final UUID   villagerEntityId;

    public AppointCabinetPacket(CabinetRole role, UUID villagerEntityId) {
        this.roleName          = role.name();
        this.villagerEntityId  = villagerEntityId;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        CabinetRole role;
        try {
            role = CabinetRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            return;
        }

        ServerWorld    world  = player.getServerWorld();
        NationManager  nm     = NationManager.get(world);
        Optional<Nation> opt  = nm.getPlayerNation(player.getUuid());
        if (opt.isEmpty()) return;

        // Basic validation: entity must exist in the world
        Entity entity = world.getEntityByUuid(villagerEntityId);
        if (entity == null) return;

        opt.get().getCabinet().appoint(villagerEntityId, role);
        nm.markDirty();

        NetworkHandler.sendToPlayer(new DiplomacyTableDataResponse(nm, player.getUuid()), player);
    }
}
