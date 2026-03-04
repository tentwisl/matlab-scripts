package net.mca.entity.interaction.dynamicdialogue;

public final class DialogueCalculator {

    private DialogueCalculator() {
    }

    public static InteractionResult calculate(DialogueSubtype subtype, NpcTrait trait, NpcMood mood, int currentHearts) {
        int rawScore = subtype.getBaseScore();
        rawScore += getTraitModifier(subtype, trait);
        rawScore += getHeartModifier(subtype, currentHearts);

        double scaled = rawScore * getMoodMultiplier(rawScore, mood);
        int finalScore = (int) Math.round(scaled);

        return new InteractionResult(finalScore, toReactionType(finalScore));
    }

    private static int getTraitModifier(DialogueSubtype subtype, NpcTrait trait) {
        return switch (trait) {
            case JOVIAL -> switch (subtype.getCategory()) {
                case JOKE, CHAT -> 2;
                case STORY -> 1;
                case ROMANCE -> subtype.isBoldRomance() ? -1 : 1;
                default -> 0;
            };
            case SERIOUS -> switch (subtype) {
                case JOKE_DARK, JOKE_CHEESY, JOKE_META -> -2;
                case CHAT_WORK, STORY_HEROIC, STORY_MYSTERIOUS, GREET_FORMAL -> 2;
                case ROMANCE_SUGGESTIVE_ACTION -> -2;
                default -> 0;
            };
            case GRUMPY -> switch (subtype.getCategory()) {
                case GREET, ROMANCE -> -2;
                case RUMORS, STORY -> 1;
                case CHAT -> -1;
                default -> 0;
            };
            case FLIRTATIOUS -> switch (subtype) {
                case ROMANCE_SWEET, ROMANCE_CHEESY -> 2;
                case ROMANCE_BOLD, ROMANCE_SUGGESTIVE_ACTION -> 3;
                case GREET_BOLD -> 1;
                case CHAT_PERSONAL -> 1;
                default -> 0;
            };
            case SHY -> switch (subtype) {
                case ROMANCE_BOLD, ROMANCE_SUGGESTIVE_ACTION, GREET_BOLD -> -3;
                case GREET_FRIENDLY, ROMANCE_SWEET, CHAT_WEATHER, CHAT_WORK -> 1;
                default -> 0;
            };
            case NORMAL -> 0;
        };
    }

    private static int getHeartModifier(DialogueSubtype subtype, int hearts) {
        if (!subtype.isRomanceSubtype()) {
            if (hearts < 0 && subtype.getCategory() == MainDialogueCategory.JOKE) {
                return -1;
            }
            return hearts > 75 && subtype.getCategory() == MainDialogueCategory.GREET ? 1 : 0;
        }

        if (hearts < 20) {
            return subtype.isBoldRomance() ? -5 : -2;
        }
        if (hearts > 75) {
            return subtype.isBoldRomance() ? 4 : 2;
        }
        return subtype.isBoldRomance() ? -1 : 1;
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
}
