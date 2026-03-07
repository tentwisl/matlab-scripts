package net.mca.aw2.block;

import net.mca.aw2.AW2BlockEntityTypes;
import net.mca.aw2.warehouse.WarehouseBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Warehouse block. A large shared storage system for the AW2 automation chain.
 * Opens a double-chest GUI (9x6) when right-clicked.
 */
public class WarehouseBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public WarehouseBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return AW2BlockEntityTypes.WAREHOUSE.get().instantiate(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof WarehouseBlockEntity warehouse) {
            if (warehouse.getOwnerUuid() == null) {
                warehouse.setOwner(player.getUuid(), player.getName().getString());
            }

            // Open the double-chest GUI
            player.openHandledScreen(warehouse);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof WarehouseBlockEntity warehouse) {
                var inv = warehouse.getInventory();
                for (int i = 0; i < inv.size(); i++) {
                    Block.dropStack(world, pos, inv.getStack(i));
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
