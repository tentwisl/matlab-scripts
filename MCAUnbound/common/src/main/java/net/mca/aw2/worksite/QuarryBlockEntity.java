package net.mca.aw2.worksite;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Automated quarry worksite. Systematically mines blocks downward within
 * its work area, collecting stone, ores, and other mineable materials.
 *
 * Ported from AW2's WorkSiteQuarry. Mines layer by layer from top to bottom.
 * Depth is configurable via upgrades.
 */
public class QuarryBlockEntity extends WorksiteBlockEntity {
    private int currentMineY;
    private int currentMineX;
    private int currentMineZ;
    private int minY = 32; // Default minimum Y, lowered by upgrades
    private boolean isDepleted = false;

    public QuarryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, WorksiteType.QUARRY);
        this.currentMineY = pos.getY() - 1;
        this.currentMineX = boundsMin.getX();
        this.currentMineZ = boundsMin.getZ();
    }

    @Override
    protected void applyUpgradeEffects() {
        super.applyUpgradeEffects();
        if (hasUpgrade(WorksiteUpgrade.QUARRY_LARGE)) {
            minY = -64;
        } else if (hasUpgrade(WorksiteUpgrade.QUARRY_MEDIUM)) {
            minY = 0;
        }
    }

    @Override
    protected boolean attemptWork(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return false;
        if (isDepleted) return false;

        int blocksMinedThisTick = 0;
        int maxBlocksPerTick = (int) (2 * getEfficiencyMultiplier());

        while (blocksMinedThisTick < maxBlocksPerTick) {
            if (currentMineY < minY) {
                isDepleted = true;
                return blocksMinedThisTick > 0;
            }

            BlockPos minePos = new BlockPos(currentMineX, currentMineY, currentMineZ);
            BlockState mineState = world.getBlockState(minePos);

            // Skip air and fluids
            if (!mineState.isAir() && !mineState.getFluidState().isStill()) {
                Block block = mineState.getBlock();

                // Don't mine bedrock
                if (block != Blocks.BEDROCK) {
                    ItemStack drop = getDropForBlock(mineState);
                    if (!drop.isEmpty()) {
                        if (addToOutput(drop)) {
                            logProduction(drop.getItem().toString(), drop.getCount());
                        }
                    }
                    world.setBlockState(minePos, Blocks.AIR.getDefaultState());
                    blocksMinedThisTick++;
                }
            }

            advanceMinePosition();
        }

        return blocksMinedThisTick > 0;
    }

    private void advanceMinePosition() {
        currentMineX++;
        if (currentMineX > boundsMax.getX()) {
            currentMineX = boundsMin.getX();
            currentMineZ++;
            if (currentMineZ > boundsMax.getZ()) {
                currentMineZ = boundsMin.getZ();
                currentMineY--;
            }
        }
    }

    private ItemStack getDropForBlock(BlockState state) {
        Block block = state.getBlock();

        // Ore blocks drop their items
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
            return new ItemStack(Items.COAL);
        }
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
            return new ItemStack(Items.RAW_IRON);
        }
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) {
            return new ItemStack(Items.RAW_GOLD);
        }
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) {
            return new ItemStack(Items.RAW_COPPER);
        }
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
            return new ItemStack(Items.DIAMOND);
        }
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
            return new ItemStack(Items.EMERALD);
        }
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
            return new ItemStack(Items.LAPIS_LAZULI, 4);
        }
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) {
            return new ItemStack(Items.REDSTONE, 4);
        }
        if (block == Blocks.ANCIENT_DEBRIS) {
            return new ItemStack(Items.ANCIENT_DEBRIS);
        }

        // Stone variants
        if (state.isIn(BlockTags.BASE_STONE_OVERWORLD)) {
            return new ItemStack(Items.COBBLESTONE);
        }
        if (block == Blocks.DEEPSLATE) {
            return new ItemStack(Items.COBBLED_DEEPSLATE);
        }

        // Dirt, gravel, sand
        if (block == Blocks.DIRT || block == Blocks.GRASS_BLOCK) {
            return new ItemStack(Items.DIRT);
        }
        if (block == Blocks.GRAVEL) {
            return new ItemStack(Items.GRAVEL);
        }
        if (block == Blocks.SAND) {
            return new ItemStack(Items.SAND);
        }
        if (block == Blocks.CLAY) {
            return new ItemStack(Items.CLAY_BALL, 4);
        }

        // Default: drop the block itself
        return new ItemStack(block.asItem());
    }

    @Override
    public List<ItemStack> getPossibleOutputs() {
        return List.of(
                new ItemStack(Items.COBBLESTONE),
                new ItemStack(Items.COAL),
                new ItemStack(Items.RAW_IRON),
                new ItemStack(Items.RAW_GOLD),
                new ItemStack(Items.RAW_COPPER),
                new ItemStack(Items.DIAMOND),
                new ItemStack(Items.REDSTONE),
                new ItemStack(Items.LAPIS_LAZULI),
                new ItemStack(Items.DIRT),
                new ItemStack(Items.GRAVEL)
        );
    }
}
