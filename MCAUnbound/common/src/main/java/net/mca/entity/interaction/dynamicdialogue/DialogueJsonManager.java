package net.mca.entity.interaction.dynamicdialogue;

import com.google.gson.Gson;
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
    private static final List<String> CATEGORIES = List.of("common", "greet", "joke", "story", "romance", "chat", "rumors");
    private static final net.minecraft.util.math.random.Random RANDOM = net.minecraft.util.math.random.Random.create();

    private final Map<String, DialogueCategoryFile> categoryFiles = new HashMap<>();

    public DialogueJsonManager() {
        reload();
    }

    public void reload() {
        categoryFiles.clear();
        for (String category : CATEGORIES) {
            loadCategory(category).ifPresent(file -> categoryFiles.put(category, file));
        }
    }

    public List<PlayerOption> getPlayerOptions(String category) {
        DialogueCategoryFile file = categoryFiles.get(category);
        if (file == null || file.subCategories == null) {
            return List.of();
        }

        List<PlayerOption> options = new ArrayList<>();
        for (JsonSubCategory sc : file.subCategories) {
            if (sc == null || sc.id == null || sc.playerOptions == null || sc.playerOptions.isEmpty()) {
                continue;
            }
            String line = sc.playerOptions.get(RANDOM.nextInt(sc.playerOptions.size()));
            options.add(new PlayerOption(category, sc.id, sc.label == null ? sc.id : sc.label, line));
        }
        return options;
    }

    public Optional<JsonSubCategory> getSubCategory(String category, String subCategoryId) {
        DialogueCategoryFile file = categoryFiles.get(category);
        if (file == null || file.subCategories == null) {
            return Optional.empty();
        }
        return file.subCategories.stream()
                .filter(Objects::nonNull)
                .filter(sc -> sc.id != null && sc.id.equalsIgnoreCase(subCategoryId))
                .findFirst();
    }

    public String randomBurnoutLine() {
        DialogueCategoryFile common = categoryFiles.get("common");
        if (common != null && common.burnoutResponses != null && !common.burnoutResponses.isEmpty()) {
            return common.burnoutResponses.get(RANDOM.nextInt(common.burnoutResponses.size()));
        }
        return "I need a break from talking right now.";
    }

    private Optional<DialogueCategoryFile> loadCategory(String category) {
        String path = "data/mca/dialogues_nested/" + category + ".json";
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
