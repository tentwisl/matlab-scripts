package net.mca.resources;

import java.util.Locale;

public enum Rank {
    // Tier 1: default spawn, no requirements
    WANDERER,
    // Tier 2: met 5 villagers, total reputation >= 0
    OUTSIDER,
    // Tier 3: recognized home (bed + door), reputation >= 100 in one village
    SETTLER,
    // Tier 4: reputation >= 300, at least 1 worker NPC employed
    FREEMAN,
    // Tier 5: active trade route (Courier between 2 towns), reputation >= 600
    MERCHANT,
    // Tier 6: 3+ trader NPCs, warehouse built, reputation >= 900
    GUILDMASTER,
    // Tier 7: controlled village pop >= 15, barracks built, reputation >= 1400
    MAGISTRATE,
    // Tier 8: pop >= 30, library + armory + engineering table, reputation >= 2000
    MAYOR,
    // Tier 9: 2+ towns under influence, 10+ military NPCs, District Charter crafted
    NOBLE,
    // Tier 10: 4+ towns, 25+ military, 3 automation machines operational
    DUKE,
    // Tier 11: 8+ towns, won at least one military conflict, Capital City designated
    ARCHDUKE,
    // Tier 12: nation formally founded, recognized by >= 1 rival nation
    MONARCH,
    // Tier 13: controls 3+ regions, 2+ rivals subjugated or allied, Council of Nations seat
    EMPEROR;

    private static final Rank[] VALUES = values();

    public Rank promote() {
        if (ordinal() + 1 < VALUES.length) {
            return VALUES[ordinal() + 1];
        } else {
            return Rank.EMPEROR;
        }
    }

    public Rank degrade() {
        if (ordinal() - 1 >= 0) {
            return VALUES[ordinal() - 1];
        } else {
            return null;
        }
    }

    public static Rank fromName(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ignored) {
        }
        // Legacy name mapping for existing saves
        return switch (name.toUpperCase(Locale.ENGLISH)) {
            case "OUTLAW"   -> WANDERER;
            case "PEASANT"  -> OUTSIDER;
            case "NOBLE"    -> NOBLE;
            case "MAYOR"    -> MAYOR;
            case "MONARCH"  -> MONARCH;
            default         -> OUTSIDER;
        };
    }

    public boolean isAtLeast(Rank r) {
        return ordinal() >= r.ordinal();
    }

    public String getTranslationKey() {
        return "gui.village.rank." + name().toLowerCase(Locale.ENGLISH);
    }
}
