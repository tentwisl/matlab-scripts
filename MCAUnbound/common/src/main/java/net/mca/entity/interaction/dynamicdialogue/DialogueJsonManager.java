package net.mca.entity.interaction.dynamicdialogue;

import com.google.gson.Gson;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.MCA;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads nested dialogue definitions from data/mca/dialogues_nested/*.json.
 *
 * Schema (example):
 * {
 *   "category": "greet",
 *   "subCategories": [
 *     {
 *       "id": "friendly",
 *       "label": "Friendly",
 *       "playerOptions": ["Hey there!", "Good to see you!"],
 *       "results": [
 *         {
 *           "baseChance": 5,
 *           "conditions": [{"chance": 4, "hearts_min": 20, "job": "farmer"}],
 *           "actions": {"positive": 2},
 *           "npcResponses": ["Hi! Great to see you."]
 *         }
 *       ]
 *     }
 *   ]
 * }
 */
public final class DialogueJsonManager {
    private static final Gson GSON = new Gson();
    private static final List<String> CATEGORIES = List.of("common", "greet", "joke", "story", "romance", "play", "chat", "rumors");
    private static final net.minecraft.util.math.random.Random RANDOM = net.minecraft.util.math.random.Random.create();

    private final Map<String, DialogueCategoryFile> categoryFiles = new HashMap<>();
    private final Map<String, DialogueCategoryFile> kidsCategoryFiles = new HashMap<>();

    public DialogueJsonManager() {
        reload();
    }

    public void reload() {
        categoryFiles.clear();
        kidsCategoryFiles.clear();
        for (String category : CATEGORIES) {
            loadCategory(category, false).ifPresent(file -> categoryFiles.put(category, file));
            loadCategory(category, true).ifPresent(file -> kidsCategoryFiles.put(category, file));
        }
    }

    public List<PlayerOption> getPlayerOptions(String category, boolean isChild, AgeState ageState) {
        DialogueCategoryFile file = getCategoryFile(category, isChild);
        if (file == null || file.subCategories == null) {
            return List.of();
        }

        List<PlayerOption> options = new ArrayList<>();
        for (JsonSubCategory sc : file.subCategories) {
            if (sc == null || sc.id == null || sc.playerOptions == null || sc.playerOptions.isEmpty() || !matchesAgeGroup(sc.age_group, ageState)) {
                continue;
            }
            String line = sc.playerOptions.get(RANDOM.nextInt(sc.playerOptions.size()));
            options.add(new PlayerOption(category, sc.id, sc.label == null ? sc.id : sc.label, line));
        }
        return options;
    }

    public Optional<JsonSubCategory> getSubCategory(String category, String subCategoryId, boolean isChild, AgeState ageState) {
        DialogueCategoryFile file = getCategoryFile(category, isChild);
        if (file == null || file.subCategories == null) {
            return Optional.empty();
        }
        return file.subCategories.stream()
                .filter(Objects::nonNull)
                .filter(sc -> sc.id != null && sc.id.equalsIgnoreCase(subCategoryId))
                .filter(sc -> matchesAgeGroup(sc.age_group, ageState))
                .findFirst();
    }

    public String randomBurnoutLine(boolean isChild) {
        DialogueCategoryFile common = getCategoryFile("common", isChild);
        if (common != null && common.burnoutResponses != null && !common.burnoutResponses.isEmpty()) {
            return common.burnoutResponses.get(RANDOM.nextInt(common.burnoutResponses.size()));
        }
        return "I need a break from talking right now.";
    }


    private boolean matchesAgeGroup(String ageGroup, AgeState ageState) {
        if (ageGroup == null || ageGroup.isBlank() || ageState == null) {
            return true;
        }

        String normalized = ageGroup.toLowerCase(Locale.ENGLISH);
        return switch (normalized) {
            case "baby" -> ageState == AgeState.BABY;
            case "toddler" -> ageState == AgeState.TODDLER;
            case "child" -> ageState == AgeState.TODDLER || ageState == AgeState.CHILD;
            case "juvenile", "kids", "kid" -> ageState == AgeState.BABY || ageState == AgeState.TODDLER || ageState == AgeState.CHILD;
            case "adult" -> ageState == AgeState.ADULT || ageState == AgeState.TEEN;
            default -> true;
        };
    }
    private DialogueCategoryFile getCategoryFile(String category, boolean isChild) {
        DialogueCategoryFile file = isChild ? kidsCategoryFiles.get(category) : categoryFiles.get(category);
        if (file == null && isChild) {
            file = categoryFiles.get(category);
        }
        return file;
    }

    private Optional<DialogueCategoryFile> loadCategory(String category, boolean isChild) {
        String path = isChild
                ? "data/mca/dialogues_nested/kids/" + category + ".json"
                : "data/mca/dialogues_nested/" + category + ".json";
        try (InputStream stream = DialogueJsonManager.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return Optional.empty();
            }
            DialogueCategoryFile parsed = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), DialogueCategoryFile.class);
            return Optional.ofNullable(parsed);
        } catch (RuntimeException ex) {
            MCA.LOGGER.warn("Failed to parse nested dialogue json {}", path, ex);
            return Optional.empty();
        } catch (Exception ex) {
            MCA.LOGGER.warn("Failed to load nested dialogue json {}", path, ex);
            return Optional.empty();
        }
    }

    public record PlayerOption(String category, String subCategoryId, String label, String playerLine) {
    }

    public static class DialogueCategoryFile {
        public String category;
        public List<JsonSubCategory> subCategories;
        public List<String> burnoutResponses;
    }

    public static class JsonSubCategory {
        public String id;
        public String label;
        public String age_group;
        public List<String> playerOptions;
        public List<JsonResult> results;
    }

    public static class JsonResult {
        public int baseChance;
        public boolean applyFatigue;
        public List<JsonCondition> conditions;
        public JsonActions actions;
        public List<String> npcResponses;
    }

    public static class JsonCondition {
        public int chance;
        public Integer hearts_min;
        public Integer hearts_max;
        public String job;
        public String trait;
        public String mood;
    }

    public static class JsonActions {
        public int positive;
        public int negative;
        public String command;
    }
}
