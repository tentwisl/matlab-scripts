package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.nation.NationManager;
import net.mca.network.s2c.DiplomacyTableDataResponse;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;

public class OpenDiplomacyTableRequest implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long posLong;

    public OpenDiplomacyTableRequest(BlockPos pos) {
        this.posLong = pos.asLong();
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        NationManager nm = NationManager.get(player.getServerWorld());
        NetworkHandler.sendToPlayer(new DiplomacyTableDataResponse(nm, player.getUuid()), player);
    }
}
