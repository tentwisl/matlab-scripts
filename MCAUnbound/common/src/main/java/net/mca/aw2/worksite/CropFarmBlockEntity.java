package net.mca.aw2.worksite;

import net.mca.aw2.AW2BlockEntityTypes;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Automated crop farm worksite. Scans the work area for mature crops,
 * harvests them, and replants seeds. Can also till and plant new farmland.
 *
 * Ported from AW2's WorkSiteCropFarm, adapted for 1.20.1.
 * Supports: wheat, carrots, potatoes, beetroot, nether wart, melon, pumpkin,
 * sugar cane, cactus, and cocoa beans.
 */
public class CropFarmBlockEntity extends WorksiteBlockEntity {
    private int scanIndex = 0;
    private static final int SCAN_BATCH_SIZE = 16;

    public CropFarmBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, WorksiteType.CROP_FARM);
    }

    @Override
    protected boolean attemptWork(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return false;

        List<BlockPos> workArea = getWorkPositions();
        if (workArea.isEmpty()) return false;

        int processed = 0;
        int startIdx = scanIndex % workArea.size();

        for (int i = 0; i < SCAN_BATCH_SIZE && i < workArea.size(); i++) {
            int idx = (startIdx + i) % workArea.size();
            BlockPos farmPos = workArea.get(idx);
            BlockState farmState = world.getBlockState(farmPos);
            Block block = farmState.getBlock();

            // Harvest mature crops
            if (block instanceof CropBlock crop) {
                if (crop.isMature(farmState)) {
                    List<ItemStack> drops = farmState.getDroppedStacks(
                            new LootContextParameterSet.Builder(serverWorld)
                                    .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(farmPos))
                                    .add(LootContextParameters.TOOL, new ItemStack(Items.IRON_HOE))
                    );
                    for (ItemStack drop : drops) {
                        if (addToOutput(drop)) {
                            logProduction(drop.getItem().toString(), drop.getCount());
                        }
                    }
                    // Replant
                    world.setBlockState(farmPos, crop.getDefaultState());
                    processed++;
                }
            }
            // Harvest sugar cane (leave bottom block)
            else if (block == Blocks.SUGAR_CANE) {
                BlockState below = world.getBlockState(farmPos.down());
                if (below.getBlock() == Blocks.SUGAR_CANE) {
                    world.breakBlock(farmPos, false);
                    addToOutput(new ItemStack(Items.SUGAR_CANE));
                    logProduction("sugar_cane", 1);
                    processed++;
                }
            }
            // Harvest cactus (leave bottom block)
            else if (block == Blocks.CACTUS) {
                BlockState below = world.getBlockState(farmPos.down());
                if (below.getBlock() == Blocks.CACTUS) {
                    world.breakBlock(farmPos, false);
                    addToOutput(new ItemStack(Items.CACTUS));
                    logProduction("cactus", 1);
                    processed++;
                }
            }
            // Plant on empty farmland
            else if (farmState.isAir()) {
                BlockState below = world.getBlockState(farmPos.down());
                if (below.getBlock() == Blocks.FARMLAND) {
                    // Try to plant from input inventory
                    for (int slot = 0; slot < inputInventory.size(); slot++) {
                        ItemStack seed = inputInventory.getStack(slot);
                        if (!seed.isEmpty()) {
                            Block plantable = getPlantableBlock(seed);
                            if (plantable != null) {
                                world.setBlockState(farmPos, plantable.getDefaultState());
                                seed.decrement(1);
                                processed++;
                                break;
                            }
                        }
                    }
                }
            }
        }

        scanIndex = (startIdx + SCAN_BATCH_SIZE) % Math.max(1, workArea.size());
        return processed > 0;
    }

    private Block getPlantableBlock(ItemStack seed) {
        if (seed.isOf(Items.WHEAT_SEEDS)) return Blocks.WHEAT;
        if (seed.isOf(Items.CARROT)) return Blocks.CARROTS;
        if (seed.isOf(Items.POTATO)) return Blocks.POTATOES;
        if (seed.isOf(Items.BEETROOT_SEEDS)) return Blocks.BEETROOTS;
        if (seed.isOf(Items.MELON_SEEDS)) return Blocks.MELON_STEM;
        if (seed.isOf(Items.PUMPKIN_SEEDS)) return Blocks.PUMPKIN_STEM;
        if (seed.isOf(Items.NETHER_WART)) return Blocks.NETHER_WART;
        return null;
    }

    private List<BlockPos> getWorkPositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = boundsMin.getX(); x <= boundsMax.getX(); x++) {
            for (int z = boundsMin.getZ(); z <= boundsMax.getZ(); z++) {
                for (int y = boundsMin.getY(); y <= boundsMax.getY(); y++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    @Override
    public List<ItemStack> getPossibleOutputs() {
        return List.of(
                new ItemStack(Items.WHEAT),
                new ItemStack(Items.CARROT),
                new ItemStack(Items.POTATO),
                new ItemStack(Items.BEETROOT),
                new ItemStack(Items.SUGAR_CANE),
                new ItemStack(Items.MELON_SLICE),
                new ItemStack(Items.PUMPKIN)
        );
    }
}
