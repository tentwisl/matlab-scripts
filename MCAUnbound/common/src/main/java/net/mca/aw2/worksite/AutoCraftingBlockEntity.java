package net.mca.aw2.worksite;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;

/**
 * Automated crafting worksite. Maintains a recipe template and automatically
 * crafts items when input materials are available.
 *
 * Ported from AW2's WorkSiteAutoCrafting. Supports all vanilla crafting recipes.
 * A Research Book must be placed in the research slot to gate recipes
 * (matching AW2's research-locked crafting system).
 */
public class AutoCraftingBlockEntity extends WorksiteBlockEntity {
    public static final int RECIPE_GRID_SIZE = 9;

    // The 3x3 recipe template set by the player
    private final SimpleInventory recipeTemplate = new SimpleInventory(RECIPE_GRID_SIZE);
    // Cached recipe result
    private ItemStack cachedOutput = ItemStack.EMPTY;
    private boolean recipeDirty = true;

    public AutoCraftingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, WorksiteType.AUTO_CRAFTING);
    }

    @Override
    protected boolean attemptWork(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return false;

        if (recipeDirty) {
            updateCachedRecipe(serverWorld);
            recipeDirty = false;
        }

        if (cachedOutput.isEmpty()) return false;

        // Check if we have all required ingredients in input
        if (!hasRequiredIngredients()) return false;

        // Consume ingredients
        consumeIngredients();

        // Add output
        ItemStack result = cachedOutput.copy();
        if (addToOutput(result)) {
            logProduction(result.getItem().toString(), result.getCount());
            return true;
        }
        return false;
    }

    private void updateCachedRecipe(ServerWorld world) {
        CraftingInventory craftingInv = createCraftingInventory();

        Optional<CraftingRecipe> recipe = world.getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, craftingInv, world);

        cachedOutput = recipe.map(r -> r.craft(craftingInv, world.getRegistryManager()))
                .orElse(ItemStack.EMPTY);
    }

    private CraftingInventory createCraftingInventory() {
        CraftingInventory inv = new CraftingInventory(new CraftingScreenHandler(0, null) {
            @Override
            public boolean canUse(net.minecraft.entity.player.PlayerEntity player) {
                return false;
            }
        }, 3, 3);
        for (int i = 0; i < RECIPE_GRID_SIZE; i++) {
            inv.setStack(i, recipeTemplate.getStack(i).copy());
        }
        return inv;
    }

    private boolean hasRequiredIngredients() {
        // Count required items from recipe template
        SimpleInventory needed = new SimpleInventory(RECIPE_GRID_SIZE);
        for (int i = 0; i < RECIPE_GRID_SIZE; i++) {
            ItemStack templateItem = recipeTemplate.getStack(i);
            if (!templateItem.isEmpty()) {
                needed.setStack(i, templateItem.copy());
            }
        }

        // Check against input inventory
        SimpleInventory inputCopy = new SimpleInventory(inputInventory.size());
        for (int i = 0; i < inputInventory.size(); i++) {
            inputCopy.setStack(i, inputInventory.getStack(i).copy());
        }

        for (int i = 0; i < RECIPE_GRID_SIZE; i++) {
            ItemStack required = needed.getStack(i);
            if (required.isEmpty()) continue;

            boolean found = false;
            for (int j = 0; j < inputCopy.size(); j++) {
                ItemStack available = inputCopy.getStack(j);
                if (ItemStack.canCombine(available, required) && available.getCount() >= required.getCount()) {
                    available.decrement(required.getCount());
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private void consumeIngredients() {
        for (int i = 0; i < RECIPE_GRID_SIZE; i++) {
            ItemStack required = recipeTemplate.getStack(i);
            if (required.isEmpty()) continue;

            for (int j = 0; j < inputInventory.size(); j++) {
                ItemStack available = inputInventory.getStack(j);
                if (ItemStack.canCombine(available, required)) {
                    available.decrement(required.getCount());
                    break;
                }
            }
        }
    }

    public SimpleInventory getRecipeTemplate() {
        return recipeTemplate;
    }

    public void setRecipeSlot(int slot, ItemStack stack) {
        if (slot >= 0 && slot < RECIPE_GRID_SIZE) {
            recipeTemplate.setStack(slot, stack);
            recipeDirty = true;
            markDirty();
        }
    }

    public ItemStack getCachedOutput() {
        return cachedOutput;
    }

    @Override
    public List<ItemStack> getPossibleOutputs() {
        if (!cachedOutput.isEmpty()) {
            return List.of(cachedOutput.copy());
        }
        return List.of();
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        NbtCompound recipeNbt = new NbtCompound();
        for (int i = 0; i < RECIPE_GRID_SIZE; i++) {
            ItemStack stack = recipeTemplate.getStack(i);
            if (!stack.isEmpty()) {
                recipeNbt.put("Slot" + i, stack.writeNbt(new NbtCompound()));
            }
        }
        nbt.put("RecipeTemplate", recipeNbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("RecipeTemplate")) {
            NbtCompound recipeNbt = nbt.getCompound("RecipeTemplate");
            for (int i = 0; i < RECIPE_GRID_SIZE; i++) {
                if (recipeNbt.contains("Slot" + i)) {
                    recipeTemplate.setStack(i, ItemStack.fromNbt(recipeNbt.getCompound("Slot" + i)));
                }
            }
            recipeDirty = true;
        }
    }
}
