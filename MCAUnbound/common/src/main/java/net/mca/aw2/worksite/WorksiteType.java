package net.mca.aw2.worksite;

import net.mca.aw2.torque.TorqueTier;

/**
 * All worksite types available in the AW2 integration.
 * Each type defines what kind of automated work the station performs,
 * how much torque it consumes, and how many workers it supports.
 */
public enum WorksiteType {
    CROP_FARM("Crop Farm", 1.0, 3, TorqueTier.LIGHT, WorkCategory.FARMING),
    ANIMAL_FARM("Animal Farm", 1.5, 2, TorqueTier.LIGHT, WorkCategory.FARMING),
    TREE_FARM("Tree Farm", 2.0, 2, TorqueTier.LIGHT, WorkCategory.FORESTRY),
    QUARRY("Quarry", 3.0, 4, TorqueTier.MEDIUM, WorkCategory.MINING),
    FISH_FARM("Fish Farm", 1.0, 2, TorqueTier.LIGHT, WorkCategory.FARMING),
    AUTO_CRAFTING("Auto Crafting", 2.0, 1, TorqueTier.MEDIUM, WorkCategory.CRAFTING),
    ORE_PROCESSOR("Ore Processor", 4.0, 1, TorqueTier.HEAVY, WorkCategory.MINING);

    private final String displayName;
    private final double torqueCostPerWork;
    private final int maxWorkers;
    private final TorqueTier requiredTier;
    private final WorkCategory category;

    WorksiteType(String displayName, double torqueCostPerWork, int maxWorkers,
                 TorqueTier requiredTier, WorkCategory category) {
        this.displayName = displayName;
        this.torqueCostPerWork = torqueCostPerWork;
        this.maxWorkers = maxWorkers;
        this.requiredTier = requiredTier;
        this.category = category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getTorqueCostPerWork() {
        return torqueCostPerWork;
    }

    public int getMaxWorkers() {
        return maxWorkers;
    }

    public TorqueTier getRequiredTier() {
        return requiredTier;
    }

    public WorkCategory getCategory() {
        return category;
    }

    public enum WorkCategory {
        FARMING,
        FORESTRY,
        MINING,
        CRAFTING
    }
}
