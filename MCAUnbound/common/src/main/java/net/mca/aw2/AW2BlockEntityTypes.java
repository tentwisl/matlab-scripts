package net.mca.aw2;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.mca.MCA;
import net.mca.aw2.research.ResearchTableBlockEntity;
import net.mca.aw2.torque.TorqueGeneratorBlockEntity;
import net.mca.aw2.warehouse.WarehouseBlockEntity;
import net.mca.aw2.worksite.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Registry for all AW2 integration block entity types.
 * Following MCA's existing BlockEntityTypesMCA pattern.
 */
public interface AW2BlockEntityTypes {
    DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(MCA.MOD_ID, RegistryKeys.BLOCK_ENTITY_TYPE);

    // ==================== WORKSITES ====================

    RegistrySupplier<BlockEntityType<CropFarmBlockEntity>> CROP_FARM =
            register("aw2_crop_farm", CropFarmBlockEntity::new, List.of(AW2Blocks.CROP_FARM));

    RegistrySupplier<BlockEntityType<AnimalFarmBlockEntity>> ANIMAL_FARM =
            register("aw2_animal_farm", AnimalFarmBlockEntity::new, List.of(AW2Blocks.ANIMAL_FARM));

    RegistrySupplier<BlockEntityType<TreeFarmBlockEntity>> TREE_FARM =
            register("aw2_tree_farm", TreeFarmBlockEntity::new, List.of(AW2Blocks.TREE_FARM));

    RegistrySupplier<BlockEntityType<QuarryBlockEntity>> QUARRY =
            register("aw2_quarry", QuarryBlockEntity::new, List.of(AW2Blocks.QUARRY));

    RegistrySupplier<BlockEntityType<FishFarmBlockEntity>> FISH_FARM =
            register("aw2_fish_farm", FishFarmBlockEntity::new, List.of(AW2Blocks.FISH_FARM));

    RegistrySupplier<BlockEntityType<AutoCraftingBlockEntity>> AUTO_CRAFTING =
            register("aw2_auto_crafting", AutoCraftingBlockEntity::new, List.of(AW2Blocks.AUTO_CRAFTING));

    // ==================== TORQUE GENERATORS ====================

    RegistrySupplier<BlockEntityType<TorqueGeneratorBlockEntity>> HAND_CRANK =
            register("aw2_hand_crank",
                    (pos, state) -> new TorqueGeneratorBlockEntity(AW2BlockEntityTypes.HAND_CRANK.get(), pos, state,
                            TorqueGeneratorBlockEntity.GeneratorType.HAND_CRANK),
                    List.of(AW2Blocks.HAND_CRANK));

    RegistrySupplier<BlockEntityType<TorqueGeneratorBlockEntity>> WINDMILL =
            register("aw2_windmill",
                    (pos, state) -> new TorqueGeneratorBlockEntity(AW2BlockEntityTypes.WINDMILL.get(), pos, state,
                            TorqueGeneratorBlockEntity.GeneratorType.WINDMILL),
                    List.of(AW2Blocks.WINDMILL));

    RegistrySupplier<BlockEntityType<TorqueGeneratorBlockEntity>> WATERWHEEL =
            register("aw2_waterwheel",
                    (pos, state) -> new TorqueGeneratorBlockEntity(AW2BlockEntityTypes.WATERWHEEL.get(), pos, state,
                            TorqueGeneratorBlockEntity.GeneratorType.WATERWHEEL),
                    List.of(AW2Blocks.WATERWHEEL));

    RegistrySupplier<BlockEntityType<TorqueGeneratorBlockEntity>> STIRLING_GENERATOR =
            register("aw2_stirling_generator",
                    (pos, state) -> new TorqueGeneratorBlockEntity(AW2BlockEntityTypes.STIRLING_GENERATOR.get(), pos, state,
                            TorqueGeneratorBlockEntity.GeneratorType.STIRLING),
                    List.of(AW2Blocks.STIRLING_GENERATOR));

    // ==================== RESEARCH & CRAFTING ====================

    RegistrySupplier<BlockEntityType<ResearchTableBlockEntity>> RESEARCH_TABLE =
            register("aw2_research_table",
                    (pos, state) -> new ResearchTableBlockEntity(AW2BlockEntityTypes.RESEARCH_TABLE.get(), pos, state),
                    List.of(AW2Blocks.RESEARCH_TABLE));

    RegistrySupplier<BlockEntityType<EngineeringStationBlockEntity>> ENGINEERING_STATION =
            register("aw2_engineering_station",
                    (pos, state) -> new EngineeringStationBlockEntity(AW2BlockEntityTypes.ENGINEERING_STATION.get(), pos, state),
                    List.of(AW2Blocks.ENGINEERING_STATION));

    // ==================== STORAGE ====================

    RegistrySupplier<BlockEntityType<WarehouseBlockEntity>> WAREHOUSE =
            register("aw2_warehouse",
                    (pos, state) -> new WarehouseBlockEntity(AW2BlockEntityTypes.WAREHOUSE.get(), pos, state),
                    List.of(AW2Blocks.WAREHOUSE));

    static void bootstrap() {
        BLOCK_ENTITY_TYPES.register();
    }

    static <T extends BlockEntity> RegistrySupplier<BlockEntityType<T>> register(
            String name, BiFunction<BlockPos, BlockState, T> factory,
            List<RegistrySupplier<Block>> suppliers) {
        Identifier id = new Identifier(MCA.MOD_ID, name);
        return BLOCK_ENTITY_TYPES.register(id, () -> BlockEntityType.Builder.create(
                factory::apply, suppliers.stream().map(RegistrySupplier::get).toArray(Block[]::new)
        ).build(Util.getChoiceType(TypeReferences.BLOCK_ENTITY, id.toString())));
    }
}
