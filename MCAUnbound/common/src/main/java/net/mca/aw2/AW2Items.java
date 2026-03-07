package net.mca.aw2;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.mca.MCA;
import net.mca.aw2.item.ResearchBookItem;
import net.mca.aw2.item.ResearchNotesItem;
import net.mca.aw2.item.WorkOrderItem;
import net.mca.aw2.item.WorksiteUpgradeItem;
import net.mca.aw2.worksite.WorksiteUpgrade;
import net.mca.item.ItemsMCA;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

/**
 * Registry for all AW2 integration items.
 * Following MCA's existing ItemsMCA pattern.
 */
public interface AW2Items {
    DeferredRegister<Item> ITEMS = DeferredRegister.create(MCA.MOD_ID, RegistryKeys.ITEM);

    // ==================== BLOCK ITEMS ====================

    RegistrySupplier<Item> CROP_FARM = register("aw2_crop_farm",
            () -> new BlockItem(AW2Blocks.CROP_FARM.get(), baseProps()));
    RegistrySupplier<Item> ANIMAL_FARM = register("aw2_animal_farm",
            () -> new BlockItem(AW2Blocks.ANIMAL_FARM.get(), baseProps()));
    RegistrySupplier<Item> TREE_FARM = register("aw2_tree_farm",
            () -> new BlockItem(AW2Blocks.TREE_FARM.get(), baseProps()));
    RegistrySupplier<Item> QUARRY = register("aw2_quarry",
            () -> new BlockItem(AW2Blocks.QUARRY.get(), baseProps()));
    RegistrySupplier<Item> FISH_FARM = register("aw2_fish_farm",
            () -> new BlockItem(AW2Blocks.FISH_FARM.get(), baseProps()));
    RegistrySupplier<Item> AUTO_CRAFTING = register("aw2_auto_crafting",
            () -> new BlockItem(AW2Blocks.AUTO_CRAFTING.get(), baseProps()));
    RegistrySupplier<Item> ORE_PROCESSOR = register("aw2_ore_processor",
            () -> new BlockItem(AW2Blocks.ORE_PROCESSOR.get(), baseProps()));

    RegistrySupplier<Item> HAND_CRANK = register("aw2_hand_crank",
            () -> new BlockItem(AW2Blocks.HAND_CRANK.get(), baseProps()));
    RegistrySupplier<Item> WINDMILL = register("aw2_windmill",
            () -> new BlockItem(AW2Blocks.WINDMILL.get(), baseProps()));
    RegistrySupplier<Item> WATERWHEEL = register("aw2_waterwheel",
            () -> new BlockItem(AW2Blocks.WATERWHEEL.get(), baseProps()));
    RegistrySupplier<Item> STIRLING_GENERATOR = register("aw2_stirling_generator",
            () -> new BlockItem(AW2Blocks.STIRLING_GENERATOR.get(), baseProps()));

    RegistrySupplier<Item> RESEARCH_TABLE = register("aw2_research_table",
            () -> new BlockItem(AW2Blocks.RESEARCH_TABLE.get(), baseProps()));
    RegistrySupplier<Item> ENGINEERING_STATION = register("aw2_engineering_station",
            () -> new BlockItem(AW2Blocks.ENGINEERING_STATION.get(), baseProps()));
    RegistrySupplier<Item> WAREHOUSE = register("aw2_warehouse",
            () -> new BlockItem(AW2Blocks.WAREHOUSE.get(), baseProps()));

    // ==================== SPECIAL ITEMS ====================

    RegistrySupplier<Item> RESEARCH_BOOK = register("aw2_research_book",
            () -> new ResearchBookItem(baseProps().maxCount(1)));

    RegistrySupplier<Item> RESEARCH_NOTES = register("aw2_research_notes",
            () -> new ResearchNotesItem(baseProps().maxCount(1)));

    RegistrySupplier<Item> WORK_ORDER = register("aw2_work_order",
            () -> new WorkOrderItem(baseProps().maxCount(1)));

    // ==================== UPGRADES ====================

    RegistrySupplier<Item> UPGRADE_SIZE_MEDIUM = register("aw2_upgrade_size_medium",
            () -> new WorksiteUpgradeItem(baseProps(), WorksiteUpgrade.SIZE_MEDIUM));
    RegistrySupplier<Item> UPGRADE_SIZE_LARGE = register("aw2_upgrade_size_large",
            () -> new WorksiteUpgradeItem(baseProps(), WorksiteUpgrade.SIZE_LARGE));
    RegistrySupplier<Item> UPGRADE_TOOLS_1 = register("aw2_upgrade_enchanted_tools_1",
            () -> new WorksiteUpgradeItem(baseProps(), WorksiteUpgrade.ENCHANTED_TOOLS_1));
    RegistrySupplier<Item> UPGRADE_TOOLS_2 = register("aw2_upgrade_enchanted_tools_2",
            () -> new WorksiteUpgradeItem(baseProps(), WorksiteUpgrade.ENCHANTED_TOOLS_2));

    static void bootstrap() {
        ITEMS.register();
    }

    static RegistrySupplier<Item> register(String name, Supplier<Item> item) {
        return ITEMS.register(new Identifier(MCA.MOD_ID, name), item);
    }

    static Item.Settings baseProps() {
        return new Item.Settings().arch$tab(ItemsMCA.MCA_GROUP);
    }
}
