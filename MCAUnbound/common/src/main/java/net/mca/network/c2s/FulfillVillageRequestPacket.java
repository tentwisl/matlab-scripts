package net.mca.network.c2s;

import net.mca.block.TownHallBlockEntity;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.HelpVillageDataResponse;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
import java.util.Optional;

/** C2S: Player attempts to fulfill (or partially fulfill) their accepted village request. */
public class FulfillVillageRequestPacket implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long blockPosLong;
    private final int requestId;

    public FulfillVillageRequestPacket(BlockPos pos, int requestId) {
        this.blockPosLong = pos.asLong();
        this.requestId = requestId;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        BlockPos pos = BlockPos.fromLong(blockPosLong);
        ServerWorld world = player.getServerWorld();
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof TownHallBlockEntity townHall)) return;

        boolean complete = townHall.tryFulfill(requestId, player);

        if (complete) {
            // Grant +10 hearts to all village residents
            Optional<Village> village = VillageManager.get(world).getOrEmpty(townHall.getVillageId());
            village.ifPresent(v -> v.pushHearts(player, 10));

            // Leader thanks the player
            String playerName = player.getName().getString();
            player.sendMessage(Text.literal("§6" + townHall.getLeaderName()
                    + "§r: Thank you, §e" + playerName + "§r! The village is grateful for your support. "
                    + "Your bond with all residents has grown stronger."), false);
        } else {
            player.sendMessage(Text.literal("§7You delivered some items. Keep going to complete the request!"), false);
        }

        // Refresh the screen
        NetworkHandler.sendToPlayer(new HelpVillageDataResponse(townHall, player.getUuid()), player);
    }
}
