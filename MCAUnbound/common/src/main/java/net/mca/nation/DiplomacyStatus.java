package net.mca.nation;

public enum DiplomacyStatus {
    AT_WAR(-2),
    HOSTILE(-1),
    NEUTRAL(0),
    FRIENDLY(1),
    TRADE_AGREEMENT(2),
    NON_AGGRESSION(2),
    ALLIANCE(3),
    VASSAL(2),
    OVERLORD(3);

    /** Rough diplomatic tier; higher = more cooperative */
    public final int tier;

    DiplomacyStatus(int tier) {
        this.tier = tier;
    }

    public boolean isAtWar() {
        return this == AT_WAR;
    }

    public boolean allowsTrade() {
        return tier >= 1;
    }

    public boolean allowsMilitaryAccess() {
        return this == ALLIANCE || this == OVERLORD || this == VASSAL;
    }
}
