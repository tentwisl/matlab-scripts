package net.mca.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Block entity for the Town Hall.
 * Stores the MCA villageId so NationManager can retrieve associated CityData.
 */
public class TownHallBlockEntity extends BlockEntity {

    /** MCA Village.getId() for this town hall's village. -1 = unassigned. */
    private int villageId = -1;

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypesMCA.TOWN_HALL.get(), pos, state);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        villageId = nbt.getInt("villageId");
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("villageId", villageId);
    }

    public int getVillageId()             { return villageId; }
    public void setVillageId(int id)      { this.villageId = id; markDirty(); }
}
