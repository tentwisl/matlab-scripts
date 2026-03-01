package net.mca.nation;

public enum GovernmentType {
    /** Single absolute ruler; no elections; player appoints all roles */
    MONARCHY,
    /** Elected president + elected congress + appointed cabinet */
    REPUBLIC,
    /** Council of nobles/merchants; player chairs council; no popular elections */
    OLIGARCHY,
    /** Religious leader; faith score determines authority; priests hold cabinet roles */
    THEOCRACY,
    /** Council of chiefs; each district governor has equal voting weight */
    TRIBAL;

    public String getDisplayName() {
        return switch (this) {
            case MONARCHY   -> "Monarchy";
            case REPUBLIC   -> "Republic";
            case OLIGARCHY  -> "Oligarchy";
            case THEOCRACY  -> "Theocracy";
            case TRIBAL     -> "Tribal Confederation";
        };
    }

    public String getLeaderTitle() {
        return switch (this) {
            case MONARCHY   -> "King/Queen";
            case REPUBLIC   -> "President";
            case OLIGARCHY  -> "Council Chair";
            case THEOCRACY  -> "High Priest";
            case TRIBAL     -> "High Chief";
        };
    }

    public boolean hasElections() {
        return this == REPUBLIC;
    }

    public boolean requiresCongressForWar() {
        return this == REPUBLIC || this == TRIBAL;
    }
}
