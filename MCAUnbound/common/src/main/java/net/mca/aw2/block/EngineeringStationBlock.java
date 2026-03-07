package net.mca.aw2.block;

import net.mca.aw2.worksite.EngineeringStationBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Engineering Station block. A persistent crafting table for all AW2
 * and vanilla recipes. Items stay in the grid when the GUI is closed.
 * Research-locked recipes require a Research Book in the book slot.
 */
public class EngineeringStationBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public EngineeringStationBlock(Settings settings) {
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
        return null;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof EngineeringStationBlockEntity station) {
            if (station.getOwnerUuid() == null) {
                station.setOwner(player.getUuid());
            }

            player.sendMessage(Text.literal(
                    "§6[Engineering Station]§r Ready for crafting. Place a Research Book to unlock AW2 recipes."
            ), true);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof EngineeringStationBlockEntity station) {
                for (int i = 0; i < station.getCraftingGrid().size(); i++) {
                    Block.dropStack(world, pos, station.getCraftingGrid().getStack(i));
                }
                Block.dropStack(world, pos, station.getBookSlot().getStack(0));
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
