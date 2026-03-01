package net.mca.nation;

public enum NationPersonality {
    AGGRESSIVE,
    EXPANSIONIST,
    MILITARIST,
    MERCANTILE,
    ISOLATIONIST,
    DIPLOMATIC,
    SCIENTIFIC,
    CULTURAL;

    /** How likely (0-100) this personality is to declare war unprovoked */
    public int warTendency() {
        return switch (this) {
            case AGGRESSIVE    -> 80;
            case EXPANSIONIST  -> 60;
            case MILITARIST    -> 70;
            case MERCANTILE    -> 15;
            case ISOLATIONIST  -> 5;
            case DIPLOMATIC    -> 20;
            case SCIENTIFIC    -> 25;
            case CULTURAL      -> 10;
        };
    }

    /** Preferred GovernmentType for AI nations with this personality */
    public GovernmentType preferredGovernment() {
        return switch (this) {
            case AGGRESSIVE, MILITARIST -> GovernmentType.MONARCHY;
            case EXPANSIONIST           -> GovernmentType.MONARCHY;
            case MERCANTILE             -> GovernmentType.OLIGARCHY;
            case ISOLATIONIST           -> GovernmentType.TRIBAL;
            case DIPLOMATIC             -> GovernmentType.REPUBLIC;
            case SCIENTIFIC             -> GovernmentType.REPUBLIC;
            case CULTURAL               -> GovernmentType.THEOCRACY;
        };
    }
}
