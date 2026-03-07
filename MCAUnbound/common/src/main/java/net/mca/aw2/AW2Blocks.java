package net.mca.aw2;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.mca.MCA;
import net.mca.aw2.block.*;
import net.mca.aw2.torque.TorqueGeneratorBlockEntity;
import net.mca.aw2.worksite.WorksiteType;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

/**
 * Registry for all AW2 integration blocks.
 * Following MCA's existing BlocksMCA pattern.
 */
public interface AW2Blocks {
    DeferredRegister<Block> BLOCKS = DeferredRegister.create(MCA.MOD_ID, RegistryKeys.BLOCK);

    // ==================== WORKSITES ====================

    RegistrySupplier<Block> CROP_FARM = register("aw2_crop_farm",
            () -> new WorksiteBlock(Block.Settings.copy(Blocks.OAK_PLANKS).nonOpaque(), WorksiteType.CROP_FARM));

    RegistrySupplier<Block> ANIMAL_FARM = register("aw2_animal_farm",
            () -> new WorksiteBlock(Block.Settings.copy(Blocks.OAK_PLANKS).nonOpaque(), WorksiteType.ANIMAL_FARM));

    RegistrySupplier<Block> TREE_FARM = register("aw2_tree_farm",
            () -> new WorksiteBlock(Block.Settings.copy(Blocks.OAK_PLANKS).nonOpaque(), WorksiteType.TREE_FARM));

    RegistrySupplier<Block> QUARRY = register("aw2_quarry",
            () -> new WorksiteBlock(Block.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), WorksiteType.QUARRY));

    RegistrySupplier<Block> FISH_FARM = register("aw2_fish_farm",
            () -> new WorksiteBlock(Block.Settings.copy(Blocks.OAK_PLANKS).nonOpaque(), WorksiteType.FISH_FARM));

    RegistrySupplier<Block> AUTO_CRAFTING = register("aw2_auto_crafting",
            () -> new WorksiteBlock(Block.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), WorksiteType.AUTO_CRAFTING));

    RegistrySupplier<Block> ORE_PROCESSOR = register("aw2_ore_processor",
            () -> new WorksiteBlock(Block.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), WorksiteType.ORE_PROCESSOR));

    // ==================== TORQUE GENERATORS ====================

    RegistrySupplier<Block> HAND_CRANK = register("aw2_hand_crank",
            () -> new TorqueGeneratorBlock(Block.Settings.copy(Blocks.OAK_PLANKS).nonOpaque(),
                    TorqueGeneratorBlockEntity.GeneratorType.HAND_CRANK));

    RegistrySupplier<Block> WINDMILL = register("aw2_windmill",
            () -> new TorqueGeneratorBlock(Block.Settings.copy(Blocks.OAK_PLANKS).nonOpaque(),
                    TorqueGeneratorBlockEntity.GeneratorType.WINDMILL));

    RegistrySupplier<Block> WATERWHEEL = register("aw2_waterwheel",
            () -> new TorqueGeneratorBlock(Block.Settings.copy(Blocks.OAK_PLANKS).nonOpaque(),
                    TorqueGeneratorBlockEntity.GeneratorType.WATERWHEEL));

    RegistrySupplier<Block> STIRLING_GENERATOR = register("aw2_stirling_generator",
            () -> new TorqueGeneratorBlock(Block.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(),
                    TorqueGeneratorBlockEntity.GeneratorType.STIRLING));

    // ==================== RESEARCH & CRAFTING ====================

    RegistrySupplier<Block> RESEARCH_TABLE = register("aw2_research_table",
            () -> new ResearchTableBlock(Block.Settings.copy(Blocks.OAK_PLANKS).nonOpaque()));

    RegistrySupplier<Block> ENGINEERING_STATION = register("aw2_engineering_station",
            () -> new EngineeringStationBlock(Block.Settings.copy(Blocks.OAK_PLANKS).nonOpaque()));

    // ==================== STORAGE ====================

    RegistrySupplier<Block> WAREHOUSE = register("aw2_warehouse",
            () -> new WarehouseBlock(Block.Settings.copy(Blocks.OAK_PLANKS).nonOpaque()));

    static void bootstrap() {
        BLOCKS.register();
        AW2BlockEntityTypes.bootstrap();
    }

    static <T extends Block> RegistrySupplier<T> register(String name, Supplier<T> block) {
        Identifier id = new Identifier(MCA.MOD_ID, name);
        return BLOCKS.register(id, block);
    }
}
