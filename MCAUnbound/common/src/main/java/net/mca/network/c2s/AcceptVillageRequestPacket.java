package net.mca.network.c2s;

import net.mca.block.TownHallBlockEntity;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.HelpVillageDataResponse;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;

/** C2S: Player accepts an open village supply request. */
public class AcceptVillageRequestPacket implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long blockPosLong;
    private final int requestId;

    public AcceptVillageRequestPacket(BlockPos pos, int requestId) {
        this.blockPosLong = pos.asLong();
        this.requestId = requestId;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        BlockPos pos = BlockPos.fromLong(blockPosLong);
        ServerWorld world = player.getServerWorld();
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof TownHallBlockEntity townHall)) return;

        if (townHall.acceptRequest(requestId, player.getUuid())) {
            player.sendMessage(Text.literal("You accepted the village supply request. Check the Town Hall for details."), false);
            NetworkHandler.sendToPlayer(new HelpVillageDataResponse(townHall, player.getUuid()), player);
        }
    }
}
