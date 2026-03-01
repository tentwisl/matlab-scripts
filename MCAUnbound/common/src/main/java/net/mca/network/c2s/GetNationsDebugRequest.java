package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.nation.NationManager;
import net.mca.network.s2c.NationsDebugResponse;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Serial;

/** C2S: player requested the Nations Debug Screen data. */
public class GetNationsDebugRequest implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public void receive(ServerPlayerEntity player) {
        NationManager nm = NationManager.get(player.getServerWorld());
        NetworkHandler.sendToPlayer(new NationsDebugResponse(nm), player);
    }
}
