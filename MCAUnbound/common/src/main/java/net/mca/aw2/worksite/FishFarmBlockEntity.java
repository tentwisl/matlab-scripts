package net.mca.aw2.worksite;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Automated fish farm worksite. Requires water blocks in its work area.
 * Periodically produces fish and other fishing loot.
 *
 * Ported from AW2's WorkSiteFishFarm.
 */
public class FishFarmBlockEntity extends WorksiteBlockEntity {
    private static final int FISH_INTERVAL_TICKS = 200; // ~10 seconds
    private int tickCounter = 0;

    public FishFarmBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, WorksiteType.FISH_FARM);
    }

    @Override
    protected boolean attemptWork(World world, BlockPos pos) {
        tickCounter++;
        if (tickCounter < (int) (FISH_INTERVAL_TICKS / getEfficiencyMultiplier())) {
            return false;
        }
        tickCounter = 0;

        // Count water blocks in work area for efficiency
        int waterBlocks = countWaterBlocks(world);
        if (waterBlocks < 4) return false; // Need minimum water

        double catchChance = Math.min(1.0, waterBlocks / 20.0);
        if (world.random.nextDouble() > catchChance) return false;

        ItemStack caught = getRandomFish(world);
        if (addToOutput(caught)) {
            logProduction(caught.getItem().toString(), caught.getCount());
            return true;
        }
        return false;
    }

    private int countWaterBlocks(World world) {
        int count = 0;
        for (int x = boundsMin.getX(); x <= boundsMax.getX(); x++) {
            for (int z = boundsMin.getZ(); z <= boundsMax.getZ(); z++) {
                for (int y = boundsMin.getY(); y <= boundsMax.getY(); y++) {
                    if (world.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.WATER) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private ItemStack getRandomFish(World world) {
        float roll = world.random.nextFloat();
        if (roll < 0.45f) return new ItemStack(Items.COD);
        if (roll < 0.70f) return new ItemStack(Items.SALMON);
        if (roll < 0.85f) return new ItemStack(Items.TROPICAL_FISH);
        if (roll < 0.93f) return new ItemStack(Items.PUFFERFISH);
        if (roll < 0.97f) return new ItemStack(Items.INK_SAC);
        return new ItemStack(Items.BONE); // Junk loot
    }

    @Override
    public List<ItemStack> getPossibleOutputs() {
        return List.of(
                new ItemStack(Items.COD),
                new ItemStack(Items.SALMON),
                new ItemStack(Items.TROPICAL_FISH),
                new ItemStack(Items.PUFFERFISH),
                new ItemStack(Items.INK_SAC)
        );
    }
}
