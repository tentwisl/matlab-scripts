package net.mca.server.world.data;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * A supply request posted by the village leader.
 * Players can accept, then fulfill by bringing the requested items.
 */
public class VillageRequest {

    public enum Category { FOOD, EMERALDS, RESOURCES }

    private final int id;
    private final String itemId;
    private final Category category;
    private final int totalAmount;
    private int fulfilledAmount = 0;
    private UUID acceptedBy = null;

    public VillageRequest(int id, String itemId, Category category, int totalAmount) {
        this.id = id;
        this.itemId = itemId;
        this.category = category;
        this.totalAmount = totalAmount;
    }

    public VillageRequest(NbtCompound nbt) {
        this.id = nbt.getInt("id");
        this.itemId = nbt.getString("itemId");
        this.category = Category.values()[nbt.getInt("category")];
        this.totalAmount = nbt.getInt("total");
        this.fulfilledAmount = nbt.getInt("fulfilled");
        if (nbt.containsUuid("acceptedBy")) this.acceptedBy = nbt.getUuid("acceptedBy");
    }

    public NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("id", id);
        nbt.putString("itemId", itemId);
        nbt.putInt("category", category.ordinal());
        nbt.putInt("total", totalAmount);
        nbt.putInt("fulfilled", fulfilledAmount);
        if (acceptedBy != null) nbt.putUuid("acceptedBy", acceptedBy);
        return nbt;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public int getId()            { return id; }
    public String getItemId()     { return itemId; }
    public Category getCategory() { return category; }
    public int getTotalAmount()   { return totalAmount; }
    public int getFulfilled()     { return fulfilledAmount; }
    public int getRemaining()     { return Math.max(0, totalAmount - fulfilledAmount); }
    public boolean isAccepted()   { return acceptedBy != null; }
    public boolean isComplete()   { return fulfilledAmount >= totalAmount; }
    public UUID getAcceptedBy()   { return acceptedBy; }

    public void accept(UUID playerUUID)   { this.acceptedBy = playerUUID; }

    /**
     * Attempt to partially or fully fulfill from the player's inventory.
     * Returns how many items were actually taken.
     */
    public int tryFulfill(ServerPlayerEntity player) {
        Item target = Registries.ITEM.get(new Identifier(itemId));
        if (target == Items.AIR) return 0;

        int needed = getRemaining();
        int taken = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            var stack = player.getInventory().getStack(slot);
            if (stack.getItem() == target) {
                int take = Math.min(needed - taken, stack.getCount());
                stack.decrement(take);
                taken += take;
                if (taken >= needed) break;
            }
        }
        fulfilledAmount += taken;
        return taken;
    }

    // ── Static generator ──────────────────────────────────────────────────────

    private static final String[][] FOOD_ITEMS     = {{"minecraft:bread", "10"}, {"minecraft:carrot", "16"}, {"minecraft:potato", "16"}, {"minecraft:apple", "8"}};
    private static final String[][] EMERALD_ITEMS  = {{"minecraft:emerald", "3"}, {"minecraft:emerald", "5"}};
    private static final String[][] RESOURCE_ITEMS = {{"minecraft:oak_planks", "16"}, {"minecraft:cobblestone", "16"}, {"minecraft:iron_ingot", "8"}, {"minecraft:wool", "12"}};

    public static VillageRequest generate(int id, java.util.Random random) {
        int typeRoll = random.nextInt(3);
        String[][] pool;
        Category cat;
        if (typeRoll == 0) { pool = FOOD_ITEMS;     cat = Category.FOOD; }
        else if (typeRoll == 1) { pool = EMERALD_ITEMS;  cat = Category.EMERALDS; }
        else { pool = RESOURCE_ITEMS; cat = Category.RESOURCES; }
        String[] chosen = pool[random.nextInt(pool.length)];
        return new VillageRequest(id, chosen[0], cat, Integer.parseInt(chosen[1]));
    }
}
