package net.mca.aw2.block;

import net.mca.aw2.research.ResearchTableBlockEntity;
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
 * Research Table block. Players interact with this to select and progress
 * research goals from the AW2 tech tree.
 */
public class ResearchTableBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty HAS_BOOK = BooleanProperty.of("has_book");

    public ResearchTableBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(HAS_BOOK, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_BOOK);
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
        return null; // Set by block entity type registration
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) return null;
        return (w, p, s, be) -> {
            if (be instanceof ResearchTableBlockEntity table) {
                ResearchTableBlockEntity.tick(w, p, s, table);
            }
        };
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof ResearchTableBlockEntity table) {
            if (table.getOwnerUuid() == null) {
                table.setOwner(player.getUuid(), player.getName().getString());
            }

            var researchId = table.getCurrentResearchId();
            if (researchId != null) {
                player.sendMessage(Text.literal(String.format(
                        "§6[Research Table]§r Researching: %s | Has Book: %s | Researcher: %s",
                        researchId.getPath(),
                        table.hasBook() ? "§a✓§r" : "§c✗§r",
                        table.getAssignedResearcherName().isEmpty() ? "None" : table.getAssignedResearcherName()
                )), true);
            } else {
                player.sendMessage(Text.literal(
                        "§6[Research Table]§r No active research. Use the research GUI to select a goal."
                ), true);
            }
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof ResearchTableBlockEntity table) {
                for (int i = 0; i < table.getResourceInventory().size(); i++) {
                    Block.dropStack(world, pos, table.getResourceInventory().getStack(i));
                }
                Block.dropStack(world, pos, table.getBookInventory().getStack(0));
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
