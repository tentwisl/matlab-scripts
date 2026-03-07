package net.mca.aw2.torque;

/**
 * Torque power tiers matching AW2's tiered energy system.
 * Higher tiers can store and transfer more energy per tick.
 */
public enum TorqueTier {
    LIGHT(32, 4, "Light"),
    MEDIUM(128, 16, "Medium"),
    HEAVY(512, 64, "Heavy");

    private final double maxStorage;
    private final double maxTransfer;
    private final String displayName;

    TorqueTier(double maxStorage, double maxTransfer, String displayName) {
        this.maxStorage = maxStorage;
        this.maxTransfer = maxTransfer;
        this.displayName = displayName;
    }

    public double getMaxStorage() {
        return maxStorage;
    }

    public double getMaxTransfer() {
        return maxTransfer;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TorqueCell createCell() {
        return new TorqueCell(maxStorage, maxTransfer, maxTransfer, 1.0);
    }
}
