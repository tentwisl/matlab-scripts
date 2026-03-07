package net.mca.aw2.worksite;

import net.mca.aw2.research.ResearchManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Engineering Station block entity. A manual crafting block for all AW2 and
 * vanilla recipes. Unlike the vanilla crafting table, items persist in the
 * grid when the GUI is closed.
 *
 * Ported from AW2's TileEngineeringStation. Recipes are gated by the
 * research book placed in the book slot — only researched recipes can be crafted.
 */
public class EngineeringStationBlockEntity extends BlockEntity {
    public static final int GRID_SIZE = 9;

    private final SimpleInventory craftingGrid = new SimpleInventory(GRID_SIZE);
    private final SimpleInventory bookSlot = new SimpleInventory(1);
    private final SimpleInventory outputSlot = new SimpleInventory(1);

    private UUID ownerUuid;
    private ItemStack cachedOutput = ItemStack.EMPTY;
    private boolean recipeDirty = true;

    public EngineeringStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Attempts to craft the current recipe. Called when the player clicks
     * the output slot in the Engineering Station GUI.
     */
    public ItemStack tryCraft(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) return ItemStack.EMPTY;

        if (recipeDirty) {
            updateCachedOutput(serverWorld, player.getUuid());
            recipeDirty = false;
        }

        if (cachedOutput.isEmpty()) return ItemStack.EMPTY;

        // Consume ingredients
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack gridStack = craftingGrid.getStack(i);
            if (!gridStack.isEmpty()) {
                gridStack.decrement(1);
            }
        }

        recipeDirty = true;
        markDirty();
        return cachedOutput.copy();
    }

    public void updateCachedOutput(ServerWorld world, UUID playerUuid) {
        CraftingInventory inv = createCraftingInventory();

        Optional<CraftingRecipe> recipe = world.getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, inv, world);

        if (recipe.isPresent()) {
            ItemStack result = recipe.get().craft(inv, world.getRegistryManager());

            // Check if this recipe is research-gated
            Identifier recipeId = recipe.get().getId();
            if (isResearchGated(recipeId)) {
                // Check if player has the required research
                ResearchManager researchManager = ResearchManager.get(world);
                Set<Identifier> completed = researchManager.getCompletedResearch(playerUuid);

                // For now, allow all vanilla recipes. Only gate AW2 recipes.
                if (recipeId.getNamespace().equals("mca") && recipeId.getPath().startsWith("aw2_")) {
                    boolean hasResearch = completed.stream()
                            .anyMatch(r -> {
                                var goal = researchManager.getResearchTree().get(r);
                                return goal != null && goal.getUnlockedRecipes().contains(recipeId);
                            });
                    if (!hasResearch) {
                        cachedOutput = ItemStack.EMPTY;
                        return;
                    }
                }
            }

            cachedOutput = result;
        } else {
            cachedOutput = ItemStack.EMPTY;
        }
    }

    private boolean isResearchGated(Identifier recipeId) {
        // Only AW2 recipes are research-gated
        return recipeId.getNamespace().equals("mca") && recipeId.getPath().startsWith("aw2_");
    }

    private CraftingInventory createCraftingInventory() {
        CraftingInventory inv = new CraftingInventory(new CraftingScreenHandler(0, null) {
            @Override
            public boolean canUse(net.minecraft.entity.player.PlayerEntity player) {
                return false;
            }
        }, 3, 3);
        for (int i = 0; i < GRID_SIZE; i++) {
            inv.setStack(i, craftingGrid.getStack(i).copy());
        }
        return inv;
    }

    public void setGridSlot(int slot, ItemStack stack) {
        if (slot >= 0 && slot < GRID_SIZE) {
            craftingGrid.setStack(slot, stack);
            recipeDirty = true;
            markDirty();
        }
    }

    public SimpleInventory getCraftingGrid() { return craftingGrid; }
    public SimpleInventory getBookSlot() { return bookSlot; }
    public SimpleInventory getOutputSlot() { return outputSlot; }
    public ItemStack getCachedOutput() { return cachedOutput; }

    public void setOwner(UUID uuid) {
        this.ownerUuid = uuid;
        markDirty();
    }

    public UUID getOwnerUuid() { return ownerUuid; }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (ownerUuid != null) nbt.putUuid("OwnerUuid", ownerUuid);

        NbtCompound gridNbt = new NbtCompound();
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack stack = craftingGrid.getStack(i);
            if (!stack.isEmpty()) {
                gridNbt.put("Slot" + i, stack.writeNbt(new NbtCompound()));
            }
        }
        nbt.put("Grid", gridNbt);

        if (!bookSlot.getStack(0).isEmpty()) {
            nbt.put("Book", bookSlot.getStack(0).writeNbt(new NbtCompound()));
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.containsUuid("OwnerUuid")) ownerUuid = nbt.getUuid("OwnerUuid");

        if (nbt.contains("Grid")) {
            NbtCompound gridNbt = nbt.getCompound("Grid");
            for (int i = 0; i < GRID_SIZE; i++) {
                if (gridNbt.contains("Slot" + i)) {
                    craftingGrid.setStack(i, ItemStack.fromNbt(gridNbt.getCompound("Slot" + i)));
                }
            }
            recipeDirty = true;
        }

        if (nbt.contains("Book")) {
            bookSlot.setStack(0, ItemStack.fromNbt(nbt.getCompound("Book")));
        }
    }
}
