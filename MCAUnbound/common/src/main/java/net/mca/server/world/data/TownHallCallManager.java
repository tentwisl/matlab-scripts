package net.mca.server.world.data;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Town Hall call mechanic:
 * call -> 15s interaction window -> 15s post wait -> return to origin.
 */
public final class TownHallCallManager {
    private static final long PHASE_TICKS = 15L * 20L;
    private static final Map<UUID, CallTask> TASKS = new HashMap<>();

    private TownHallCallManager() {
    }

    public static void startCall(VillagerEntityMCA villager, BlockPos origin, BlockPos target, long nowTick) {
        TASKS.put(villager.getUuid(), new CallTask(origin.toImmutable(), target.toImmutable(), nowTick + PHASE_TICKS, nowTick + PHASE_TICKS * 2));
        villager.getNavigation().startMovingTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 1.05D);
    }

    public static void tick(ServerWorld world) {
        long now = world.getTime();
        Iterator<Map.Entry<UUID, CallTask>> it = TASKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CallTask> e = it.next();
            VillagerEntityMCA villager = world.getEntitiesByClass(VillagerEntityMCA.class,
                            new net.minecraft.util.math.Box(e.getValue().origin).expand(256),
                            v -> v.getUuid().equals(e.getKey()))
                    .stream().findFirst().orElse(null);
            if (villager == null) {
                it.remove();
                continue;
            }

            CallTask task = e.getValue();
            if (now >= task.returnAtTick) {
                villager.getNavigation().startMovingTo(task.origin.getX() + 0.5D, task.origin.getY(), task.origin.getZ() + 0.5D, 1.0D);
                it.remove();
            } else if (now >= task.interactionEndsAtTick && now % 40 == 0) {
                villager.getNavigation().stop();
            }
        }
    }

    private record CallTask(BlockPos origin, BlockPos target, long interactionEndsAtTick, long returnAtTick) {
    }
}
