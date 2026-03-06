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
    private static final List<String> CATEGORIES = List.of("common", "greet", "joke", "story", "romance", "play", "chat", "rumors", "ask");
    private static final net.minecraft.util.math.random.Random RANDOM = net.minecraft.util.math.random.Random.create();

    private final Map<String, DialogueCategoryFile> categoryFiles = new HashMap<>();
    private final Map<String, DialogueCategoryFile> kidsCategoryFiles = new HashMap<>();
    private EscalationDialogueFile escalationDialogueFile = new EscalationDialogueFile();
    private IntroDialogueFile introDialogueFile = new IntroDialogueFile();

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
        escalationDialogueFile = loadEscalationFile().orElseGet(EscalationDialogueFile::new);
        introDialogueFile = loadIntroFile().orElseGet(IntroDialogueFile::new);
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



    public String buildIntroLine(boolean firstMeeting,
                                 NpcTrait trait,
                                 String playerName,
                                 String npcName,
                                 String jobKey,
                                 String jobDisplay,
                                 String rivalName) {
        String tone = pickToneVariant(firstMeeting, trait)
                .replace("{player}", playerName)
                .replace("{npc}", npcName);

        StringBuilder line = new StringBuilder(tone);

        String normalizedJobKey = jobKey == null ? "none" : jobKey.toLowerCase(Locale.ENGLISH);
        boolean canOfferService = !normalizedJobKey.equals("none")
                && !normalizedJobKey.equals("jobless")
                && !normalizedJobKey.equals("guard")
                && !normalizedJobKey.equals("village_leader");

        if (canOfferService) {
            String service = introDialogueFile.service_description.getOrDefault(normalizedJobKey, "a helping hand");
            String template = introDialogueFile.job_intro_template == null || introDialogueFile.job_intro_template.isBlank()
                    ? "I'm the {job} around here. Let me know if you need {service}."
                    : introDialogueFile.job_intro_template;
            line.append(' ').append(template
                    .replace("{job}", jobDisplay)
                    .replace("{service}", service));

            if (rivalName != null && !rivalName.isBlank()) {
                String rivalTemplate = introDialogueFile.rivalry_template == null || introDialogueFile.rivalry_template.isBlank()
                        ? "...and don't listen to {rival}, their {service} isn't nearly as good as mine."
                        : introDialogueFile.rivalry_template;
                line.append(' ').append(rivalTemplate
                        .replace("{rival}", rivalName)
                        .replace("{service}", service));
            }
        }

        return line.toString();
    }

    private String pickToneVariant(boolean firstMeeting, NpcTrait trait) {
        String key = trait == null ? "normal" : trait.name().toLowerCase(Locale.ENGLISH);
        Map<String, String> toneMap = firstMeeting
                ? introDialogueFile.first_tone_variants
                : introDialogueFile.standard_tone_variants;
        if (toneMap == null || toneMap.isEmpty()) {
            return "Hello {player}, I'm {npc}.";
        }

        if (toneMap.containsKey(key)) {
            return toneMap.get(key);
        }
        return toneMap.getOrDefault("normal", "Hello {player}, I'm {npc}.");
    }

    public Optional<String> getEscalatedResponse(int sessionHeartDelta, boolean lockoutActive) {
        if (lockoutActive) {
            return Optional.of(pickEscalationLine(escalationDialogueFile.lockout, "I have nothing to say to you right now."));
        }
        if (sessionHeartDelta <= -20) {
            return Optional.of(pickEscalationLine(escalationDialogueFile.severe_dismissive, "Enough. Leave me alone."));
        }
        if (sessionHeartDelta <= -5) {
            return Optional.of(pickEscalationLine(escalationDialogueFile.annoyed, "You're getting on my nerves."));
        }
        return Optional.empty();
    }

    private String pickEscalationLine(List<String> lines, String fallback) {
        if (lines == null || lines.isEmpty()) {
            return fallback;
        }
        return lines.get(RANDOM.nextInt(lines.size()));
    }


    private Optional<IntroDialogueFile> loadIntroFile() {
        String path = "data/mca/dialogues_nested/common/intro.json";
        try (InputStream stream = DialogueJsonManager.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return Optional.empty();
            }
            IntroDialogueFile parsed = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), IntroDialogueFile.class);
            return Optional.ofNullable(parsed);
        } catch (RuntimeException ex) {
            MCA.LOGGER.warn("Failed to parse intro dialogue json {}", path, ex);
            return Optional.empty();
        } catch (Exception ex) {
            MCA.LOGGER.warn("Failed to load intro dialogue json {}", path, ex);
            return Optional.empty();
        }
    }

    private Optional<EscalationDialogueFile> loadEscalationFile() {
        String path = "data/mca/dialogues_nested/common/escalation.json";
        try (InputStream stream = DialogueJsonManager.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return Optional.empty();
            }
            EscalationDialogueFile parsed = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), EscalationDialogueFile.class);
            return Optional.ofNullable(parsed);
        } catch (RuntimeException ex) {
            MCA.LOGGER.warn("Failed to parse escalation dialogue json {}", path, ex);
            return Optional.empty();
        } catch (Exception ex) {
            MCA.LOGGER.warn("Failed to load escalation dialogue json {}", path, ex);
            return Optional.empty();
        }
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

    public static class EscalationDialogueFile {
        public List<String> annoyed = List.of();
        public List<String> severe_dismissive = List.of();
        public List<String> lockout = List.of();
    }

    public static class IntroDialogueFile {
        public Map<String, String> first_tone_variants = new HashMap<>();
        public Map<String, String> standard_tone_variants = new HashMap<>();
        public Map<String, String> service_description = new HashMap<>();
        public String job_intro_template = "I'm the {job} around here. Let me know if you need {service}.";
        public String rivalry_template = "...and don't listen to {rival}, their {service} isn't nearly as good as mine.";
    }
}
