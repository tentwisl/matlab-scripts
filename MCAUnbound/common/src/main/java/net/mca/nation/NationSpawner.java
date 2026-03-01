package net.mca.nation;

import net.mca.server.world.data.VillageManager;
import net.mca.server.world.data.Village;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

/**
 * Seeds AI nations the first time a world loads.
 * Runs once and sets {@link NationManager#setAiNationsSeeded(boolean)}.
 */
public final class NationSpawner {

    /** How many AI nations to create at world start. */
    private static final int AI_NATION_COUNT = 4;

    private static final String[] NATION_NAMES = {
            "Arendaal", "Ironveil", "Goldmere", "The Ashlands",
            "Stonewatch", "Crystalport", "Embervale", "Tideholm"
    };

    private static final String[] FLAG_COLORS = {
            "3366CC", "CC3333", "33AA44", "CCAA22",
            "883399", "11AACC", "CC6611", "557788"
    };

    private NationSpawner() {}

    /**
     * Called once on server start if nations haven't been seeded yet.
     * Assigns AI nations to existing MCA villages.
     */
    public static void seedAiNations(ServerWorld world, NationManager nationManager) {
        if (nationManager.isAiNationsSeeded()) return;

        // Gather existing MCA villages (may be empty on first world load — that's fine).
        VillageManager villageManager = VillageManager.get(world);
        List<Village> villages = new ArrayList<>();
        for (Village v : villageManager) {
            villages.add(v);
        }

        // Shuffle so AI nations get spread across available villages.
        if (!villages.isEmpty()) {
            Collections.shuffle(villages, new Random(world.getSeed()));
        }

        NationPersonality[] personalities = NationPersonality.values();

        for (int i = 0; i < AI_NATION_COUNT; i++) {
            NationPersonality personality = personalities[i % personalities.length];
            GovernmentType gov = personality.preferredGovernment();

            UUID nationId   = UUID.randomUUID();
            UUID founderUUID = UUID.randomUUID(); // synthetic NPC founder

            String name  = NATION_NAMES[i % NATION_NAMES.length];
            String color = FLAG_COLORS[i % FLAG_COLORS.length];

            Nation nation = new Nation(nationId, name, founderUUID, gov);
            nation.setPersonality(personality);
            nation.setAiControlled(true);
            nation.setFlagColorHex(color);
            nation.setFoundedDay(world.getTime() / 24000L);
            // Start with modest randomised stats
            Random rng = new Random(world.getSeed() + i);
            nation.getStats().setMilitaryStrength(5  + rng.nextInt(10));
            nation.getStats().setEconomicOutput(10   + rng.nextInt(20));
            nation.getStats().setApprovalRating(50   + rng.nextInt(30));

            // Assign a capital village if one is available
            if (i < villages.size()) {
                Village capital = villages.get(i);
                nation.setCapitalCityVillageId(capital.getId());
                CityData cityData = nationManager.getOrCreateCity(capital.getId());
                cityData.setOwningNationId(nationId);
                cityData.setTier(CityTier.VILLAGE);
                nationManager.saveCity(cityData);
            }
            // If no village is available, capitalCityVillageId stays -1;
            // it will be assigned when the first suitable village is discovered.

            nationManager.addNation(nation);
        }

        // Bootstrap diplomacy relations between all AI nations
        List<Nation> aiNations = new ArrayList<>(nationManager.getAllNations());
        for (int a = 0; a < aiNations.size(); a++) {
            for (int b = a + 1; b < aiNations.size(); b++) {
                UUID idA = aiNations.get(a).getNationId();
                UUID idB = aiNations.get(b).getNationId();
                DiplomacyRelation rel = nationManager.getOrCreateRelation(idA, idB);
                NationPersonality pA = aiNations.get(a).getPersonality();
                NationPersonality pB = aiNations.get(b).getPersonality();
                if (pA.warTendency() > 50 && pB.warTendency() > 50) {
                    rel.adjustTrust(-20);
                } else if (pA == NationPersonality.DIPLOMATIC || pB == NationPersonality.DIPLOMATIC) {
                    rel.adjustTrust(10);
                }
            }
        }

        nationManager.setAiNationsSeeded(true);
    }
}
