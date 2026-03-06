package net.mca.entity.interaction.dynamicdialogue;

import net.mca.Config;

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
                                                         int alternatingCount,
                                                         int interactionFatigue,
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
            if (result.conditions != null) {
                for (DialogueJsonManager.JsonCondition condition : result.conditions) {
                    if (matchesCondition(condition, currentHearts, npcJob, trait, mood)) {
                        weight += condition.chance;
                    }
                }
            }

            if (result.applyFatigue && interactionFatigue > 0) {
                weight -= (int) (interactionFatigue * Config.getInstance().interactionChanceFatigue);
            }

            if (weight > 0) {
                validResults.add(new WeightedResult(result, weight));
            }
        }

        if (validResults.isEmpty()) {
            return new JsonEvaluationResult(0, ReactionType.NEUTRAL, "...", false);
        }

        DialogueJsonManager.JsonResult chosen = pickWeighted(validResults, random);
        int base = Math.max(0, chosen.actions == null ? 0 : chosen.actions.positive)
                - Math.max(0, chosen.actions == null ? 0 : chosen.actions.negative);

        String cat = category == null ? "" : category.toLowerCase(Locale.ENGLISH);
        String sub = subCategory.id == null ? "" : subCategory.id.toLowerCase(Locale.ENGLISH);

        int rawScore = base
                + getTraitModifier(cat, sub, trait)
                + getHeartModifier(cat, sub, currentHearts)
                + getJobModifier(cat, sub, npcJob);

        double moodScaled = rawScore * getMoodMultiplier(rawScore, mood);
        int finalScore = (int) Math.round(moodScaled);

        if (interactionFatigue >= 6) {
            finalScore = Math.min(finalScore, 1);
        }
        if (interactionFatigue >= 10 && finalScore > 0) {
            finalScore = 0;
        }
        if (interactionFatigue >= 14 && finalScore >= 0) {
            finalScore = -1;
        }

        String currentKey = (category + ":" + subCategory.id).toLowerCase(Locale.ENGLISH);
        boolean jobInfluenced = getJobModifier(cat, sub, npcJob) != 0;

        if (lastUsedKey != null && lastUsedKey.equalsIgnoreCase(currentKey)) {
            if (repetitionCount >= 2) {
                finalScore = Math.min(-3, finalScore - 4);
            } else {
                finalScore = Math.min(0, finalScore);
            }
            return new JsonEvaluationResult(finalScore,
                    ReactionType.REPETITIVE,
                    pickNpcResponse(chosen, random, "You're repeating yourself."),
                    jobInfluenced);
        }

        if (alternatingCount >= 2) {
            finalScore = Math.min(-2, finalScore - 3);
            return new JsonEvaluationResult(finalScore,
                    ReactionType.REPETITIVE,
                    pickNpcResponse(chosen, random, "You keep bouncing between the same lines."),
                    jobInfluenced);
        }

        if (interactionFatigue >= 16 && finalScore > -3) {
            finalScore = -3;
            return new JsonEvaluationResult(finalScore,
                    ReactionType.NEGATIVE,
                    pickNpcResponse(chosen, random, "I'm tired of talking right now."),
                    jobInfluenced);
        }

        return new JsonEvaluationResult(finalScore,
                toReactionType(finalScore),
                pickNpcResponse(chosen, random, "..."),
                jobInfluenced);
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

    /**
     * Trait modifiers now match on exact category and subId
     * rather than doing fuzzy key.contains() matching.
     */
    private static int getTraitModifier(String cat, String sub, NpcTrait trait) {
        return switch (trait) {
            case JOVIAL -> {
                // Loves jokes and chat — bonus on humor, friendly banter
                if (cat.equals("joke") || cat.equals("chat")) yield 2;
                if (cat.equals("greet") && sub.equals("friendly")) yield 1;
                if (cat.equals("play")) yield 2;
                yield 0;
            }
            case SERIOUS -> {
                // Respects formality, dislikes jokes and silliness
                if (sub.equals("formal")) yield 2;
                if (cat.equals("joke")) yield -1;
                if (cat.equals("play")) yield -1;
                if (cat.equals("ask") && sub.equals("favor")) yield 1;
                yield 0;
            }
            case GRUMPY -> {
                // Dislikes romance, bold approaches, and play
                if (cat.equals("romance")) yield -2;
                if (sub.equals("bold")) yield -2;
                if (cat.equals("play")) yield -2;
                if (cat.equals("chat") && sub.equals("personal")) yield -1;
                if (cat.equals("greet") && sub.equals("casual")) yield -1;
                yield 0;
            }
            case FLIRTATIOUS -> {
                // Big bonus on romance, mild bonus on bold greetings
                if (cat.equals("romance")) yield 3;
                if (sub.equals("bold") || sub.equals("suggestive_action")) yield 2;
                if (cat.equals("greet") && sub.equals("bold")) yield 1;
                yield 0;
            }
            case SHY -> {
                // Hates bold/suggestive, but appreciates gentle approaches
                if (sub.equals("bold") || sub.equals("suggestive_action")) yield -3;
                if (cat.equals("romance") && sub.equals("sweet")) yield 2;
                if (cat.equals("chat") && sub.equals("personal")) yield 1;
                if (cat.equals("greet") && sub.equals("friendly")) yield 1;
                yield 0;
            }
            case ODD -> {
                // Unpredictable: likes weird stuff, dislikes boring formality
                if (sub.equals("formal")) yield -2;
                if (cat.equals("joke") && sub.equals("meta")) yield 3;
                if (cat.equals("joke") && sub.equals("dark")) yield 2;
                if (cat.equals("story") && sub.equals("mysterious")) yield 2;
                if (cat.equals("rumors") && sub.equals("spooky")) yield 2;
                yield 0;
            }
            case LAZY -> {
                // Disengaged — dislikes being asked things, likes easy chat
                if (cat.equals("ask")) yield -2;
                if (cat.equals("chat") && sub.equals("weather")) yield 1;
                if (cat.equals("story")) yield -1;
                if (sub.equals("formal")) yield -1;
                yield 0;
            }
            case PEPPY -> {
                // Energetic — loves play, stories, bold greetings
                if (cat.equals("play")) yield 3;
                if (cat.equals("story") && sub.equals("heroic")) yield 2;
                if (cat.equals("greet") && sub.equals("bold")) yield 1;
                if (cat.equals("joke")) yield 1;
                if (cat.equals("chat") && sub.equals("personal")) yield -1;
                yield 0;
            }
            case GREEDY -> {
                // Motivated by profit — loves money talk, dislikes favors
                if (cat.equals("ask") && sub.equals("money")) yield -2;
                if (cat.equals("ask") && sub.equals("favor")) yield -3;
                if (cat.equals("rumors") && sub.equals("treasure")) yield 3;
                if (cat.equals("chat") && sub.equals("work")) yield 1;
                yield 0;
            }
            case NORMAL -> 0;
        };
    }

    /**
     * Heart-based modifiers using exact category/sub matching.
     */
    private static int getHeartModifier(String cat, String sub, int hearts) {
        boolean isRomance = cat.equals("romance");
        boolean isBold = sub.equals("bold") || sub.equals("suggestive_action");
        boolean isAsk = cat.equals("ask");

        if (isRomance) {
            if (hearts < 20) return isBold ? -5 : -2;
            if (hearts > 75) return isBold ? 4 : 2;
            return isBold ? -1 : 1;
        }

        if (isAsk) {
            // Asking for things requires relationship
            if (sub.equals("money") || sub.equals("favor")) {
                if (hearts < 30) return -3;
                if (hearts > 80) return 3;
                return 0;
            }
            return hearts > 50 ? 1 : 0;
        }

        return hearts > 75 ? 1 : 0;
    }

    /**
     * Job-based modifiers using exact category/sub matching.
     * Covers all MCA and vanilla professions.
     */
    private static int getJobModifier(String cat, String sub, NpcJob npcJob) {
        return switch (npcJob) {
            case VILLAGE_LEADER -> {
                if (sub.equals("formal")) yield 2;
                if (sub.equals("casual")) yield -2;
                if (cat.equals("ask") && sub.equals("favor")) yield 2;
                if (cat.equals("rumors") && sub.equals("drama")) yield 1;
                yield 0;
            }
            case GUARD, ARCHER -> {
                if (cat.equals("rumors") && sub.equals("warning")) yield 2;
                if (cat.equals("story") && sub.equals("heroic")) yield 2;
                if (cat.equals("chat") && sub.equals("village")) yield 1;
                if (cat.equals("joke") && sub.equals("dark")) yield 1;
                yield 0;
            }
            case FARMER -> {
                if (cat.equals("chat") && sub.equals("weather")) yield 2;
                if (cat.equals("chat") && sub.equals("village")) yield 2;
                if (cat.equals("chat") && sub.equals("work")) yield 1;
                if (cat.equals("rumors") && sub.equals("treasure")) yield 1;
                yield 0;
            }
            case LEATHERWORKER, BUTCHER, MASON, SHEPHERD -> {
                if (cat.equals("chat") && sub.equals("work")) yield 2;
                if (cat.equals("chat") && sub.equals("village")) yield 1;
                yield 0;
            }
            case LIBRARIAN, CARTOGRAPHER -> {
                if (cat.equals("story")) yield 2;
                if (cat.equals("rumors") && sub.equals("treasure")) yield 2;
                if (cat.equals("chat") && sub.equals("work")) yield 1;
                if (cat.equals("joke") && sub.equals("meta")) yield 1;
                yield 0;
            }
            case CLERIC -> {
                if (cat.equals("story") && sub.equals("mysterious")) yield 2;
                if (cat.equals("rumors") && sub.equals("spooky")) yield 2;
                if (sub.equals("formal")) yield 1;
                if (cat.equals("joke") && sub.equals("dark")) yield -1;
                yield 0;
            }
            case ARMORER, WEAPONSMITH, TOOLSMITH -> {
                if (cat.equals("chat") && sub.equals("work")) yield 2;
                if (cat.equals("story") && sub.equals("heroic")) yield 1;
                if (cat.equals("ask") && sub.equals("task")) yield 1;
                yield 0;
            }
            case FISHERMAN -> {
                if (cat.equals("chat") && sub.equals("weather")) yield 2;
                if (cat.equals("story") && sub.equals("humorous")) yield 1;
                if (cat.equals("chat") && sub.equals("work")) yield 1;
                yield 0;
            }
            case FLETCHER -> {
                if (cat.equals("chat") && sub.equals("work")) yield 1;
                if (cat.equals("story") && sub.equals("heroic")) yield 1;
                yield 0;
            }
            case ADVENTURER -> {
                if (cat.equals("story")) yield 2;
                if (cat.equals("rumors")) yield 2;
                if (cat.equals("greet") && sub.equals("bold")) yield 1;
                if (cat.equals("ask") && sub.equals("task")) yield 2;
                yield 0;
            }
            case MERCENARY -> {
                if (cat.equals("ask") && sub.equals("money")) yield 2;
                if (cat.equals("story") && sub.equals("heroic")) yield 1;
                if (cat.equals("greet") && sub.equals("bold")) yield 1;
                if (cat.equals("romance")) yield -1;
                yield 0;
            }
            case OUTLAW -> {
                if (cat.equals("rumors")) yield 2;
                if (cat.equals("joke") && sub.equals("dark")) yield 2;
                if (sub.equals("formal")) yield -2;
                if (cat.equals("ask") && sub.equals("favor")) yield -2;
                yield 0;
            }
            case CULTIST -> {
                if (cat.equals("story") && sub.equals("mysterious")) yield 3;
                if (cat.equals("rumors") && sub.equals("spooky")) yield 3;
                if (cat.equals("joke")) yield -1;
                if (sub.equals("casual")) yield -1;
                yield 0;
            }
            case NITWIT -> {
                if (cat.equals("joke")) yield 2;
                if (cat.equals("play")) yield 2;
                if (sub.equals("formal")) yield -2;
                if (cat.equals("ask")) yield -2;
                yield 0;
            }
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
