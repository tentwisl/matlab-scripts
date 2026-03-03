package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.nation.Nation;
import net.mca.nation.NationManager;
import net.mca.nation.cabinet.CabinetRole;
import net.mca.network.s2c.DiplomacyTableDataResponse;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Serial;
import java.util.Optional;

/**
 * C2S: Dismiss the current occupant of a Cabinet role in the player's nation.
 */
public class DismissCabinetPacket implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String roleName;

    public DismissCabinetPacket(CabinetRole role) {
        this.roleName = role.name();
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        CabinetRole role;
        try {
            role = CabinetRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            return;
        }

        NationManager    nm   = NationManager.get(player.getServerWorld());
        Optional<Nation> opt  = nm.getPlayerNation(player.getUuid());
        if (opt.isEmpty()) return;

        opt.get().getCabinet().dismiss(role);
        nm.markDirty();

        NetworkHandler.sendToPlayer(new DiplomacyTableDataResponse(nm, player.getUuid()), player);
    }
}
