package net.mca.aw2.worker;

import com.google.common.collect.ImmutableMap;
import net.mca.aw2.AW2ColonyManager;
import net.mca.aw2.worksite.WorksiteBlockEntity;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.VillageManager;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;
import java.util.UUID;

/**
 * AI task that makes MCA villagers path to their assigned AW2 worksite
 * and perform work ticks. This is the core behavioral bridge between
 * MCA's villager AI system and AW2's worksite automation.
 */
public class WorkAtWorksiteTask extends MultiTickTask<VillagerEntityMCA> {
    private static final int WORK_RANGE_SQ = 9; // 3 blocks squared
    private static final int MAX_WORK_DURATION = 2400; // 2 minutes before taking a break
    private static final int PATH_RETRY_COOLDOWN = 40;
    private static final int ARM_SWING_INTERVAL = 10;
    private static final int RATION_CONSUMPTION_INTERVAL = 200;

    private BlockPos targetWorksite;
    private int workTicksRemaining;
    private int pathRetryCooldown;
    private int armSwingTimer;
    private int rationTimer;
    private boolean cachedFedState = true;

    public WorkAtWorksiteTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryModuleState.VALUE_ABSENT
        ), MAX_WORK_DURATION);
    }

    @Override
    protected boolean shouldRun(ServerWorld world, VillagerEntityMCA villager) {
        WorkerManager manager = WorkerManager.get(world);
        AW2ColonyManager colony = AW2ColonyManager.get(world);
        Optional<BlockPos> assignment = manager.getAssignment(villager.getUuid());

        if (assignment.isEmpty()) {
            colony.setWorkerStatus(villager.getUuid(), AW2ColonyManager.WorkerState.IDLE,
                    AW2ColonyManager.WorkerBlockedReason.NO_ASSIGNMENT, null, world.getTime());
            return false;
        }

        targetWorksite = assignment.get();
        colony.setWorkerStatus(villager.getUuid(), AW2ColonyManager.WorkerState.ASSIGNED,
                AW2ColonyManager.WorkerBlockedReason.NONE, targetWorksite, world.getTime());

        if (!(world.getBlockEntity(targetWorksite) instanceof WorksiteBlockEntity)) {
            manager.unassignVillager(villager.getUuid(), world);
            colony.setWorkerStatus(villager.getUuid(), AW2ColonyManager.WorkerState.BLOCKED,
                    AW2ColonyManager.WorkerBlockedReason.WORKSITE_MISSING, targetWorksite, world.getTime());
            return false;
        }

        return true;
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        workTicksRemaining = MAX_WORK_DURATION;
        pathRetryCooldown = 0;
        armSwingTimer = 0;
        rationTimer = 0;
        cachedFedState = true;
        AW2ColonyManager.get(world).setWorkerStatus(villager.getUuid(), AW2ColonyManager.WorkerState.TRAVELING,
                AW2ColonyManager.WorkerBlockedReason.OUT_OF_RANGE, targetWorksite, time);
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        if (targetWorksite == null) return;

        AW2ColonyManager colony = AW2ColonyManager.get(world);
        double distanceSq = villager.getBlockPos().getSquaredDistance(targetWorksite);

        if (distanceSq <= WORK_RANGE_SQ) {
            if (world.getBlockEntity(targetWorksite) instanceof WorksiteBlockEntity worksite) {
                if (rationTimer <= 0) {
                    cachedFedState = tryConsumeRation(world, villager.getUuid(), targetWorksite);
                    rationTimer = RATION_CONSUMPTION_INTERVAL;
                } else {
                    rationTimer--;
                }

                worksite.onWorkerTick(villager.getUuid(), cachedFedState);

                colony.setWorkerStatus(villager.getUuid(),
                        cachedFedState ? AW2ColonyManager.WorkerState.WORKING : AW2ColonyManager.WorkerState.STARVING,
                        cachedFedState ? AW2ColonyManager.WorkerBlockedReason.NONE : AW2ColonyManager.WorkerBlockedReason.NO_VILLAGE_FOOD,
                        targetWorksite, time);

                armSwingTimer++;
                if (armSwingTimer >= ARM_SWING_INTERVAL) {
                    villager.swingHand(Hand.MAIN_HAND);
                    armSwingTimer = 0;
                }

                villager.getLookControl().lookAt(
                        targetWorksite.getX() + 0.5,
                        targetWorksite.getY() + 0.5,
                        targetWorksite.getZ() + 0.5);
            }
            workTicksRemaining--;
            return;
        }

        if (pathRetryCooldown <= 0) {
            boolean started = villager.getNavigation().startMovingTo(
                    targetWorksite.getX() + 0.5,
                    targetWorksite.getY(),
                    targetWorksite.getZ() + 0.5,
                    0.5);

            colony.setWorkerStatus(villager.getUuid(), AW2ColonyManager.WorkerState.TRAVELING,
                    started ? AW2ColonyManager.WorkerBlockedReason.OUT_OF_RANGE : AW2ColonyManager.WorkerBlockedReason.PATHING_FAILED,
                    targetWorksite, time);
            pathRetryCooldown = PATH_RETRY_COOLDOWN;
        } else {
            pathRetryCooldown--;
        }
    }

    @Override
    protected boolean shouldKeepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        if (workTicksRemaining <= 0) return false;
        if (targetWorksite == null) return false;

        WorkerManager manager = WorkerManager.get(world);
        boolean present = manager.getAssignment(villager.getUuid()).isPresent();
        if (!present) {
            AW2ColonyManager.get(world).setWorkerStatus(villager.getUuid(), AW2ColonyManager.WorkerState.IDLE,
                    AW2ColonyManager.WorkerBlockedReason.NO_ASSIGNMENT, null, time);
        }
        return present;
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        villager.getNavigation().stop();
        AW2ColonyManager.get(world).setWorkerStatus(villager.getUuid(), AW2ColonyManager.WorkerState.IDLE,
                AW2ColonyManager.WorkerBlockedReason.NONE, targetWorksite, time);
    }

    private boolean tryConsumeRation(ServerWorld world, UUID villagerUuid, BlockPos worksitePos) {
        var villageOpt = VillageManager.get(world).findNearestVillage(worksitePos, 128);
        if (villageOpt.isEmpty()) {
            return false;
        }

        AW2ColonyManager colony = AW2ColonyManager.get(world);
        boolean consumed = colony.tryConsumeVillageRation(villageOpt.get().getVillageUuid(), colony.getWorkerRationCost());
        if (!consumed) {
            colony.setWorkerStatus(villagerUuid, AW2ColonyManager.WorkerState.STARVING,
                    AW2ColonyManager.WorkerBlockedReason.NO_VILLAGE_FOOD, worksitePos, world.getTime());
        }
        return consumed;
    }
}
