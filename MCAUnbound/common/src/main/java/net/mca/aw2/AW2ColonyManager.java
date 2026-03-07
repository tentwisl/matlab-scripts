package net.mca.aw2;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cross-cutting runtime manager for AW2->MCA colony behavior.
 *
 * This intentionally connects multiple integration phases:
 * - Phase 0: runtime diagnostics/state capture
 * - Phase 1: worker state machine visibility
 * - Phase 2: structure-tier progression hooks (consumed by worksites)
 * - Phase 3: village food supply + worker ration consumption
 */
public class AW2ColonyManager extends PersistentState {
    private static final String DATA_KEY = "mca_aw2_colony_runtime";
    private static final int DAILY_FOOD_DECAY = 2;
    private static final long WORKER_STATUS_RETENTION_TICKS = 24000L * 7L;

    public enum WorkerState {
        IDLE,
        ASSIGNED,
        TRAVELING,
        WORKING,
        BLOCKED,
        STARVING
    }

    public enum WorkerBlockedReason {
        NONE,
        NO_ASSIGNMENT,
        WORKSITE_MISSING,
        PATHING_FAILED,
        NO_VILLAGE_FOOD,
        OUT_OF_RANGE
    }

    public record WorkerStatus(
            WorkerState state,
            WorkerBlockedReason blockedReason,
            BlockPos targetWorksite,
            long updatedAt
    ) {}

    private final Map<UUID, Integer> villageFoodPoints = new HashMap<>();
    private final Map<UUID, WorkerStatus> workerStatuses = new HashMap<>();
    private long lastFoodDecayDay = -1L;

    public static AW2ColonyManager get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager()
                .getOrCreate(AW2ColonyManager::fromNbt, AW2ColonyManager::new, DATA_KEY);
    }

    public void setWorkerStatus(UUID villagerUuid, WorkerState state, WorkerBlockedReason reason,
                                BlockPos targetWorksite, long time) {
        workerStatuses.put(villagerUuid, new WorkerStatus(state, reason, targetWorksite, time));
        markDirty();
    }

    public Optional<WorkerStatus> getWorkerStatus(UUID villagerUuid) {
        return Optional.ofNullable(workerStatuses.get(villagerUuid));
    }

    public void clearWorkerStatus(UUID villagerUuid) {
        if (workerStatuses.remove(villagerUuid) != null) {
            markDirty();
        }
    }

    public int getVillageFoodPoints(UUID villageUuid) {
        return villageFoodPoints.getOrDefault(villageUuid, 0);
    }

    public int getTrackedWorkerStatusCount() {
        return workerStatuses.size();
    }

    public int getWorkerRationCost() {
        return 1;
    }

    public boolean tryConsumeVillageRation(UUID villageUuid, int cost) {
        int current = villageFoodPoints.getOrDefault(villageUuid, 0);
        if (current < cost) return false;
        villageFoodPoints.put(villageUuid, current - cost);
        markDirty();
        return true;
    }

    public void registerVillageProduction(UUID villageUuid, Map<String, Integer> productionLog) {
        int points = 0;
        for (Map.Entry<String, Integer> entry : productionLog.entrySet()) {
            points += getFoodValue(entry.getKey()) * Math.max(0, entry.getValue());
        }

        if (points > 0) {
            villageFoodPoints.merge(villageUuid, points, Integer::sum);
            markDirty();
        }
    }

    public void tickDailyMaintenance(long worldTime) {
        long day = worldTime / 24000L;
        if (lastFoodDecayDay == day) return;
        lastFoodDecayDay = day;

        if (!villageFoodPoints.isEmpty()) {
            for (Map.Entry<UUID, Integer> entry : villageFoodPoints.entrySet()) {
                entry.setValue(Math.max(0, entry.getValue() - DAILY_FOOD_DECAY));
            }
        }

        pruneStaleWorkerStatuses(worldTime);
        markDirty();
    }

    private void pruneStaleWorkerStatuses(long worldTime) {
        workerStatuses.entrySet().removeIf(entry ->
                worldTime - entry.getValue().updatedAt() > WORKER_STATUS_RETENTION_TICKS);
    }

    private int getFoodValue(String itemIdRaw) {
        String itemId = itemIdRaw.toLowerCase(Locale.ROOT);

        if (itemId.contains("bread")) return 5;
        if (itemId.contains("carrot") || itemId.contains("potato") || itemId.contains("beetroot")) return 3;
        if (itemId.contains("cod") || itemId.contains("salmon") || itemId.contains("fish")) return 4;
        if (itemId.contains("beef") || itemId.contains("pork") || itemId.contains("mutton") || itemId.contains("chicken")) return 6;
        if (itemId.contains("apple") || itemId.contains("melon") || itemId.contains("pumpkin")) return 2;
        if (itemId.contains("egg") || itemId.contains("wheat") || itemId.contains("berry")) return 2;
        if (itemId.contains("rabbit") || itemId.contains("stew") || itemId.contains("soup")) return 5;

        return 0;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList foods = new NbtList();
        for (Map.Entry<UUID, Integer> entry : villageFoodPoints.entrySet()) {
            NbtCompound tag = new NbtCompound();
            tag.putUuid("Village", entry.getKey());
            tag.putInt("Food", entry.getValue());
            foods.add(tag);
        }
        nbt.put("VillageFood", foods);

        NbtList statuses = new NbtList();
        for (Map.Entry<UUID, WorkerStatus> entry : workerStatuses.entrySet()) {
            WorkerStatus status = entry.getValue();
            NbtCompound tag = new NbtCompound();
            tag.putUuid("Villager", entry.getKey());
            tag.putString("State", status.state().name());
            tag.putString("Reason", status.blockedReason().name());
            tag.putLong("UpdatedAt", status.updatedAt());
            if (status.targetWorksite() != null) {
                tag.putInt("X", status.targetWorksite().getX());
                tag.putInt("Y", status.targetWorksite().getY());
                tag.putInt("Z", status.targetWorksite().getZ());
            }
            statuses.add(tag);
        }
        nbt.put("WorkerStatus", statuses);

        nbt.putLong("LastFoodDecayDay", lastFoodDecayDay);
        return nbt;
    }

    public static AW2ColonyManager fromNbt(NbtCompound nbt) {
        AW2ColonyManager manager = new AW2ColonyManager();

        NbtList foods = nbt.getList("VillageFood", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < foods.size(); i++) {
            NbtCompound tag = foods.getCompound(i);
            manager.villageFoodPoints.put(tag.getUuid("Village"), tag.getInt("Food"));
        }

        NbtList statuses = nbt.getList("WorkerStatus", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < statuses.size(); i++) {
            NbtCompound tag = statuses.getCompound(i);
            UUID villager = tag.getUuid("Villager");
            WorkerState state;
            WorkerBlockedReason reason;
            try {
                state = WorkerState.valueOf(tag.getString("State"));
            } catch (IllegalArgumentException ex) {
                state = WorkerState.IDLE;
            }
            try {
                reason = WorkerBlockedReason.valueOf(tag.getString("Reason"));
            } catch (IllegalArgumentException ex) {
                reason = WorkerBlockedReason.NONE;
            }

            BlockPos target = null;
            if (tag.contains("X") && tag.contains("Y") && tag.contains("Z")) {
                target = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
            }

            manager.workerStatuses.put(villager,
                    new WorkerStatus(state, reason, target, tag.getLong("UpdatedAt")));
        }

        manager.lastFoodDecayDay = nbt.getLong("LastFoodDecayDay");
        return manager;
    }
}
