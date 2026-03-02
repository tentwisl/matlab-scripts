package net.mca.nation;

public enum CityTier {
    HAMLET(5),
    VILLAGE(15),
    TOWN(30),
    CITY(60),
    METROPOLIS(120);

    /** Minimum population to reach this tier */
    public final int minPopulation;

    CityTier(int minPopulation) {
        this.minPopulation = minPopulation;
    }

    public static CityTier fromPopulation(int pop) {
        CityTier result = HAMLET;
        for (CityTier t : values()) {
            if (pop >= t.minPopulation) result = t;
        }
        return result;
    }

    /** Number of market trade slots available at this tier */
    public int marketSlots() {
        return ordinal() + 2; // 2 for hamlet, up to 6 for metropolis
    }
}
