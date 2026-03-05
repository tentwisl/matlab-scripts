package net.mca.entity.interaction.dynamicdialogue;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum DialogueSubtype {
    GREET_FRIENDLY(MainDialogueCategory.GREET, "Friendly", 1),
    GREET_FORMAL(MainDialogueCategory.GREET, "Formal", 0),
    GREET_CASUAL(MainDialogueCategory.GREET, "Casual", 1),
    GREET_BOLD(MainDialogueCategory.GREET, "Bold", -1),

    JOKE_CHEESY(MainDialogueCategory.JOKE, "Cheesy", 1),
    JOKE_DARK(MainDialogueCategory.JOKE, "Dark", -2),
    JOKE_SELF_DEPRECATING(MainDialogueCategory.JOKE, "Self-Deprecating", 0),
    JOKE_META(MainDialogueCategory.JOKE, "Meta", 1),

    STORY_HEROIC(MainDialogueCategory.STORY, "Heroic", 1),
    STORY_TRAGIC(MainDialogueCategory.STORY, "Tragic", -1),
    STORY_HUMOROUS(MainDialogueCategory.STORY, "Humorous", 1),
    STORY_MYSTERIOUS(MainDialogueCategory.STORY, "Mysterious", 0),

    ROMANCE_SWEET(MainDialogueCategory.ROMANCE, "Sweet", 2),
    ROMANCE_CHEESY(MainDialogueCategory.ROMANCE, "Cheesy", 1),
    ROMANCE_BOLD(MainDialogueCategory.ROMANCE, "Bold", -1),
    ROMANCE_SUGGESTIVE_ACTION(MainDialogueCategory.ROMANCE, "Suggestive Action", -2),

    CHAT_WORK(MainDialogueCategory.CHAT, "Work", 1),
    CHAT_VILLAGE(MainDialogueCategory.CHAT, "Village", 1),
    CHAT_WEATHER(MainDialogueCategory.CHAT, "Weather", 0),
    CHAT_PERSONAL(MainDialogueCategory.CHAT, "Personal", 0),

    RUMORS_SPOOKY(MainDialogueCategory.RUMORS, "Spooky", 0),
    RUMORS_DRAMA(MainDialogueCategory.RUMORS, "Drama", -1),
    RUMORS_TREASURE(MainDialogueCategory.RUMORS, "Treasure", 1),
    RUMORS_WARNING(MainDialogueCategory.RUMORS, "Warning", 0);

    private final MainDialogueCategory category;
    private final String displayName;
    private final int baseScore;

    DialogueSubtype(MainDialogueCategory category, String displayName, int baseScore) {
        this.category = category;
        this.displayName = displayName;
        this.baseScore = baseScore;
    }

    public MainDialogueCategory getCategory() {
        return category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getBaseScore() {
        return baseScore;
    }

    public boolean isRomanceSubtype() {
        return category == MainDialogueCategory.ROMANCE;
    }

    public boolean isBoldRomance() {
        return this == ROMANCE_BOLD || this == ROMANCE_SUGGESTIVE_ACTION;
    }

    public static List<DialogueSubtype> forCategory(MainDialogueCategory category) {
        return Stream.of(values())
                .filter(v -> v.category == category)
                .collect(Collectors.toList());
    }
}
