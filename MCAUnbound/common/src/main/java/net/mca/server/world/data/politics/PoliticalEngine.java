package net.mca.server.world.data.politics;

import net.mca.entity.VillagerEntityMCA;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class PoliticalEngine {
    private PoliticalEngine() {
    }

    public static double computeLfp(VillagerEntityMCA villager, int nearbyGuardCount) {
        String personality = villager.getVillagerBrain().getPersonality().name().toLowerCase();
        double score = switch (personality) {
            case "friendly", "confident", "flirty", "witty", "peaceful" -> 45;
            case "curious", "athletic" -> 35;
            case "strong", "tough" -> 25 + Math.max(0, nearbyGuardCount);
            case "sensitive", "odd", "sleepy", "lazy" -> 15;
            case "greedy", "grumpy" -> 12;
            default -> 20;
        };

        // Placeholder trait preferences (can be wired to genetics/traits systems later)
        score += 3;

        String mood = villager.getVillagerBrain().getMood().getName().toLowerCase();
        score += switch (mood) {
            case "overjoyed" -> 20;
            case "happy" -> 15;
            case "fine" -> 10;
            case "passive" -> 8;
            case "unhappy" -> -8;
            case "sad" -> -10;
            case "depressed" -> -12;
            default -> 0;
        };

        return Math.max(0, Math.min(100, score));
    }

    public static double computeNfp(PoliticalCompass compass, PolicyVector policy) {
        return policy.dot(compass);
    }

    public static VillagerEntityMCA chooseVoteTarget(List<VillagerEntityMCA> candidates,
                                                     Map<VillagerEntityMCA, FavorProfile> profiles,
                                                     boolean proPresident) {
        return candidates.stream()
                .filter(c -> {
                    FavorProfile p = profiles.get(c);
                    return p != null && (proPresident ? p.nfp() >= 0 : p.nfp() < 0);
                })
                .max(Comparator.comparingDouble(c -> profiles.get(c).lfp()))
                .orElse(candidates.isEmpty() ? null : candidates.get(0));
    }
}
