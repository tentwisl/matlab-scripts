package net.mca.server.world.data;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MoveState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Town Hall call mechanic.
 *
 * Flow:
 * 1) villager enters call-path mode and continuously repaths toward player/town hall
 * 2) upon arrival near player OR town hall, villager waits in-place for 10 seconds
 * 3) after wait, villager returns to default MCA autonomous pathing (MoveState.MOVE)
 *
 * If the player interacts with the villager during the wait phase, waiting is cancelled
 * and the villager immediately returns to default MCA pathing.
 */
public final class TownHallCallManager {
    private static final long WAIT_TICKS = 10L * 20L;
    private static final long REPATH_TICKS = 20L;
    private static final double ARRIVAL_DISTANCE_SQ = 3.0D * 3.0D;

    private static final Map<UUID, CallTask> TASKS = new HashMap<>();

    private TownHallCallManager() {
    }

    public static boolean isCalled(UUID villagerId) {
        return TASKS.containsKey(villagerId);
    }

    public static void startCall(VillagerEntityMCA villager, UUID playerUuid, BlockPos townHallPos, long nowTick) {
        CallTask task = new CallTask(playerUuid, townHallPos.toImmutable(), nowTick);
        TASKS.put(villager.getUuid(), task);

        // Force MCA autonomous movement while called.
        villager.getVillagerBrain().setMoveState(MoveState.MOVE, null);
        pathTowardTarget(villager, task, villager.getServerWorld());
        task.nextPathTick = nowTick + REPATH_TICKS;
    }

    public static boolean cancelWaitingOnInteract(VillagerEntityMCA villager) {
        CallTask task = TASKS.get(villager.getUuid());
        if (task == null || !task.waiting) {
            return false;
        }

        villager.getVillagerBrain().setMoveState(MoveState.MOVE, null);
        TASKS.remove(villager.getUuid());
        return true;
    }

    public static void tick(ServerWorld world) {
        long now = world.getTime();
        Iterator<Map.Entry<UUID, CallTask>> it = TASKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CallTask> entry = it.next();
            VillagerEntityMCA villager = world.getEntity(entry.getKey()) instanceof VillagerEntityMCA found ? found : null;
            if (villager == null || !villager.isAlive()) {
                it.remove();
                continue;
            }

            CallTask task = entry.getValue();
            if (task.waiting) {
                if (now >= task.waitUntilTick) {
                    villager.getVillagerBrain().setMoveState(MoveState.MOVE, null);
                    it.remove();
                }
                continue;
            }

            if (hasArrived(villager, task, world)) {
                task.waiting = true;
                task.waitUntilTick = now + WAIT_TICKS;
                villager.getNavigation().stop();
                continue;
            }

            if (now >= task.nextPathTick || villager.getNavigation().isIdle()) {
                pathTowardTarget(villager, task, world);
                task.nextPathTick = now + REPATH_TICKS;
            }
        }
    }

    private static boolean hasArrived(VillagerEntityMCA villager, CallTask task, ServerWorld world) {
        Vec3d villagerPos = villager.getPos();
        if (villagerPos.squaredDistanceTo(Vec3d.ofCenter(task.townHallPos)) <= ARRIVAL_DISTANCE_SQ) {
            return true;
        }

        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(task.playerUuid);
        return player != null && villagerPos.squaredDistanceTo(player.getPos()) <= ARRIVAL_DISTANCE_SQ;
    }

    private static void pathTowardTarget(VillagerEntityMCA villager, CallTask task, ServerWorld world) {
        BlockPos destination = task.townHallPos;
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(task.playerUuid);
        if (player != null && player.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
            destination = player.getBlockPos();
        }

        villager.getNavigation().startMovingTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D, 1.05D);
    }

    private static final class CallTask {
        private final UUID playerUuid;
        private final BlockPos townHallPos;
        private long nextPathTick;
        private boolean waiting;
        private long waitUntilTick;

        private CallTask(UUID playerUuid, BlockPos townHallPos, long nowTick) {
            this.playerUuid = playerUuid;
            this.townHallPos = townHallPos;
            this.nextPathTick = nowTick;
            this.waiting = false;
            this.waitUntilTick = 0L;
        }
    }
}
