package net.mca.server.world.data.politics;

import net.mca.entity.VillagerEntityMCA;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        // Trait preference placeholder bucket; future hook can map actual MCA traits/genetics.
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

        return clamp(score, 0, 100);
    }

    public static double computeNfp(PoliticalCompass compass, PolicyVector policy) {
        return clamp(policy.dot(compass), -100, 100);
    }

    /** Final_NFP_Change = Σ(policyWeight[axis] * compass[axis]). */
    public static double applyPolicyImpact(double currentNfp, PoliticalCompass compass, PolicyVector policy) {
        double delta = policy.dot(compass);
        return clamp(currentNfp + delta, -100, 100);
    }

    public static VillagerEntityMCA chooseVoteTarget(List<VillagerEntityMCA> candidates,
                                                     Map<VillagerEntityMCA, FavorProfile> profiles,
                                                     boolean proPresident) {
        if (candidates.isEmpty()) return null;

        List<VillagerEntityMCA> bracket = candidates.stream()
                .filter(c -> {
                    FavorProfile p = profiles.get(c);
                    return p != null && (proPresident ? p.nfp() >= 0 : p.nfp() < 0);
                })
                .toList();

        List<VillagerEntityMCA> pool = bracket.isEmpty() ? candidates : bracket;

        // Tie-break order: highest LFP, then highest NFP, then UUID lexical.
        return pool.stream()
                .max(Comparator
                        .comparingDouble((VillagerEntityMCA c) -> profiles.getOrDefault(c, new FavorProfile(0, 0, new PoliticalCompass(0.5, 0.5, 0.5, 0.5))).lfp())
                        .thenComparingDouble(c -> profiles.getOrDefault(c, new FavorProfile(0, 0, new PoliticalCompass(0.5, 0.5, 0.5, 0.5))).nfp())
                        .thenComparing(c -> c.getUuid().toString()))
                .orElse(candidates.get(0));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
