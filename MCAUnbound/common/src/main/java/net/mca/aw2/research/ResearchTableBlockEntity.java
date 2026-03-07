package net.mca.aw2.research;

import net.mca.aw2.torque.ITorqueProvider;
import net.mca.aw2.torque.TorqueCell;
import net.mca.aw2.torque.TorqueTier;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Research Table block entity. Players or assigned Researcher NPCs work here
 * to advance the tech tree. Requires a Research Book to be placed in the book slot.
 *
 * Ported from AW2's TileResearchStation, adapted for 1.20.1 with MCA integration.
 * The research table can be powered by torque for automated research via NPCs.
 */
public class ResearchTableBlockEntity extends BlockEntity implements ITorqueProvider {
    private static final double ENERGY_PER_RESEARCH_TICK = 10.0;
    private static final int BASE_RESEARCH_RATE = 1;

    // Inventory: slot 0 = research book, slots 1-9 = resource materials
    private final SimpleInventory bookInventory = new SimpleInventory(1);
    private final SimpleInventory resourceInventory = new SimpleInventory(9);

    private final TorqueCell torqueCell;

    // Owner tracking
    private UUID ownerUuid;
    private String ownerName = "";

    // Assigned researcher NPC
    private UUID assignedResearcherUuid;
    private String assignedResearcherName = "";

    // Research state
    private Identifier currentResearchId;
    private boolean hasBook = false;

    public ResearchTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.torqueCell = TorqueTier.LIGHT.createCell();
    }

    public static void tick(World world, BlockPos pos, BlockState state, ResearchTableBlockEntity be) {
        if (world.isClient()) return;
        if (!(world instanceof ServerWorld serverWorld)) return;

        // Update book state
        be.hasBook = !be.bookInventory.getStack(0).isEmpty();

        if (!be.hasBook || be.ownerUuid == null || be.currentResearchId == null) return;

        // Check if powered (torque or worker present)
        boolean hasTorque = be.torqueCell.hasEnergy(ENERGY_PER_RESEARCH_TICK);
        boolean hasWorker = be.assignedResearcherUuid != null;

        if (!hasTorque && !hasWorker) return;

        // Advance research
        ResearchManager manager = ResearchManager.get(serverWorld);
        int rate = BASE_RESEARCH_RATE;
        if (hasWorker) rate += 1; // Researcher NPC bonus
        if (hasTorque) {
            be.torqueCell.drainEnergy(ENERGY_PER_RESEARCH_TICK);
            rate += 1; // Torque bonus
        }

        boolean completed = manager.tickResearch(be.ownerUuid, rate);
        if (completed) {
            be.currentResearchId = null;
            be.markDirty();
        }
    }

    // ==================== RESEARCH OPERATIONS ====================

    public boolean startResearch(UUID playerUuid, Identifier researchId, ServerWorld world) {
        ResearchManager manager = ResearchManager.get(world);
        if (manager.startResearch(playerUuid, researchId)) {
            this.ownerUuid = playerUuid;
            this.currentResearchId = researchId;
            markDirty();
            return true;
        }
        return false;
    }

    public void cancelResearch() {
        this.currentResearchId = null;
        markDirty();
    }

    // ==================== RESEARCHER ASSIGNMENT ====================

    public void assignResearcher(UUID npcUuid, String npcName) {
        this.assignedResearcherUuid = npcUuid;
        this.assignedResearcherName = npcName;
        markDirty();
    }

    public void removeResearcher() {
        this.assignedResearcherUuid = null;
        this.assignedResearcherName = "";
        markDirty();
    }

    public UUID getAssignedResearcherUuid() { return assignedResearcherUuid; }
    public String getAssignedResearcherName() { return assignedResearcherName; }

    // ==================== OWNER ====================

    public void setOwner(UUID uuid, String name) {
        this.ownerUuid = uuid;
        this.ownerName = name;
        markDirty();
    }

    public UUID getOwnerUuid() { return ownerUuid; }

    // ==================== INVENTORIES ====================

    public SimpleInventory getBookInventory() { return bookInventory; }
    public SimpleInventory getResourceInventory() { return resourceInventory; }
    public boolean hasBook() { return hasBook; }
    public Identifier getCurrentResearchId() { return currentResearchId; }

    // ==================== TORQUE ====================

    @Override
    public double getTorqueStored(Direction side) { return torqueCell.getStoredEnergy(); }

    @Override
    public double getMaxTorque(Direction side) { return torqueCell.getMaxEnergy(); }

    @Override
    public double addTorque(Direction side, double amount) { return torqueCell.addEnergy(amount); }

    @Override
    public double drainTorque(Direction side, double amount) { return 0; }

    @Override
    public boolean canOutputTorque(Direction side) { return false; }

    @Override
    public boolean canInputTorque(Direction side) { return true; }

    @Override
    public TorqueTier getTorqueTier() { return TorqueTier.LIGHT; }

    // ==================== NBT ====================

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        torqueCell.writeNbt(nbt);

        if (ownerUuid != null) {
            nbt.putUuid("OwnerUuid", ownerUuid);
            nbt.putString("OwnerName", ownerName);
        }
        if (assignedResearcherUuid != null) {
            nbt.putUuid("ResearcherUuid", assignedResearcherUuid);
            nbt.putString("ResearcherName", assignedResearcherName);
        }
        if (currentResearchId != null) {
            nbt.putString("CurrentResearch", currentResearchId.toString());
        }

        // Book inventory
        NbtCompound bookNbt = new NbtCompound();
        if (!bookInventory.getStack(0).isEmpty()) {
            bookInventory.getStack(0).writeNbt(bookNbt);
        }
        nbt.put("BookInv", bookNbt);

        // Resource inventory
        NbtCompound resNbt = new NbtCompound();
        for (int i = 0; i < resourceInventory.size(); i++) {
            ItemStack stack = resourceInventory.getStack(i);
            if (!stack.isEmpty()) {
                resNbt.put("Slot" + i, stack.writeNbt(new NbtCompound()));
            }
        }
        nbt.put("ResourceInv", resNbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        torqueCell.readNbt(nbt);

        if (nbt.containsUuid("OwnerUuid")) {
            ownerUuid = nbt.getUuid("OwnerUuid");
            ownerName = nbt.getString("OwnerName");
        }
        if (nbt.containsUuid("ResearcherUuid")) {
            assignedResearcherUuid = nbt.getUuid("ResearcherUuid");
            assignedResearcherName = nbt.getString("ResearcherName");
        }
        if (nbt.contains("CurrentResearch")) {
            currentResearchId = new Identifier(nbt.getString("CurrentResearch"));
        }

        if (nbt.contains("BookInv")) {
            NbtCompound bookNbt = nbt.getCompound("BookInv");
            if (!bookNbt.isEmpty()) {
                bookInventory.setStack(0, ItemStack.fromNbt(bookNbt));
            }
        }

        if (nbt.contains("ResourceInv")) {
            NbtCompound resNbt = nbt.getCompound("ResourceInv");
            for (int i = 0; i < resourceInventory.size(); i++) {
                if (resNbt.contains("Slot" + i)) {
                    resourceInventory.setStack(i, ItemStack.fromNbt(resNbt.getCompound("Slot" + i)));
                }
            }
        }
    }
}
