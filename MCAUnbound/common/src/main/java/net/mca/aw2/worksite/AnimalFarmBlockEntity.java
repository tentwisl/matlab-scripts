package net.mca.aw2.worksite;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.passive.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

/**
 * Automated animal farm worksite. Manages breeding, shearing, and harvesting
 * of passive mobs within its work area.
 *
 * Ported from AW2's WorkSiteAnimalFarm. Supports cows, pigs, sheep, and chickens.
 * Animals above the population cap are harvested for drops.
 */
public class AnimalFarmBlockEntity extends WorksiteBlockEntity {
    private static final int MAX_ANIMALS_PER_TYPE = 12;
    private static final int MIN_BREEDING_PAIR = 2;
    private static final int WORK_INTERVAL_TICKS = 100; // ~5 seconds between actions
    private int tickCounter = 0;

    public AnimalFarmBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, WorksiteType.ANIMAL_FARM);
    }

    @Override
    protected boolean attemptWork(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return false;

        tickCounter++;
        if (tickCounter < WORK_INTERVAL_TICKS) return false;
        tickCounter = 0;

        Box workBox = new Box(boundsMin, boundsMax.add(1, 1, 1));
        boolean didWork = false;

        // Handle cows
        List<CowEntity> cows = world.getEntitiesByClass(CowEntity.class, workBox, EntityPredicates.VALID_ENTITY);
        didWork |= manageAnimals(serverWorld, cows, Items.WHEAT);

        // Handle sheep - shear if possible
        List<SheepEntity> sheep = world.getEntitiesByClass(SheepEntity.class, workBox, EntityPredicates.VALID_ENTITY);
        for (SheepEntity s : sheep) {
            if (!s.isSheared() && !s.isBaby()) {
                s.sheared(null);
                addToOutput(new ItemStack(Items.WHITE_WOOL, 1 + world.random.nextInt(3)));
                logProduction("wool", 1);
                didWork = true;
            }
        }
        didWork |= manageAnimals(serverWorld, sheep, Items.WHEAT);

        // Handle pigs
        List<PigEntity> pigs = world.getEntitiesByClass(PigEntity.class, workBox, EntityPredicates.VALID_ENTITY);
        didWork |= manageAnimals(serverWorld, pigs, Items.CARROT);

        // Handle chickens - collect eggs
        List<ChickenEntity> chickens = world.getEntitiesByClass(ChickenEntity.class, workBox, EntityPredicates.VALID_ENTITY);
        didWork |= manageAnimals(serverWorld, chickens, Items.WHEAT_SEEDS);

        return didWork;
    }

    private <T extends AnimalEntity> boolean manageAnimals(ServerWorld world, List<T> animals, net.minecraft.item.Item breedingFood) {
        boolean didWork = false;

        // Kill excess animals (keep breeding pair + buffer)
        if (animals.size() > MAX_ANIMALS_PER_TYPE) {
            int toKill = animals.size() - MAX_ANIMALS_PER_TYPE;
            for (int i = 0; i < toKill; i++) {
                T animal = animals.get(animals.size() - 1 - i);
                if (!animal.isBaby()) {
                    // Collect drops
                    animal.kill();
                    collectAnimalDrops(animal);
                    didWork = true;
                }
            }
        }

        // Breed animals if we have food and space
        if (animals.size() >= MIN_BREEDING_PAIR && animals.size() < MAX_ANIMALS_PER_TYPE) {
            boolean hasFood = false;
            for (int slot = 0; slot < inputInventory.size(); slot++) {
                if (inputInventory.getStack(slot).isOf(breedingFood)) {
                    hasFood = true;
                    break;
                }
            }

            if (hasFood) {
                int bred = 0;
                for (T animal : animals) {
                    if (!animal.isBaby() && animal.getBreedingAge() == 0 && bred < 2) {
                        animal.setBreedingAge(6000); // Cooldown
                        // Consume food from input
                        for (int slot = 0; slot < inputInventory.size(); slot++) {
                            ItemStack stack = inputInventory.getStack(slot);
                            if (stack.isOf(breedingFood)) {
                                stack.decrement(1);
                                break;
                            }
                        }
                        bred++;
                        didWork = true;
                    }
                    if (bred >= 2) break;
                }
            }
        }

        return didWork;
    }

    private void collectAnimalDrops(AnimalEntity animal) {
        if (animal instanceof CowEntity) {
            addToOutput(new ItemStack(Items.BEEF, 1 + animal.getWorld().random.nextInt(3)));
            addToOutput(new ItemStack(Items.LEATHER, 1));
            logProduction("beef", 1);
            logProduction("leather", 1);
        } else if (animal instanceof PigEntity) {
            addToOutput(new ItemStack(Items.PORKCHOP, 1 + animal.getWorld().random.nextInt(3)));
            logProduction("porkchop", 1);
        } else if (animal instanceof SheepEntity) {
            addToOutput(new ItemStack(Items.MUTTON, 1 + animal.getWorld().random.nextInt(2)));
            logProduction("mutton", 1);
        } else if (animal instanceof ChickenEntity) {
            addToOutput(new ItemStack(Items.CHICKEN, 1));
            addToOutput(new ItemStack(Items.FEATHER, 1 + animal.getWorld().random.nextInt(2)));
            logProduction("chicken", 1);
        }
    }

    @Override
    public List<ItemStack> getPossibleOutputs() {
        return List.of(
                new ItemStack(Items.BEEF),
                new ItemStack(Items.LEATHER),
                new ItemStack(Items.PORKCHOP),
                new ItemStack(Items.MUTTON),
                new ItemStack(Items.WHITE_WOOL),
                new ItemStack(Items.CHICKEN),
                new ItemStack(Items.FEATHER),
                new ItemStack(Items.EGG)
        );
    }
}
