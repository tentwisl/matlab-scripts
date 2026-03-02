package net.mca.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Block entity for the Diplomacy Table.
 * Stores the owning nation UUID so the GUI can load nation data from NationManager.
 */
public class DiplomacyTableBlockEntity extends BlockEntity {

    public DiplomacyTableBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypesMCA.DIPLOMACY_TABLE.get(), pos, state);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
    }
}
