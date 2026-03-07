package net.mca.aw2.warehouse;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Warehouse block entity. A large shared storage system that worksites can
 * deposit outputs into and draw inputs from. Acts as the central logistics
 * hub for AW2 automation chains.
 *
 * Ported from AW2's TileWarehouse. Features:
 * - 54-slot main inventory (double chest equivalent)
 * - Item filtering per slot
 * - Auto-input from adjacent worksites
 * - Stock tracking for the nation economy system
 */
public class WarehouseBlockEntity extends BlockEntity {
    public static final int INVENTORY_SIZE = 54;

    private final SimpleInventory inventory = new SimpleInventory(INVENTORY_SIZE);
    private UUID ownerUuid;
    private String ownerName = "";

    // Item filters: slot -> allowed item identifier (empty = any)
    private final Map<Integer, String> slotFilters = new HashMap<>();

    // Stock tracking for nation economy
    private final Map<String, Integer> stockLevels = new HashMap<>();
    private long lastStockUpdate = 0;

    public WarehouseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Attempts to insert an item stack into the warehouse.
     * Respects slot filters and stacking rules.
     */
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

    /**
     * Extracts items from the warehouse matching the given item.
     */
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

    /**
     * Counts how many of a specific item are stored.
     */
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

    /**
     * Updates the stock level cache for economy integration.
     */
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

    public SimpleInventory getInventory() { return inventory; }

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

        // Filters
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
}
