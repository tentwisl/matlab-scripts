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

        VillageManager villageManager = VillageManager.get(world);
        List<Village> villages = new ArrayList<>();
        for (Village v : villageManager) {
            villages.add(v);
        }

        if (villages.isEmpty()) {
            // No villages yet — will be retried next tick until villages exist
            return;
        }

        // Shuffle villages so AI nations get spread out
        Collections.shuffle(villages, new Random(world.getSeed()));

        NationPersonality[] personalities = NationPersonality.values();
        int created = 0;

        for (int i = 0; i < Math.min(AI_NATION_COUNT, villages.size()) && created < AI_NATION_COUNT; i++) {
            Village capital = villages.get(i);
            NationPersonality personality = personalities[i % personalities.length];
            GovernmentType gov = personality.preferredGovernment();

            UUID nationId = UUID.randomUUID();
            // AI nations get a synthetic NPC UUID as founder
            UUID founderUUID = UUID.randomUUID();

            String name  = NATION_NAMES[created % NATION_NAMES.length];
            String color = FLAG_COLORS[created % FLAG_COLORS.length];

            Nation nation = new Nation(nationId, name, founderUUID, gov);
            nation.setPersonality(personality);
            nation.setAiControlled(true);
            nation.setFlagColorHex(color);
            nation.setCapitalCityVillageId(capital.getId());
            nation.setFoundedDay(world.getTime() / 24000L);
            // Start with modest stats
            nation.getStats().setMilitaryStrength(5 + (int)(Math.random() * 10));
            nation.getStats().setEconomicOutput(10 + (int)(Math.random() * 20));
            nation.getStats().setApprovalRating(50 + (int)(Math.random() * 30));

            nationManager.addNation(nation);

            // Mark capital city as owned by this nation
            CityData cityData = nationManager.getOrCreateCity(capital.getId());
            cityData.setOwningNationId(nationId);
            cityData.setTier(CityTier.VILLAGE);
            nationManager.saveCity(cityData);

            created++;
        }

        // Set up initial diplomacy relations between AI nations (start neutral)
        List<Nation> aiNations = new ArrayList<>(nationManager.getAllNations());
        for (int a = 0; a < aiNations.size(); a++) {
            for (int b = a + 1; b < aiNations.size(); b++) {
                UUID idA = aiNations.get(a).getNationId();
                UUID idB = aiNations.get(b).getNationId();
                DiplomacyRelation rel = nationManager.getOrCreateRelation(idA, idB);
                // Aggressive + Militarist nations start slightly hostile to each other
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
