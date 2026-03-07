package net.mca.aw2.block;

import net.mca.aw2.torque.TorqueGeneratorBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
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
 * Block for torque generators (windmill, waterwheel, hand crank, stirling).
 */
public class TorqueGeneratorBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private final TorqueGeneratorBlockEntity.GeneratorType generatorType;

    public TorqueGeneratorBlock(Settings settings, TorqueGeneratorBlockEntity.GeneratorType generatorType) {
        super(settings);
        this.generatorType = generatorType;
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
        return null; // Overridden by specific block registrations
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) return null;
        return (w, p, s, be) -> {
            if (be instanceof TorqueGeneratorBlockEntity gen) {
                TorqueGeneratorBlockEntity.tick(w, p, s, gen);
            }
        };
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof TorqueGeneratorBlockEntity gen) {
            // Hand crank: manual activation
            if (generatorType == TorqueGeneratorBlockEntity.GeneratorType.HAND_CRANK) {
                gen.onManualCrank();
                player.sendMessage(Text.literal("§6[Hand Crank]§r Cranked! Torque generated."), true);
            } else {
                double torque = gen.getTorqueStored(Direction.UP);
                double maxTorque = gen.getMaxTorque(Direction.UP);
                player.sendMessage(Text.literal(String.format(
                        "§6[%s]§r Torque: %.0f/%.0f (%.0f%%)",
                        generatorType.name(), torque, maxTorque,
                        maxTorque > 0 ? (torque / maxTorque * 100) : 0
                )), true);
            }
        }

        return ActionResult.SUCCESS;
    }

    public TorqueGeneratorBlockEntity.GeneratorType getGeneratorType() {
        return generatorType;
    }
}
