package net.mca.aw2.worker;

import net.mca.aw2.AW2Integration;
import net.mca.aw2.worksite.WorksiteBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * Server-side manager tracking all worker-to-worksite assignments globally.
 * Stored as PersistentState in the overworld.
 *
 * This manager is the bridge between MCA villager entities and AW2 worksites.
 * When a player assigns a villager to a worksite (via interaction or GUI),
 * this manager records the assignment. The villager's AI brain then includes
 * a task to periodically path to their assigned worksite and perform work.
 */
public class WorkerManager extends PersistentState {
    private static final String DATA_KEY = "mca_aw2_workers";

    // villagerUUID -> worksite position
    private final Map<UUID, BlockPos> villagerAssignments = new HashMap<>();
    // villagerUUID -> worker role
    private final Map<UUID, WorkerRole> villagerRoles = new HashMap<>();

    public WorkerManager() {
        super();
    }

    public static WorkerManager get(ServerWorld world) {
        return world.getServer().getOverWorld().getPersistentStateManager()
                .getOrCreate(WorkerManager::fromNbt, WorkerManager::new, DATA_KEY);
    }

    /**
     * Assigns a villager to a worksite. The villager's AI will automatically
     * pick up this assignment and begin working.
     */
    public boolean assignVillagerToWorksite(UUID villagerUuid, String villagerName,
                                            BlockPos worksitePos, ServerWorld world) {
        // Remove any existing assignment first
        unassignVillager(villagerUuid, world);

        // Get the worksite block entity
        if (world.getBlockEntity(worksitePos) instanceof WorksiteBlockEntity worksite) {
            if (worksite.assignWorker(villagerUuid, villagerName)) {
                villagerAssignments.put(villagerUuid, worksitePos);
                villagerRoles.put(villagerUuid, WorkerRole.getBestRoleFor(worksite.getWorksiteType()));
                markDirty();
                AW2Integration.LOGGER.info("Assigned villager {} ({}) to worksite at {}",
                        villagerName, villagerUuid, worksitePos);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a villager from their current worksite assignment.
     */
    public void unassignVillager(UUID villagerUuid, ServerWorld world) {
        BlockPos oldPos = villagerAssignments.remove(villagerUuid);
        villagerRoles.remove(villagerUuid);
        if (oldPos != null && world.getBlockEntity(oldPos) instanceof WorksiteBlockEntity worksite) {
            worksite.removeWorker(villagerUuid);
        }
        markDirty();
    }

    /**
     * Gets the worksite position a villager is assigned to.
     */
    public Optional<BlockPos> getAssignment(UUID villagerUuid) {
        return Optional.ofNullable(villagerAssignments.get(villagerUuid));
    }

    /**
     * Gets the worker role for a villager.
     */
    public Optional<WorkerRole> getRole(UUID villagerUuid) {
        return Optional.ofNullable(villagerRoles.get(villagerUuid));
    }

    /**
     * Returns true if the villager has an active worksite assignment.
     */
    public boolean isAssigned(UUID villagerUuid) {
        return villagerAssignments.containsKey(villagerUuid);
    }

    /**
     * Gets all villager UUIDs assigned to a specific worksite.
     */
    public List<UUID> getWorkersAt(BlockPos worksitePos) {
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, BlockPos> entry : villagerAssignments.entrySet()) {
            if (entry.getValue().equals(worksitePos)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    // ==================== SERIALIZATION ====================

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, BlockPos> entry : villagerAssignments.entrySet()) {
            NbtCompound tag = new NbtCompound();
            tag.putUuid("Uuid", entry.getKey());
            tag.putInt("X", entry.getValue().getX());
            tag.putInt("Y", entry.getValue().getY());
            tag.putInt("Z", entry.getValue().getZ());
            WorkerRole role = villagerRoles.get(entry.getKey());
            if (role != null) {
                tag.putString("Role", role.name());
            }
            list.add(tag);
        }
        nbt.put("Assignments", list);
        return nbt;
    }

    public static WorkerManager fromNbt(NbtCompound nbt) {
        WorkerManager manager = new WorkerManager();
        NbtList list = nbt.getList("Assignments", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i);
            UUID uuid = tag.getUuid("Uuid");
            BlockPos pos = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
            manager.villagerAssignments.put(uuid, pos);
            if (tag.contains("Role")) {
                try {
                    manager.villagerRoles.put(uuid, WorkerRole.valueOf(tag.getString("Role")));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return manager;
    }
}
