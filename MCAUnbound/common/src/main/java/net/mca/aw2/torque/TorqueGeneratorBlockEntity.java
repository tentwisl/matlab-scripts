package net.mca.aw2.torque;

import net.mca.aw2.AW2BlockEntityTypes;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Base block entity for torque generators (windmill, waterwheel, hand crank).
 * Generates torque energy each tick based on environmental conditions.
 */
public class TorqueGeneratorBlockEntity extends BlockEntity implements ITorqueProvider {
    protected final TorqueCell torqueCell;
    protected final TorqueTier tier;
    protected final GeneratorType generatorType;
    protected double generationRate;

    public enum GeneratorType {
        HAND_CRANK(1.0, TorqueTier.LIGHT),
        WINDMILL(2.5, TorqueTier.MEDIUM),
        WATERWHEEL(3.0, TorqueTier.MEDIUM),
        STIRLING(4.0, TorqueTier.HEAVY);

        private final double baseRate;
        private final TorqueTier tier;

        GeneratorType(double baseRate, TorqueTier tier) {
            this.baseRate = baseRate;
            this.tier = tier;
        }

        public double getBaseRate() {
            return baseRate;
        }

        public TorqueTier getTier() {
            return tier;
        }
    }

    public TorqueGeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, GeneratorType generatorType) {
        super(type, pos, state);
        this.generatorType = generatorType;
        this.tier = generatorType.getTier();
        this.torqueCell = tier.createCell();
        this.generationRate = generatorType.getBaseRate();
    }

    public static void tick(World world, BlockPos pos, BlockState state, TorqueGeneratorBlockEntity be) {
        if (world.isClient()) return;

        double rate = be.calculateGenerationRate(world, pos);
        if (rate > 0) {
            be.torqueCell.addEnergy(rate);
        }

        be.distributeTorque(world, pos);
    }

    protected double calculateGenerationRate(World world, BlockPos pos) {
        return switch (generatorType) {
            case WINDMILL -> {
                double heightBonus = Math.max(0, (pos.getY() - 64) * 0.02);
                boolean exposed = world.isSkyVisible(pos.up());
                yield exposed ? generationRate * (1.0 + heightBonus) : 0;
            }
            case WATERWHEEL -> {
                int waterCount = 0;
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    BlockPos check = pos.offset(dir);
                    if (!world.getFluidState(check).isEmpty()) {
                        waterCount++;
                    }
                }
                yield waterCount > 0 ? generationRate * (waterCount / 4.0) : 0;
            }
            case STIRLING -> {
                // Burns fuel in a furnace-like manner
                yield generationRate;
            }
            case HAND_CRANK -> 0; // Requires manual player interaction
        };
    }

    protected void distributeTorque(World world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockEntity neighbor = world.getBlockEntity(neighborPos);
            if (neighbor instanceof ITorqueProvider target) {
                if (target.canInputTorque(dir.getOpposite()) && this.canOutputTorque(dir)) {
                    double available = torqueCell.drainEnergy(tier.getMaxTransfer() * 0.1);
                    if (available > 0) {
                        double accepted = target.addTorque(dir.getOpposite(), available);
                        double leftover = available - accepted;
                        if (leftover > 0) {
                            torqueCell.addEnergy(leftover);
                        }
                    }
                }
            }
        }
    }

    public void onManualCrank() {
        if (generatorType == GeneratorType.HAND_CRANK) {
            torqueCell.addEnergy(generationRate * 10);
        }
    }

    @Override
    public double getTorqueStored(Direction side) {
        return torqueCell.getStoredEnergy();
    }

    @Override
    public double getMaxTorque(Direction side) {
        return torqueCell.getMaxEnergy();
    }

    @Override
    public double addTorque(Direction side, double amount) {
        return torqueCell.addEnergy(amount);
    }

    @Override
    public double drainTorque(Direction side, double amount) {
        return torqueCell.drainEnergy(amount);
    }

    @Override
    public boolean canOutputTorque(Direction side) {
        return true;
    }

    @Override
    public boolean canInputTorque(Direction side) {
        return false; // Generators only output
    }

    @Override
    public TorqueTier getTorqueTier() {
        return tier;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        torqueCell.writeNbt(nbt);
        nbt.putString("GeneratorType", generatorType.name());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        torqueCell.readNbt(nbt);
    }

    public TorqueCell getTorqueCell() {
        return torqueCell;
    }

    public GeneratorType getGeneratorType() {
        return generatorType;
    }
}
