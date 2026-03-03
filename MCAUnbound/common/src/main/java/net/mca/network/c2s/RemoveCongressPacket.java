package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.nation.Nation;
import net.mca.nation.NationManager;
import net.mca.network.s2c.DiplomacyTableDataResponse;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Serial;
import java.util.Optional;
import java.util.UUID;

/**
 * C2S: Remove an NPC from the player nation's Congress.
 */
public class RemoveCongressPacket implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID villagerEntityId;

    public RemoveCongressPacket(UUID villagerEntityId) {
        this.villagerEntityId = villagerEntityId;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        NationManager    nm   = NationManager.get(player.getServerWorld());
        Optional<Nation> opt  = nm.getPlayerNation(player.getUuid());
        if (opt.isEmpty()) return;

        opt.get().getCongress().removeMember(villagerEntityId);
        nm.markDirty();

        NetworkHandler.sendToPlayer(new DiplomacyTableDataResponse(nm, player.getUuid()), player);
    }
}
