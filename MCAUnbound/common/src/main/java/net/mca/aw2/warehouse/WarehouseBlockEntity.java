package net.mca.aw2.warehouse;

import net.mca.aw2.worksite.WorksiteBlockEntity;
import net.mca.aw2.worksite.WorksiteType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Warehouse block entity. A large shared storage system that worksites can
 * deposit outputs into and draw inputs from. Acts as the central logistics
 * hub for AW2 automation chains.
 *
 * Implements NamedScreenHandlerFactory to provide a double-chest GUI
 * when players right-click the warehouse block.
 */
public class WarehouseBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {
    public static final int INVENTORY_SIZE = 54;

    private final WarehouseInventory inventory = new WarehouseInventory(INVENTORY_SIZE, this);
    private UUID ownerUuid;
    private String ownerName = "";

    // Item filters: slot -> allowed item identifier (empty = any)
    private final Map<Integer, String> slotFilters = new HashMap<>();

    // Stock tracking for nation economy
    private final Map<String, Integer> stockLevels = new HashMap<>();
    private long lastStockUpdate = 0;

    private static final int LOGISTICS_INTERVAL_TICKS = 100;
    private static final int LOGISTICS_RANGE = 24;
    private long lastLogisticsTick = 0;
    private int lastItemsHauledFromWorksites = 0;
    private int lastItemsSuppliedToWorksites = 0;

    public WarehouseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ==================== SCREEN HANDLER ====================

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.mca.aw2_warehouse");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return GenericContainerScreenHandler.createGeneric9x6(syncId, playerInventory, inventory);
    }

    // ==================== ITEM OPERATIONS ====================

    public ItemStack insertItem(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack remaining = stack.copy();

        // First pass: try to merge with existing stacks
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (!remaining.isEmpty() && matchesFilter(i, remaining)) {
                ItemStack existing = inventory.getStack(i);
                if (ItemStack.canCombine(existing, remaining)) {
                    int space = existing.getMaxCount() - existing.getCount();
                    int toAdd = Math.min(space, remaining.getCount());
                    if (toAdd > 0) {
                        existing.increment(toAdd);
                        remaining.decrement(toAdd);
                    }
                }
            }
        }

        // Second pass: insert into empty slots
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (!remaining.isEmpty() && matchesFilter(i, remaining)) {
                if (inventory.getStack(i).isEmpty()) {
                    inventory.setStack(i, remaining.copy());
                    remaining = ItemStack.EMPTY;
                    break;
                }
            }
        }

        if (!remaining.isEmpty() && remaining.getCount() < stack.getCount()) {
            markDirty();
        }
        return remaining;
    }

    public ItemStack extractItem(ItemStack target, int maxAmount) {
        int extracted = 0;
        ItemStack result = ItemStack.EMPTY;

        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack existing = inventory.getStack(i);
            if (ItemStack.canCombine(existing, target)) {
                int toExtract = Math.min(existing.getCount(), maxAmount - extracted);
                if (toExtract > 0) {
                    if (result.isEmpty()) {
                        result = existing.split(toExtract);
                    } else {
                        result.increment(toExtract);
                        existing.decrement(toExtract);
                    }
                    extracted += toExtract;
                }
            }
            if (extracted >= maxAmount) break;
        }

        if (extracted > 0) markDirty();
        return result;
    }

    public int countItem(ItemStack target) {
        int count = 0;
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack existing = inventory.getStack(i);
            if (ItemStack.canCombine(existing, target)) {
                count += existing.getCount();
            }
        }
        return count;
    }

    public void updateStockLevels() {
        stockLevels.clear();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                String id = stack.getItem().toString();
                stockLevels.merge(id, stack.getCount(), Integer::sum);
            }
        }
        lastStockUpdate = world != null ? world.getTime() : 0;
    }

    public Map<String, Integer> getStockLevels() {
        return Collections.unmodifiableMap(stockLevels);
    }

    // ==================== FILTERS ====================

    public void setSlotFilter(int slot, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            slotFilters.remove(slot);
        } else {
            slotFilters.put(slot, itemId);
        }
        markDirty();
    }

    public String getSlotFilter(int slot) {
        return slotFilters.getOrDefault(slot, "");
    }

    private boolean matchesFilter(int slot, ItemStack stack) {
        String filter = slotFilters.get(slot);
        if (filter == null || filter.isEmpty()) return true;
        return stack.getItem().toString().equals(filter);
    }

    // ==================== OWNER ====================

    public void setOwner(UUID uuid, String name) {
        this.ownerUuid = uuid;
        this.ownerName = name;
        markDirty();
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }

    public Inventory getInventory() { return inventory; }

    // ==================== LOGISTICS ====================

    public static void tick(net.minecraft.world.World world, BlockPos pos, BlockState state, WarehouseBlockEntity be) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (serverWorld.getTime() - be.lastLogisticsTick < LOGISTICS_INTERVAL_TICKS) return;

        be.lastLogisticsTick = serverWorld.getTime();
        int hauled = 0;
        int supplied = 0;

        Box scanBox = Box.from(pos.toCenterPos()).expand(LOGISTICS_RANGE);
        for (BlockPos checkPos : BlockPos.iterate(
                (int) scanBox.minX, (int) scanBox.minY, (int) scanBox.minZ,
                (int) scanBox.maxX, (int) scanBox.maxY, (int) scanBox.maxZ)) {
            if (!(serverWorld.getBlockEntity(checkPos) instanceof WorksiteBlockEntity worksite)) continue;

            hauled += be.haulOutputsFromWorksite(worksite);
            supplied += be.supplyInputsToWorksite(worksite);
        }

        be.lastItemsHauledFromWorksites = hauled;
        be.lastItemsSuppliedToWorksites = supplied;
        if (hauled > 0 || supplied > 0) {
            be.updateStockLevels();
            be.markDirty();
        }
    }

    private int haulOutputsFromWorksite(WorksiteBlockEntity worksite) {
        int moved = 0;
        var output = worksite.getOutputInventory();
        for (int i = 0; i < output.size(); i++) {
            ItemStack stack = output.getStack(i);
            if (stack.isEmpty()) continue;

            int before = stack.getCount();
            ItemStack remaining = insertItem(stack.copy());
            int transferred = before - remaining.getCount();
            if (transferred > 0) {
                stack.decrement(transferred);
                moved += transferred;
            }
        }
        return moved;
    }

    private int supplyInputsToWorksite(WorksiteBlockEntity worksite) {
        int moved = 0;
        var input = worksite.getInputInventory();

        // top-up existing input stacks (keeps recipes/worker feed stable)
        for (int i = 0; i < input.size(); i++) {
            ItemStack current = input.getStack(i);
            if (current.isEmpty()) continue;
            int targetCount = Math.min(current.getMaxCount(), 16);
            int need = targetCount - current.getCount();
            if (need <= 0) continue;

            ItemStack extracted = extractItem(current, need);
            if (!extracted.isEmpty()) {
                current.increment(extracted.getCount());
                moved += extracted.getCount();
            }
        }

        // seed empty inputs for common worksite consumables
        for (int i = 0; i < input.size(); i++) {
            if (!input.getStack(i).isEmpty()) continue;
            ItemStack template = getDefaultSupplyFor(worksite.getWorksiteType(), i);
            if (template.isEmpty()) continue;

            ItemStack extracted = extractItem(template, Math.min(8, template.getMaxCount()));
            if (!extracted.isEmpty()) {
                input.setStack(i, extracted);
                moved += extracted.getCount();
            }
        }

        return moved;
    }

    private ItemStack getDefaultSupplyFor(WorksiteType type, int slot) {
        return switch (type) {
            case CROP_FARM -> switch (slot % 4) {
                case 0 -> new ItemStack(Items.WHEAT_SEEDS, 1);
                case 1 -> new ItemStack(Items.CARROT, 1);
                case 2 -> new ItemStack(Items.POTATO, 1);
                default -> new ItemStack(Items.BEETROOT_SEEDS, 1);
            };
            case ANIMAL_FARM -> (slot % 2 == 0) ? new ItemStack(Items.WHEAT, 1) : new ItemStack(Items.CARROT, 1);
            case TREE_FARM -> new ItemStack(Items.OAK_SAPLING, 1);
            default -> ItemStack.EMPTY;
        };
    }

    public int getLastItemsHauledFromWorksites() {
        return lastItemsHauledFromWorksites;
    }

    public int getLastItemsSuppliedToWorksites() {
        return lastItemsSuppliedToWorksites;
    }

    // ==================== NBT ====================

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);

        if (ownerUuid != null) {
            nbt.putUuid("OwnerUuid", ownerUuid);
            nbt.putString("OwnerName", ownerName);
        }

        DefaultedList<ItemStack> stacks = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            stacks.set(i, inventory.getStack(i));
        }
        Inventories.writeNbt(nbt, stacks);

        nbt.putInt("LastHauled", lastItemsHauledFromWorksites);
        nbt.putInt("LastSupplied", lastItemsSuppliedToWorksites);

        NbtCompound filterNbt = new NbtCompound();
        for (Map.Entry<Integer, String> entry : slotFilters.entrySet()) {
            filterNbt.putString("Slot" + entry.getKey(), entry.getValue());
        }
        nbt.put("Filters", filterNbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);

        if (nbt.containsUuid("OwnerUuid")) {
            ownerUuid = nbt.getUuid("OwnerUuid");
            ownerName = nbt.getString("OwnerName");
        }

        DefaultedList<ItemStack> stacks = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
        Inventories.readNbt(nbt, stacks);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventory.setStack(i, stacks.get(i));
        }

        lastItemsHauledFromWorksites = nbt.getInt("LastHauled");
        lastItemsSuppliedToWorksites = nbt.getInt("LastSupplied");

        slotFilters.clear();
        if (nbt.contains("Filters")) {
            NbtCompound filterNbt = nbt.getCompound("Filters");
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                String key = "Slot" + i;
                if (filterNbt.contains(key)) {
                    slotFilters.put(i, filterNbt.getString(key));
                }
            }
        }
    }

    /**
     * Custom inventory wrapper that marks the block entity dirty on changes
     * and can be used as the backing inventory for GenericContainerScreenHandler.
     */
    private static class WarehouseInventory extends SimpleInventory {
        private final WarehouseBlockEntity owner;

        WarehouseInventory(int size, WarehouseBlockEntity owner) {
            super(size);
            this.owner = owner;
        }

        @Override
        public void markDirty() {
            super.markDirty();
            if (owner != null) {
                owner.markDirty();
            }
        }
    }
}
