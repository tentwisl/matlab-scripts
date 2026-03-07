package net.mca.aw2.block;

import net.mca.aw2.worksite.WorksiteBlockEntity;
import net.mca.aw2.worksite.WorksiteType;
import net.mca.aw2.worker.WorkerManager;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
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
 * Base block for all AW2 worksites. Handles placement, interaction,
 * and linking to block entities. Visual state includes facing direction
 * and active/inactive indicator.
 */
public class WorksiteBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");

    private final WorksiteType worksiteType;

    public WorksiteBlock(Settings settings, WorksiteType worksiteType) {
        super(settings);
        this.worksiteType = worksiteType;
        setDefaultState(getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(ACTIVE, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
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
        // Subclasses override this
        return null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) return null;
        return (w, p, s, be) -> {
            if (be instanceof WorksiteBlockEntity worksite) {
                WorksiteBlockEntity.tick(w, p, s, worksite);
            }
        };
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof WorksiteBlockEntity worksite) {
            // Set owner on first interaction
            if (worksite.getOwnerUuid() == null) {
                worksite.setOwner(player.getUuid(), player.getName().getString());
            }

            // Send status message
            int workers = worksite.getWorkerCount();
            int maxWorkers = worksite.getWorksiteType().getMaxWorkers();
            boolean active = worksite.isActive();
            double torque = worksite.getTorqueStored(Direction.UP);
            double maxTorque = worksite.getMaxTorque(Direction.UP);

            player.sendMessage(Text.literal(String.format(
                    "§6[%s]§r Workers: %d/%d | Active: %s | Torque: %.0f/%.0f | Work Done: %d",
                    worksiteType.getDisplayName(), workers, maxWorkers,
                    active ? "§a✓§r" : "§c✗§r",
                    torque, maxTorque, worksite.getTotalWorkDone()
            )), true);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof WorksiteBlockEntity worksite) {
                // Drop inventories
                for (int i = 0; i < worksite.getInputInventory().size(); i++) {
                    Block.dropStack(world, pos, worksite.getInputInventory().getStack(i));
                }
                for (int i = 0; i < worksite.getOutputInventory().size(); i++) {
                    Block.dropStack(world, pos, worksite.getOutputInventory().getStack(i));
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    public WorksiteType getWorksiteType() {
        return worksiteType;
    }
}
