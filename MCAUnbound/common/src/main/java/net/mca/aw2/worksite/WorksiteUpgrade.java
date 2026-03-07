package net.mca.aw2.worksite;

/**
 * Upgrades that can be applied to worksites to improve their efficiency.
 * Ported from AW2's WorksiteUpgrade system.
 */
public enum WorksiteUpgrade {
    SIZE_MEDIUM("Medium Bounds", "Increases work area to medium size", 1.5),
    SIZE_LARGE("Large Bounds", "Increases work area to large size", 2.0),
    ENCHANTED_TOOLS_1("Enchanted Tools I", "Workers use enchanted tools (+25% speed)", 1.25),
    ENCHANTED_TOOLS_2("Enchanted Tools II", "Workers use enchanted tools (+50% speed)", 1.5),
    QUARRY_MEDIUM("Quarry Depth Medium", "Quarry digs to Y=32", 1.0),
    QUARRY_LARGE("Quarry Depth Large", "Quarry digs to Y=0", 1.0),
    BASIC_CHUNK_LOADER("Basic Loader", "Keeps worksite loaded when no players are nearby", 1.0);

    private final String displayName;
    private final String description;
    private final double efficiencyMultiplier;

    WorksiteUpgrade(String displayName, String description, double efficiencyMultiplier) {
        this.displayName = displayName;
        this.description = description;
        this.efficiencyMultiplier = efficiencyMultiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public double getEfficiencyMultiplier() {
        return efficiencyMultiplier;
    }
}
