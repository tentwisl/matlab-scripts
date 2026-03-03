package net.mca.network.c2s;

import net.mca.block.TownHallBlockEntity;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.network.s2c.HelpVillageDataResponse;
import net.mca.server.world.data.Village;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
import java.util.Optional;
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

        // Find the leader villager and resolve their village for robust Town Hall lookup.
        Entity entity = world.getEntity(leaderUUID);
        if (!(entity instanceof VillagerEntityMCA villager)) return;

        Optional<Village> homeVillage = villager.getResidency().getHomeVillage();
        if (homeVillage.isEmpty()) return;

        Village village = homeVillage.get();
        int villageId   = village.getId();

        // ── Fast path: direct O(1) lookup via stored townHallPos ──────────────
        BlockPos storedPos = village.getTownHallPos();
        if (storedPos != null) {
            BlockEntity be = world.getBlockEntity(storedPos);
            if (be instanceof TownHallBlockEntity th) {
                sendResponse(th, player);
                return;
            }
            // Cached pos is stale (block destroyed/replaced) — clear and scan.
            village.setTownHallPos(null);
        }

        // ── Fallback: localized scan for pre-existing towns without townHallPos ─
        BlockPos centerPos = homeVillage
                .map(Village::getCenter)
                .map(v -> new BlockPos(v.getX(), v.getY(), v.getZ()))
                .orElse(entity.getBlockPos());

        TownHallBlockEntity townHall = findTownHall(world, villageId, centerPos, entity.getBlockPos());
        if (townHall == null) return;

        // Cache position so future opens are instant.
        village.setTownHallPos(townHall.getPos());
        sendResponse(townHall, player);
    }

    private void sendResponse(TownHallBlockEntity townHall, ServerPlayerEntity player) {
        // Ensure requests are initialised for pre-existing NPC-led towns.
        if (townHall.hasLeader() && !townHall.isLeaderPlayer()
                && townHall.getOpenRequests().isEmpty() && townHall.getPendingRequests().isEmpty()) {
            townHall.generateOpenRequests();
        }
        NetworkHandler.sendToPlayer(new HelpVillageDataResponse(townHall, player.getUuid()), player);
    }

    // ── Town Hall search helpers ───────────────────────────────────────────────

    private TownHallBlockEntity findTownHall(ServerWorld world, int villageId, BlockPos centerPos, BlockPos leaderPos) {
        // Exact surface lookup first — Town Halls are always placed at the highest surface.
        BlockPos surface = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, centerPos);
        TownHallBlockEntity atSurface = asTownHall(world.getBlockEntity(surface), villageId);
        if (atSurface != null) return atSurface;
        TownHallBlockEntity aboveSurface = asTownHall(world.getBlockEntity(surface.up()), villageId);
        if (aboveSurface != null) return aboveSurface;

        // Localized radius scans (step=2 is much cheaper than step=1 or step=4).
        TownHallBlockEntity aroundCenter = scanNearby(world, centerPos, villageId, 32, 2);
        if (aroundCenter != null) return aroundCenter;

        return scanNearby(world, leaderPos, villageId, 64, 2);
    }

    private TownHallBlockEntity scanNearby(ServerWorld world, BlockPos origin, int villageId, int radius, int step) {
        TownHallBlockEntity fallbackNearest = null;
        double fallbackDist = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                for (int dy = -16; dy <= 16; dy += step) {
                    BlockPos p = origin.add(dx, dy, dz);
                    BlockEntity be = world.getBlockEntity(p);
                    if (!(be instanceof TownHallBlockEntity th)) continue;

                    if (th.getVillageId() == villageId) return th;

                    double dist = th.getPos().getSquaredDistance(origin);
                    if (dist < fallbackDist) {
                        fallbackDist = dist;
                        fallbackNearest = th;
                    }
                }
            }
        }

        return fallbackNearest;
    }

    private TownHallBlockEntity asTownHall(BlockEntity be, int villageId) {
        if (be instanceof TownHallBlockEntity th && th.getVillageId() == villageId) return th;
        return null;
    }
}
