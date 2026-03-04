package net.mca.entity.interaction.dynamicdialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DialogueCalculator {

    private DialogueCalculator() {
    }

    public static JsonEvaluationResult calculateFromJson(String category,
                                                         DialogueJsonManager.JsonSubCategory subCategory,
                                                         NpcTrait trait,
                                                         NpcMood mood,
                                                         int currentHearts,
                                                         NpcJob npcJob,
                                                         String lastUsedKey,
                                                         int repetitionCount,
                                                         net.minecraft.util.math.random.Random random) {
        if (subCategory == null || subCategory.results == null || subCategory.results.isEmpty()) {
            return new JsonEvaluationResult(0, ReactionType.NEUTRAL, "...", false);
        }

        List<WeightedResult> validResults = new ArrayList<>();
        for (DialogueJsonManager.JsonResult result : subCategory.results) {
            if (result == null) {
                continue;
            }

            int weight = result.baseChance;
            boolean valid = true;
            if (result.conditions != null) {
                for (DialogueJsonManager.JsonCondition condition : result.conditions) {
                    if (!matchesCondition(condition, currentHearts, npcJob, trait, mood)) {
                        valid = false;
                        break;
                    }
                    weight += condition.chance;
                }
            }

            if (valid) {
                validResults.add(new WeightedResult(result, Math.max(1, weight)));
            }
        }

        if (validResults.isEmpty()) {
            return new JsonEvaluationResult(0, ReactionType.NEUTRAL, "...", false);
        }

        DialogueJsonManager.JsonResult chosen = pickWeighted(validResults, random);
        int base = Math.max(0, chosen.actions == null ? 0 : chosen.actions.positive)
                - Math.max(0, chosen.actions == null ? 0 : chosen.actions.negative);

        int rawScore = base
                + getTraitModifier(category, subCategory.id, trait)
                + getHeartModifier(category, subCategory.id, currentHearts)
                + getJobModifier(category, subCategory.id, npcJob);

        double moodScaled = rawScore * getMoodMultiplier(rawScore, mood);
        int finalScore = (int) Math.round(moodScaled);

        String currentKey = (category + ":" + subCategory.id).toLowerCase(Locale.ENGLISH);
        if (lastUsedKey != null && lastUsedKey.equalsIgnoreCase(currentKey)) {
            if (repetitionCount >= 2) {
                finalScore = Math.min(-3, finalScore - 4);
            } else {
                finalScore = Math.min(0, finalScore);
            }
            return new JsonEvaluationResult(finalScore,
                    ReactionType.REPETITIVE,
                    pickNpcResponse(chosen, random, "You're repeating yourself."),
                    getJobModifier(category, subCategory.id, npcJob) != 0);
        }

        return new JsonEvaluationResult(finalScore,
                toReactionType(finalScore),
                pickNpcResponse(chosen, random, "..."),
                getJobModifier(category, subCategory.id, npcJob) != 0);
    }

    private static boolean matchesCondition(DialogueJsonManager.JsonCondition condition,
                                            int hearts,
                                            NpcJob job,
                                            NpcTrait trait,
                                            NpcMood mood) {
        if (condition == null) {
            return true;
        }

        if (condition.hearts_min != null && hearts < condition.hearts_min) {
            return false;
        }
        if (condition.hearts_max != null && hearts > condition.hearts_max) {
            return false;
        }
        if (condition.job != null && !condition.job.equalsIgnoreCase(job.name())) {
            return false;
        }
        if (condition.trait != null && !condition.trait.equalsIgnoreCase(trait.name())) {
            return false;
        }
        if (condition.mood != null && !condition.mood.equalsIgnoreCase(mood.name())) {
            return false;
        }

        return true;
    }

    private static DialogueJsonManager.JsonResult pickWeighted(List<WeightedResult> valid,
                                                               net.minecraft.util.math.random.Random random) {
        int total = valid.stream().mapToInt(v -> v.weight).sum();
        int target = random.nextInt(Math.max(1, total));

        int running = 0;
        for (WeightedResult v : valid) {
            running += v.weight;
            if (target < running) {
                return v.result;
            }
        }
        return valid.get(0).result;
    }

    private static String pickNpcResponse(DialogueJsonManager.JsonResult result,
                                          net.minecraft.util.math.random.Random random,
                                          String fallback) {
        if (result.npcResponses == null || result.npcResponses.isEmpty()) {
            return fallback;
        }
        return result.npcResponses.get(random.nextInt(result.npcResponses.size()));
    }

    private static int getTraitModifier(String category, String subId, NpcTrait trait) {
        String key = (category + ":" + subId).toLowerCase(Locale.ENGLISH);
        return switch (trait) {
            case JOVIAL -> key.contains("joke") || key.contains("chat") ? 2 : 0;
            case SERIOUS -> key.contains("formal") ? 2 : (key.contains("joke") ? -1 : 0);
            case GRUMPY -> key.contains("romance") || key.contains("bold") ? -2 : 0;
            case FLIRTATIOUS -> key.contains("romance") ? 3 : 0;
            case SHY -> key.contains("bold") ? -3 : 1;
            case NORMAL -> 0;
        };
    }

    private static int getHeartModifier(String category, String subId, int hearts) {
        String key = (category + ":" + subId).toLowerCase(Locale.ENGLISH);
        boolean romance = key.contains("romance");
        boolean bold = key.contains("bold") || key.contains("suggestive");

        if (!romance) {
            return hearts > 75 ? 1 : 0;
        }
        if (hearts < 20) {
            return bold ? -5 : -2;
        }
        if (hearts > 75) {
            return bold ? 4 : 2;
        }
        return bold ? -1 : 1;
    }

    private static int getJobModifier(String category, String subId, NpcJob npcJob) {
        String key = (category + ":" + subId).toLowerCase(Locale.ENGLISH);
        return switch (npcJob) {
            case VILLAGE_LEADER -> key.contains("formal") ? 2 : (key.contains("casual") ? -2 : 0);
            case LEATHERWORKER -> key.contains("work") ? 1 : 0;
            case FARMER -> key.contains("weather") || key.contains("village") ? 2 : 0;
            case GUARD -> key.contains("warning") || key.contains("heroic") ? 2 : 0;
            case NONE -> 0;
        };
    }

    private static double getMoodMultiplier(int rawScore, NpcMood mood) {
        return switch (mood) {
            case HAPPY -> rawScore > 0 ? 1.5D : 1.0D;
            case ANGRY -> rawScore < 0 ? 2.0D : 0.8D;
            case SAD -> rawScore > 0 ? 0.75D : 1.25D;
            case NEUTRAL -> 1.0D;
        };
    }

    private static ReactionType toReactionType(int points) {
        if (points > 1) {
            return ReactionType.POSITIVE;
        }
        if (points < -1) {
            return ReactionType.NEGATIVE;
        }
        return ReactionType.NEUTRAL;
    }

    private record WeightedResult(DialogueJsonManager.JsonResult result, int weight) {
    }

    public record JsonEvaluationResult(int relationshipPointChange,
                                       ReactionType reactionType,
                                       String npcResponse,
                                       boolean jobInfluenced) {
    }
}
