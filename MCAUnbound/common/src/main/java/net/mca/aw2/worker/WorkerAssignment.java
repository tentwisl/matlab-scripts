package net.mca.aw2.worker;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Tracks the assignment of an MCA villager to an AW2 worksite.
 * This bridges MCA's villager entity system with AW2's worksite system.
 */
public class WorkerAssignment {
    private final UUID villagerUuid;
    private final String villagerName;
    private final BlockPos worksitePos;
    private long lastWorkTick;
    private int totalWorkPerformed;

    public WorkerAssignment(UUID villagerUuid, String villagerName, BlockPos worksitePos) {
        this.villagerUuid = villagerUuid;
        this.villagerName = villagerName;
        this.worksitePos = worksitePos;
        this.lastWorkTick = 0;
        this.totalWorkPerformed = 0;
    }

    public UUID getVillagerUuid() {
        return villagerUuid;
    }

    public String getVillagerName() {
        return villagerName;
    }

    public BlockPos getWorksitePos() {
        return worksitePos;
    }

    public long getLastWorkTick() {
        return lastWorkTick;
    }

    public void setLastWorkTick(long tick) {
        this.lastWorkTick = tick;
    }

    public int getTotalWorkPerformed() {
        return totalWorkPerformed;
    }

    public void incrementWorkPerformed() {
        this.totalWorkPerformed++;
    }

    public NbtCompound writeNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("VillagerUuid", villagerUuid);
        nbt.putString("VillagerName", villagerName);
        nbt.putInt("WsX", worksitePos.getX());
        nbt.putInt("WsY", worksitePos.getY());
        nbt.putInt("WsZ", worksitePos.getZ());
        nbt.putLong("LastWorkTick", lastWorkTick);
        nbt.putInt("TotalWork", totalWorkPerformed);
        return nbt;
    }

    public static WorkerAssignment fromNbt(NbtCompound nbt) {
        UUID uuid = nbt.getUuid("VillagerUuid");
        String name = nbt.getString("VillagerName");
        BlockPos pos = new BlockPos(nbt.getInt("WsX"), nbt.getInt("WsY"), nbt.getInt("WsZ"));
        WorkerAssignment a = new WorkerAssignment(uuid, name, pos);
        a.lastWorkTick = nbt.getLong("LastWorkTick");
        a.totalWorkPerformed = nbt.getInt("TotalWork");
        return a;
    }
}
