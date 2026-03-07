package net.mca.aw2.worksite;

import net.mca.aw2.AW2ColonyManager;
import net.mca.aw2.AW2Integration;
import net.mca.aw2.torque.ITorqueProvider;
import net.mca.aw2.torque.TorqueCell;
import net.mca.aw2.torque.TorqueTier;
import net.mca.aw2.worker.WorkerAssignment;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.mca.server.world.data.VillageManager;

import java.util.*;

/**
 * Base block entity for all AW2 worksites (farms, quarries, etc.).
 * Ported from AW2's TileWorksiteBase, adapted for 1.20.1 Architectury patterns.
 *
 * Worksites can be powered by:
 * 1. MCA villager workers assigned to the site (manual labor)
 * 2. Torque energy from generators (automated)
 * 3. Both simultaneously for maximum efficiency
 *
 * The key integration with MCA: instead of AW2's custom NPC entities,
 * worksites accept MCA VillagerEntityMCA assignments. Villagers assigned
 * as workers will path to this worksite and perform work ticks.
 */
public abstract class WorksiteBlockEntity extends BlockEntity implements ITorqueProvider {
    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_SLOTS = 9;
    public static final double ENERGY_PER_WORK_UNIT = 50.0;
    private static final int WORK_RETRY_DELAY_TICKS = 20;

    protected final WorksiteType worksiteType;
    protected final TorqueCell torqueCell;
    protected final SimpleInventory inputInventory;
    protected final SimpleInventory outputInventory;

    private final Set<WorksiteUpgrade> upgrades = EnumSet.noneOf(WorksiteUpgrade.class);
    private final List<WorkerAssignment> assignedWorkers = new ArrayList<>();

    private UUID ownerUuid;
    private String ownerName = "";

    // Work area bounds
    protected BlockPos boundsMin;
    protected BlockPos boundsMax;

    // Work state
    private int workRetryDelay = 0;
    private boolean isActive = false;
    private int totalWorkDone = 0;

    // Production tracking for nation economy integration
    private final Map<String, Integer> productionLog = new HashMap<>();
    private long lastProductionLogReset = 0;

    public WorksiteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, WorksiteType worksiteType) {
        super(type, pos, state);
        this.worksiteType = worksiteType;
        this.torqueCell = worksiteType.getRequiredTier().createCell();
        this.inputInventory = new SimpleInventory(INPUT_SLOTS);
        this.outputInventory = new SimpleInventory(OUTPUT_SLOTS);
        this.boundsMin = pos.add(-8, -1, -8);
        this.boundsMax = pos.add(8, 3, 8);
    }

    // ==================== TICK LOGIC ====================

    public static void tick(World world, BlockPos pos, BlockState state, WorksiteBlockEntity be) {
        if (world.isClient()) return;

        be.absorbNeighborTorque(world, pos);

        if (be.workRetryDelay > 0) {
            be.workRetryDelay--;
            return;
        }

        boolean hasPower = be.hasSufficientPower();
        boolean hasWorkers = !be.assignedWorkers.isEmpty();

        if (!hasPower && !hasWorkers) {
            be.isActive = false;
            return;
        }

        boolean didWork = be.attemptWork(world, pos);

        if (didWork) {
            be.isActive = true;
            be.totalWorkDone++;

            if (hasPower) {
                be.torqueCell.drainEnergy(be.worksiteType.getTorqueCostPerWork());
            }
        } else {
            be.workRetryDelay = WORK_RETRY_DELAY_TICKS;
            be.isActive = false;
        }

        // Reset production log daily (24000 ticks)
        if (world.getTime() - be.lastProductionLogReset >= 24000) {
            be.productionLog.clear();
            be.lastProductionLogReset = world.getTime();
        }
    }

    /**
     * Subclasses implement this to perform their specific work action.
     * Returns true if work was done this tick.
     */
    protected abstract boolean attemptWork(World world, BlockPos pos);

    /**
     * Returns the list of items this worksite can produce.
     * Used by the nation economy system to forecast production.
     */
    public abstract List<ItemStack> getPossibleOutputs();

    // ==================== TORQUE HANDLING ====================

    private void absorbNeighborTorque(World world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockEntity neighbor = world.getBlockEntity(neighborPos);
            if (neighbor instanceof ITorqueProvider provider) {
                if (provider.canOutputTorque(dir.getOpposite())) {
                    double available = provider.drainTorque(dir.getOpposite(),
                            torqueCell.getMaxEnergy() - torqueCell.getStoredEnergy());
                    if (available > 0) {
                        torqueCell.addEnergy(available);
                    }
                }
            }
        }
    }

    protected boolean hasSufficientPower() {
        return torqueCell.hasEnergy(worksiteType.getTorqueCostPerWork());
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
        return 0; // Worksites don't output torque
    }

    @Override
    public boolean canOutputTorque(Direction side) {
        return false;
    }

    @Override
    public boolean canInputTorque(Direction side) {
        return true;
    }

    @Override
    public TorqueTier getTorqueTier() {
        return worksiteType.getRequiredTier();
    }

    // ==================== WORKER MANAGEMENT ====================

    public boolean assignWorker(UUID villagerUuid, String villagerName) {
        if (assignedWorkers.size() >= worksiteType.getMaxWorkers()) {
            return false;
        }
        for (WorkerAssignment a : assignedWorkers) {
            if (a.getVillagerUuid().equals(villagerUuid)) {
                return false; // Already assigned
            }
        }
        assignedWorkers.add(new WorkerAssignment(villagerUuid, villagerName, pos));
        markDirty();
        return true;
    }

    public boolean removeWorker(UUID villagerUuid) {
        boolean removed = assignedWorkers.removeIf(a -> a.getVillagerUuid().equals(villagerUuid));
        if (removed) markDirty();
        return removed;
    }

    public List<WorkerAssignment> getAssignedWorkers() {
        return Collections.unmodifiableList(assignedWorkers);
    }

    public int getWorkerCount() {
        return assignedWorkers.size();
    }

    /**
     * Called by a worker villager when they arrive at this worksite and perform work.
     * Provides a manual-labor torque boost equivalent to partial generator output.
     */
    public void onWorkerTick(UUID workerUuid) {
        onWorkerTick(workerUuid, true);
    }

    public void onWorkerTick(UUID workerUuid, boolean isFed) {
        double foodMultiplier = isFed ? 1.0 : 0.35;
        double structureMultiplier = getStructureTierMultiplier();
        double workerContribution = worksiteType.getTorqueCostPerWork() * 0.5 * foodMultiplier * structureMultiplier;
        torqueCell.addEnergy(workerContribution);

        if (!isFed && world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            AW2ColonyManager.get(serverWorld).setWorkerStatus(workerUuid,
                    AW2ColonyManager.WorkerState.STARVING,
                    AW2ColonyManager.WorkerBlockedReason.NO_VILLAGE_FOOD,
                    pos,
                    serverWorld.getTime());
        }
    }

    // ==================== UPGRADE MANAGEMENT ====================

    public boolean addUpgrade(WorksiteUpgrade upgrade) {
        boolean added = upgrades.add(upgrade);
        if (added) {
            applyUpgradeEffects();
            markDirty();
        }
        return added;
    }

    public boolean hasUpgrade(WorksiteUpgrade upgrade) {
        return upgrades.contains(upgrade);
    }

    public Set<WorksiteUpgrade> getUpgrades() {
        return Collections.unmodifiableSet(upgrades);
    }

    protected void applyUpgradeEffects() {
        int baseRadius = 8;
        if (hasUpgrade(WorksiteUpgrade.SIZE_MEDIUM)) baseRadius = 12;
        if (hasUpgrade(WorksiteUpgrade.SIZE_LARGE)) baseRadius = 16;
        boundsMin = pos.add(-baseRadius, -1, -baseRadius);
        boundsMax = pos.add(baseRadius, 3, baseRadius);
    }

    protected double getEfficiencyMultiplier() {
        double mult = 1.0;
        for (WorksiteUpgrade upgrade : upgrades) {
            mult *= upgrade.getEfficiencyMultiplier();
        }
        return mult;
    }


    protected double getStructureTierMultiplier() {
        if (!(world instanceof net.minecraft.server.world.ServerWorld serverWorld)) return 1.0;

        var nearestVillage = VillageManager.get(serverWorld).findNearestVillage(pos, 128);
        if (nearestVillage.isEmpty()) return 1.0;

        long completedBuildings = nearestVillage.get().getBuildings().values().stream()
                .filter(b -> b.isComplete())
                .count();

        if (completedBuildings >= 12) return 1.30;
        if (completedBuildings >= 8) return 1.20;
        if (completedBuildings >= 4) return 1.10;
        return 1.0;
    }

    public double getCurrentStructureTierMultiplier() {
        return getStructureTierMultiplier();
    }

    // ==================== PRODUCTION LOGGING ====================

    protected void logProduction(String itemId, int count) {
        productionLog.merge(itemId, count, Integer::sum);
    }

    public Map<String, Integer> getProductionLog() {
        return Collections.unmodifiableMap(productionLog);
    }

    /**
     * Returns a snapshot of production since the last collection and clears it.
     * This avoids repeatedly counting the same output between village collection ticks.
     */
    public Map<String, Integer> consumeProductionLogSnapshot() {
        if (productionLog.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Integer> snapshot = new HashMap<>(productionLog);
        productionLog.clear();
        markDirty();
        return snapshot;
    }

    // ==================== INVENTORY HELPERS ====================

    protected boolean addToOutput(ItemStack stack) {
        for (int i = 0; i < outputInventory.size(); i++) {
            ItemStack existing = outputInventory.getStack(i);
            if (existing.isEmpty()) {
                outputInventory.setStack(i, stack.copy());
                return true;
            }
            if (ItemStack.canCombine(existing, stack) &&
                    existing.getCount() + stack.getCount() <= existing.getMaxCount()) {
                existing.increment(stack.getCount());
                return true;
            }
        }
        return false;
    }

    protected ItemStack removeFromInput(int slot) {
        return inputInventory.removeStack(slot, 1);
    }

    public SimpleInventory getInputInventory() {
        return inputInventory;
    }

    public SimpleInventory getOutputInventory() {
        return outputInventory;
    }

    // ==================== OWNER ====================

    public void setOwner(UUID uuid, String name) {
        this.ownerUuid = uuid;
        this.ownerName = name;
        markDirty();
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    // ==================== ACCESSORS ====================

    public WorksiteType getWorksiteType() {
        return worksiteType;
    }

    public boolean isActive() {
        return isActive;
    }

    public int getTotalWorkDone() {
        return totalWorkDone;
    }

    public BlockPos getBoundsMin() {
        return boundsMin;
    }

    public BlockPos getBoundsMax() {
        return boundsMax;
    }

    // ==================== NBT SERIALIZATION ====================

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        torqueCell.writeNbt(nbt);

        nbt.putString("WorksiteType", worksiteType.name());
        nbt.putInt("TotalWorkDone", totalWorkDone);
        nbt.putBoolean("IsActive", isActive);
        nbt.putLong("LastProdLogReset", lastProductionLogReset);

        if (ownerUuid != null) {
            nbt.putUuid("OwnerUuid", ownerUuid);
            nbt.putString("OwnerName", ownerName);
        }

        // Bounds
        nbt.putIntArray("BoundsMin", new int[]{boundsMin.getX(), boundsMin.getY(), boundsMin.getZ()});
        nbt.putIntArray("BoundsMax", new int[]{boundsMax.getX(), boundsMax.getY(), boundsMax.getZ()});

        // Upgrades
        NbtList upgradesList = new NbtList();
        for (WorksiteUpgrade u : upgrades) {
            NbtCompound tag = new NbtCompound();
            tag.putString("Name", u.name());
            upgradesList.add(tag);
        }
        nbt.put("Upgrades", upgradesList);

        // Workers
        NbtList workersList = new NbtList();
        for (WorkerAssignment w : assignedWorkers) {
            workersList.add(w.writeNbt());
        }
        nbt.put("Workers", workersList);

        // Inventories
        NbtCompound inputNbt = new NbtCompound();
        DefaultedList<ItemStack> inputStacks = DefaultedList.ofSize(INPUT_SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < inputInventory.size(); i++) inputStacks.set(i, inputInventory.getStack(i));
        Inventories.writeNbt(inputNbt, inputStacks);
        nbt.put("InputInv", inputNbt);

        NbtCompound outputNbt = new NbtCompound();
        DefaultedList<ItemStack> outputStacks = DefaultedList.ofSize(OUTPUT_SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < outputInventory.size(); i++) outputStacks.set(i, outputInventory.getStack(i));
        Inventories.writeNbt(outputNbt, outputStacks);
        nbt.put("OutputInv", outputNbt);

        // Production log
        NbtCompound logNbt = new NbtCompound();
        for (Map.Entry<String, Integer> entry : productionLog.entrySet()) {
            logNbt.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("ProdLog", logNbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        torqueCell.readNbt(nbt);

        totalWorkDone = nbt.getInt("TotalWorkDone");
        isActive = nbt.getBoolean("IsActive");
        lastProductionLogReset = nbt.getLong("LastProdLogReset");

        if (nbt.containsUuid("OwnerUuid")) {
            ownerUuid = nbt.getUuid("OwnerUuid");
            ownerName = nbt.getString("OwnerName");
        }

        // Bounds
        if (nbt.contains("BoundsMin")) {
            int[] bmin = nbt.getIntArray("BoundsMin");
            int[] bmax = nbt.getIntArray("BoundsMax");
            if (bmin.length == 3 && bmax.length == 3) {
                boundsMin = new BlockPos(bmin[0], bmin[1], bmin[2]);
                boundsMax = new BlockPos(bmax[0], bmax[1], bmax[2]);
            }
        }

        // Upgrades
        upgrades.clear();
        NbtList upgradesList = nbt.getList("Upgrades", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < upgradesList.size(); i++) {
            String name = upgradesList.getCompound(i).getString("Name");
            try {
                upgrades.add(WorksiteUpgrade.valueOf(name));
            } catch (IllegalArgumentException ignored) {}
        }

        // Workers
        assignedWorkers.clear();
        NbtList workersList = nbt.getList("Workers", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < workersList.size(); i++) {
            assignedWorkers.add(WorkerAssignment.fromNbt(workersList.getCompound(i)));
        }

        // Inventories
        if (nbt.contains("InputInv")) {
            DefaultedList<ItemStack> inputStacks = DefaultedList.ofSize(INPUT_SLOTS, ItemStack.EMPTY);
            Inventories.readNbt(nbt.getCompound("InputInv"), inputStacks);
            for (int i = 0; i < inputStacks.size(); i++) inputInventory.setStack(i, inputStacks.get(i));
        }
        if (nbt.contains("OutputInv")) {
            DefaultedList<ItemStack> outputStacks = DefaultedList.ofSize(OUTPUT_SLOTS, ItemStack.EMPTY);
            Inventories.readNbt(nbt.getCompound("OutputInv"), outputStacks);
            for (int i = 0; i < outputStacks.size(); i++) outputInventory.setStack(i, outputStacks.get(i));
        }

        // Production log
        productionLog.clear();
        if (nbt.contains("ProdLog")) {
            NbtCompound logNbt = nbt.getCompound("ProdLog");
            for (String key : logNbt.getKeys()) {
                productionLog.put(key, logNbt.getInt(key));
            }
        }
    }
}
