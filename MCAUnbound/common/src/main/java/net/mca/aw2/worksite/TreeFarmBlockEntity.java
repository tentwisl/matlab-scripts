package net.mca.aw2.worksite;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Automated tree farm worksite. Plants saplings, chops mature trees,
 * and collects logs and saplings.
 *
 * Ported from AW2's WorkSiteTreeFarm, adapted for 1.20.1.
 */
public class TreeFarmBlockEntity extends WorksiteBlockEntity {
    private int scanIndex = 0;
    private static final int SCAN_BATCH = 8;

    public TreeFarmBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, WorksiteType.TREE_FARM);
    }

    @Override
    protected boolean attemptWork(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return false;

        boolean didWork = false;

        // Scan ground level for choppable trees and plantable spots
        List<BlockPos> groundPositions = getGroundPositions();
        int startIdx = scanIndex % Math.max(1, groundPositions.size());

        for (int i = 0; i < SCAN_BATCH && i < groundPositions.size(); i++) {
            int idx = (startIdx + i) % groundPositions.size();
            BlockPos checkPos = groundPositions.get(idx);

            BlockState checkState = world.getBlockState(checkPos);
            Block block = checkState.getBlock();

            // Chop logs
            if (checkState.isIn(BlockTags.LOGS)) {
                didWork |= chopTree(serverWorld, checkPos);
            }
            // Plant saplings on empty dirt/grass
            else if (checkState.isAir()) {
                BlockState below = world.getBlockState(checkPos.down());
                if (below.isIn(BlockTags.DIRT)) {
                    didWork |= plantSapling(serverWorld, checkPos);
                }
            }
        }

        scanIndex = (startIdx + SCAN_BATCH) % Math.max(1, groundPositions.size());
        return didWork;
    }

    private boolean chopTree(ServerWorld world, BlockPos logPos) {
        // Chop upward from the base log, collecting all connected logs
        int logsChopped = 0;
        BlockPos current = logPos;

        while (logsChopped < 32) { // Safety limit
            BlockState state = world.getBlockState(current);
            if (!state.isIn(BlockTags.LOGS)) break;

            world.breakBlock(current, false);
            addToOutput(new ItemStack(state.getBlock().asItem()));
            logProduction("log", 1);
            logsChopped++;

            // Also check for leaves nearby and collect saplings
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        BlockPos leafPos = current.add(dx, dy, dz);
                        BlockState leafState = world.getBlockState(leafPos);
                        if (leafState.isIn(BlockTags.LEAVES)) {
                            world.breakBlock(leafPos, false);
                            // Random sapling drop
                            if (world.random.nextFloat() < 0.05f) {
                                addToOutput(getSaplingForLog(state));
                            }
                            // Random stick/apple drop
                            if (world.random.nextFloat() < 0.02f) {
                                addToOutput(new ItemStack(Items.STICK, 1 + world.random.nextInt(2)));
                            }
                        }
                    }
                }
            }

            current = current.up();
        }

        return logsChopped > 0;
    }

    private boolean plantSapling(ServerWorld world, BlockPos plantPos) {
        for (int slot = 0; slot < inputInventory.size(); slot++) {
            ItemStack stack = inputInventory.getStack(slot);
            if (!stack.isEmpty() && isSapling(stack)) {
                Block saplingBlock = getSaplingBlock(stack);
                if (saplingBlock != null) {
                    world.setBlockState(plantPos, saplingBlock.getDefaultState());
                    stack.decrement(1);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSapling(ItemStack stack) {
        return stack.isOf(Items.OAK_SAPLING) || stack.isOf(Items.BIRCH_SAPLING) ||
                stack.isOf(Items.SPRUCE_SAPLING) || stack.isOf(Items.JUNGLE_SAPLING) ||
                stack.isOf(Items.ACACIA_SAPLING) || stack.isOf(Items.DARK_OAK_SAPLING) ||
                stack.isOf(Items.CHERRY_SAPLING) || stack.isOf(Items.MANGROVE_PROPAGULE);
    }

    private Block getSaplingBlock(ItemStack stack) {
        if (stack.isOf(Items.OAK_SAPLING)) return Blocks.OAK_SAPLING;
        if (stack.isOf(Items.BIRCH_SAPLING)) return Blocks.BIRCH_SAPLING;
        if (stack.isOf(Items.SPRUCE_SAPLING)) return Blocks.SPRUCE_SAPLING;
        if (stack.isOf(Items.JUNGLE_SAPLING)) return Blocks.JUNGLE_SAPLING;
        if (stack.isOf(Items.ACACIA_SAPLING)) return Blocks.ACACIA_SAPLING;
        if (stack.isOf(Items.DARK_OAK_SAPLING)) return Blocks.DARK_OAK_SAPLING;
        if (stack.isOf(Items.CHERRY_SAPLING)) return Blocks.CHERRY_SAPLING;
        if (stack.isOf(Items.MANGROVE_PROPAGULE)) return Blocks.MANGROVE_PROPAGULE;
        return null;
    }

    private ItemStack getSaplingForLog(BlockState logState) {
        Block log = logState.getBlock();
        if (log == Blocks.OAK_LOG) return new ItemStack(Items.OAK_SAPLING);
        if (log == Blocks.BIRCH_LOG) return new ItemStack(Items.BIRCH_SAPLING);
        if (log == Blocks.SPRUCE_LOG) return new ItemStack(Items.SPRUCE_SAPLING);
        if (log == Blocks.JUNGLE_LOG) return new ItemStack(Items.JUNGLE_SAPLING);
        if (log == Blocks.ACACIA_LOG) return new ItemStack(Items.ACACIA_SAPLING);
        if (log == Blocks.DARK_OAK_LOG) return new ItemStack(Items.DARK_OAK_SAPLING);
        if (log == Blocks.CHERRY_LOG) return new ItemStack(Items.CHERRY_SAPLING);
        if (log == Blocks.MANGROVE_LOG) return new ItemStack(Items.MANGROVE_PROPAGULE);
        return new ItemStack(Items.OAK_SAPLING);
    }

    private List<BlockPos> getGroundPositions() {
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
                new ItemStack(Items.OAK_LOG),
                new ItemStack(Items.BIRCH_LOG),
                new ItemStack(Items.SPRUCE_LOG),
                new ItemStack(Items.OAK_SAPLING),
                new ItemStack(Items.STICK),
                new ItemStack(Items.APPLE)
        );
    }
}
