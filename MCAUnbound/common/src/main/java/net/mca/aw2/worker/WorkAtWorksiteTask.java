package net.mca.aw2.worker;

import com.google.common.collect.ImmutableMap;
import net.mca.aw2.worksite.WorksiteBlockEntity;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * AI task that makes MCA villagers path to their assigned AW2 worksite
 * and perform work ticks. This is the core behavioral bridge between
 * MCA's villager AI system and AW2's worksite automation.
 *
 * When a villager has an active worksite assignment (via WorkerManager),
 * this task will:
 * 1. Path the villager to the worksite block
 * 2. Once in range, call onWorkerTick() on the worksite each tick
 * 3. Continue working until the villager needs to eat, sleep, or socialize
 */
public class WorkAtWorksiteTask extends MultiTickTask<VillagerEntityMCA> {
    private static final int WORK_RANGE = 3;
    private static final int MAX_WORK_DURATION = 2400; // 2 minutes before taking a break
    private static final int PATH_RETRY_COOLDOWN = 40;

    private BlockPos targetWorksite;
    private int workTicksRemaining;
    private int pathRetryCooldown;

    public WorkAtWorksiteTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryModuleState.VALUE_ABSENT
        ), MAX_WORK_DURATION);
    }

    @Override
    protected boolean shouldRun(ServerWorld world, VillagerEntityMCA villager) {
        WorkerManager manager = WorkerManager.get(world);
        Optional<BlockPos> assignment = manager.getAssignment(villager.getUuid());

        if (assignment.isEmpty()) return false;

        targetWorksite = assignment.get();
        return true;
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        workTicksRemaining = MAX_WORK_DURATION;
        pathRetryCooldown = 0;
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        if (targetWorksite == null) return;

        double distance = villager.getBlockPos().getSquaredDistance(targetWorksite);

        if (distance <= WORK_RANGE * WORK_RANGE) {
            // In range - perform work
            if (world.getBlockEntity(targetWorksite) instanceof WorksiteBlockEntity worksite) {
                worksite.onWorkerTick(villager.getUuid());

                // Update worker assignment stats
                WorkerManager manager = WorkerManager.get(world);
                // Worker is actively working
            }
            workTicksRemaining--;
        } else {
            // Not in range - path to worksite
            if (pathRetryCooldown <= 0) {
                villager.getNavigation().startMovingTo(
                        targetWorksite.getX() + 0.5,
                        targetWorksite.getY(),
                        targetWorksite.getZ() + 0.5,
                        0.5);
                pathRetryCooldown = PATH_RETRY_COOLDOWN;
            } else {
                pathRetryCooldown--;
            }
        }
    }

    @Override
    protected boolean shouldKeepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        if (workTicksRemaining <= 0) return false;
        if (targetWorksite == null) return false;

        // Check if assignment still valid
        WorkerManager manager = WorkerManager.get(world);
        return manager.getAssignment(villager.getUuid()).isPresent();
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        villager.getNavigation().stop();
    }
}
