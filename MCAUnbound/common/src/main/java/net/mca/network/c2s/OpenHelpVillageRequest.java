package net.mca.network.c2s;

import net.mca.block.TownHallBlockEntity;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.HelpVillageDataResponse;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
import java.util.UUID;

/** C2S: Player opens the Help Village screen from the village leader. */
public class OpenHelpVillageRequest implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long mostSig, leastSig; // leader villager UUID

    public OpenHelpVillageRequest(UUID leaderUUID) {
        mostSig = leaderUUID.getMostSignificantBits();
        leastSig = leaderUUID.getLeastSignificantBits();
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        UUID leaderUUID = new UUID(mostSig, leastSig);
        ServerWorld world = player.getServerWorld();

        // Find the Town Hall block entity for this leader's village
        Entity entity = world.getEntity(leaderUUID);
        if (entity == null) return;

        // Search for a TownHallBlockEntity near the leader
        TownHallBlockEntity townHall = findTownHall(world, entity.getBlockPos());
        if (townHall == null) return;

        NetworkHandler.sendToPlayer(
                new HelpVillageDataResponse(townHall, player.getUuid()), player);
    }

    private TownHallBlockEntity findTownHall(ServerWorld world, BlockPos near) {
        // Search in a 128-block radius for a TownHallBlockEntity linked to this village
        for (int dx = -128; dx <= 128; dx += 4) {
            for (int dz = -128; dz <= 128; dz += 4) {
                for (int dy = -32; dy <= 32; dy += 4) {
                    BlockPos p = near.add(dx, dy, dz);
                    BlockEntity be = world.getBlockEntity(p);
                    if (be instanceof TownHallBlockEntity th) return th;
                }
            }
        }
        return null;
    }
}
