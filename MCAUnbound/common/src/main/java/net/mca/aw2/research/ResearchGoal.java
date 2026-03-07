package net.mca.aw2.research;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Represents a single research goal in the AW2 tech tree.
 * Each goal has prerequisites, required materials, and research time.
 * Completing a goal unlocks recipes, buildings, or capabilities.
 *
 * Ported from AW2's ResearchGoal system, adapted for MCA's nation simulation.
 * Research goals are tied to the nation's science level progression.
 */
public class ResearchGoal {
    private final Identifier id;
    private final String displayName;
    private final String description;
    private final ResearchCategory category;
    private final int researchTime; // Ticks required to complete
    private final int scienceLevelRequired;
    private final List<Identifier> prerequisites;
    private final List<ItemStack> requiredMaterials;
    private final List<Identifier> unlockedRecipes;

    public ResearchGoal(Identifier id, String displayName, String description,
                        ResearchCategory category, int researchTime, int scienceLevelRequired,
                        List<Identifier> prerequisites, List<ItemStack> requiredMaterials,
                        List<Identifier> unlockedRecipes) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.researchTime = researchTime;
        this.scienceLevelRequired = scienceLevelRequired;
        this.prerequisites = Collections.unmodifiableList(prerequisites);
        this.requiredMaterials = Collections.unmodifiableList(requiredMaterials);
        this.unlockedRecipes = Collections.unmodifiableList(unlockedRecipes);
    }

    public Identifier getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public ResearchCategory getCategory() { return category; }
    public int getResearchTime() { return researchTime; }
    public int getScienceLevelRequired() { return scienceLevelRequired; }
    public List<Identifier> getPrerequisites() { return prerequisites; }
    public List<ItemStack> getRequiredMaterials() { return requiredMaterials; }
    public List<Identifier> getUnlockedRecipes() { return unlockedRecipes; }

    public enum ResearchCategory {
        AGRICULTURE("Agriculture", "Farming and food production technologies"),
        MINING("Mining", "Excavation and ore processing technologies"),
        CRAFTING("Crafting", "Advanced crafting and manufacturing"),
        MILITARY("Military", "Weapons, armor, and siege equipment"),
        LOGISTICS("Logistics", "Transport, storage, and distribution"),
        ENGINEERING("Engineering", "Mechanical power and automation"),
        SCIENCE("Science", "Advanced research and discovery");

        private final String displayName;
        private final String description;

        ResearchCategory(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // ==================== DEFAULT RESEARCH TREE ====================

    /**
     * Builds the default AW2 research tree.
     * This is the full tech tree ported from AW2 with modifications for MCA integration.
     */
    public static Map<Identifier, ResearchGoal> buildDefaultTree() {
        Map<Identifier, ResearchGoal> tree = new LinkedHashMap<>();
        Identifier id;

        // === Tier 1: Basic (Science Level 0-1) ===

        id = new Identifier("mca", "research/basic_farming");
        tree.put(id, new ResearchGoal(id, "Basic Farming", "Unlock the Crop Farm worksite",
                ResearchCategory.AGRICULTURE, 6000, 0, List.of(),
                List.of(new ItemStack(Items.WHEAT, 16), new ItemStack(Items.OAK_PLANKS, 8)),
                List.of(new Identifier("mca", "aw2_crop_farm"))));

        id = new Identifier("mca", "research/animal_husbandry");
        tree.put(id, new ResearchGoal(id, "Animal Husbandry", "Unlock the Animal Farm worksite",
                ResearchCategory.AGRICULTURE, 8000, 0, List.of(new Identifier("mca", "research/basic_farming")),
                List.of(new ItemStack(Items.WHEAT, 16), new ItemStack(Items.LEAD, 2)),
                List.of(new Identifier("mca", "aw2_animal_farm"))));

        id = new Identifier("mca", "research/basic_forestry");
        tree.put(id, new ResearchGoal(id, "Basic Forestry", "Unlock the Tree Farm worksite",
                ResearchCategory.AGRICULTURE, 6000, 0, List.of(),
                List.of(new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.IRON_AXE, 1)),
                List.of(new Identifier("mca", "aw2_tree_farm"))));

        id = new Identifier("mca", "research/hand_power");
        tree.put(id, new ResearchGoal(id, "Hand Cranked Power", "Unlock the Hand Crank generator",
                ResearchCategory.ENGINEERING, 4000, 0, List.of(),
                List.of(new ItemStack(Items.IRON_INGOT, 8), new ItemStack(Items.OAK_PLANKS, 16)),
                List.of(new Identifier("mca", "aw2_hand_crank"))));

        // === Tier 2: Intermediate (Science Level 2-3) ===

        id = new Identifier("mca", "research/fishing");
        tree.put(id, new ResearchGoal(id, "Fishing Operations", "Unlock the Fish Farm worksite",
                ResearchCategory.AGRICULTURE, 8000, 2, List.of(new Identifier("mca", "research/basic_farming")),
                List.of(new ItemStack(Items.FISHING_ROD, 2), new ItemStack(Items.OAK_PLANKS, 16)),
                List.of(new Identifier("mca", "aw2_fish_farm"))));

        id = new Identifier("mca", "research/basic_mining");
        tree.put(id, new ResearchGoal(id, "Mining Operations", "Unlock the Quarry worksite",
                ResearchCategory.MINING, 12000, 2, List.of(),
                List.of(new ItemStack(Items.IRON_PICKAXE, 2), new ItemStack(Items.OAK_PLANKS, 32)),
                List.of(new Identifier("mca", "aw2_quarry"))));

        id = new Identifier("mca", "research/wind_power");
        tree.put(id, new ResearchGoal(id, "Wind Power", "Unlock the Windmill generator",
                ResearchCategory.ENGINEERING, 10000, 2,
                List.of(new Identifier("mca", "research/hand_power")),
                List.of(new ItemStack(Items.IRON_INGOT, 16), new ItemStack(Items.WHITE_WOOL, 8)),
                List.of(new Identifier("mca", "aw2_windmill"))));

        id = new Identifier("mca", "research/water_power");
        tree.put(id, new ResearchGoal(id, "Water Power", "Unlock the Waterwheel generator",
                ResearchCategory.ENGINEERING, 10000, 2,
                List.of(new Identifier("mca", "research/hand_power")),
                List.of(new ItemStack(Items.IRON_INGOT, 16), new ItemStack(Items.OAK_PLANKS, 32)),
                List.of(new Identifier("mca", "aw2_waterwheel"))));

        // === Tier 3: Advanced (Science Level 4-5) ===

        id = new Identifier("mca", "research/auto_crafting");
        tree.put(id, new ResearchGoal(id, "Automated Crafting", "Unlock the Auto Crafting worksite",
                ResearchCategory.CRAFTING, 16000, 4,
                List.of(new Identifier("mca", "research/wind_power")),
                List.of(new ItemStack(Items.CRAFTING_TABLE, 4), new ItemStack(Items.IRON_INGOT, 16),
                        new ItemStack(Items.REDSTONE, 16)),
                List.of(new Identifier("mca", "aw2_auto_crafting"))));

        id = new Identifier("mca", "research/warehousing");
        tree.put(id, new ResearchGoal(id, "Warehousing", "Unlock the Warehouse storage block",
                ResearchCategory.LOGISTICS, 12000, 4,
                List.of(new Identifier("mca", "research/basic_mining")),
                List.of(new ItemStack(Items.CHEST, 8), new ItemStack(Items.IRON_INGOT, 16)),
                List.of(new Identifier("mca", "aw2_warehouse"))));

        id = new Identifier("mca", "research/ore_processing");
        tree.put(id, new ResearchGoal(id, "Ore Processing", "Unlock the Ore Processor worksite",
                ResearchCategory.MINING, 20000, 4,
                List.of(new Identifier("mca", "research/basic_mining")),
                List.of(new ItemStack(Items.IRON_INGOT, 32), new ItemStack(Items.FURNACE, 4)),
                List.of(new Identifier("mca", "aw2_ore_processor"))));

        id = new Identifier("mca", "research/advanced_farming");
        tree.put(id, new ResearchGoal(id, "Advanced Agriculture", "Crop farms gain +25% yield",
                ResearchCategory.AGRICULTURE, 16000, 4,
                List.of(new Identifier("mca", "research/animal_husbandry")),
                List.of(new ItemStack(Items.BONE_MEAL, 32), new ItemStack(Items.GOLDEN_HOE, 1)),
                List.of()));

        // === Tier 4: Expert (Science Level 6-7) ===

        id = new Identifier("mca", "research/stirling_engine");
        tree.put(id, new ResearchGoal(id, "Stirling Engine", "Unlock the Stirling Generator",
                ResearchCategory.ENGINEERING, 24000, 6,
                List.of(new Identifier("mca", "research/wind_power"),
                        new Identifier("mca", "research/water_power")),
                List.of(new ItemStack(Items.IRON_INGOT, 32), new ItemStack(Items.PISTON, 4),
                        new ItemStack(Items.BLAZE_ROD, 2)),
                List.of(new Identifier("mca", "aw2_stirling_generator"))));

        id = new Identifier("mca", "research/advanced_logistics");
        tree.put(id, new ResearchGoal(id, "Advanced Logistics", "Warehouse capacity doubled; courier speed +50%",
                ResearchCategory.LOGISTICS, 20000, 6,
                List.of(new Identifier("mca", "research/warehousing")),
                List.of(new ItemStack(Items.ENDER_PEARL, 8), new ItemStack(Items.IRON_INGOT, 32)),
                List.of()));

        // === Tier 5: Master (Science Level 8-10) ===

        id = new Identifier("mca", "research/deep_mining");
        tree.put(id, new ResearchGoal(id, "Deep Mining", "Quarries can reach Y=-64; diamond/ancient debris extraction",
                ResearchCategory.MINING, 30000, 8,
                List.of(new Identifier("mca", "research/ore_processing")),
                List.of(new ItemStack(Items.DIAMOND_PICKAXE, 2), new ItemStack(Items.OBSIDIAN, 16)),
                List.of()));

        id = new Identifier("mca", "research/full_automation");
        tree.put(id, new ResearchGoal(id, "Full Automation", "All worksites produce at 2x rate",
                ResearchCategory.ENGINEERING, 48000, 10,
                List.of(new Identifier("mca", "research/stirling_engine"),
                        new Identifier("mca", "research/auto_crafting")),
                List.of(new ItemStack(Items.NETHERITE_INGOT, 4), new ItemStack(Items.REDSTONE_BLOCK, 16),
                        new ItemStack(Items.DIAMOND, 8)),
                List.of()));

        return tree;
    }
}
