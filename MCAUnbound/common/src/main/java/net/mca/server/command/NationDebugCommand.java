package net.mca.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.mca.aw2.AW2ColonyManager;
import net.mca.aw2.WorksiteProductionTracker;
import net.mca.aw2.warehouse.WarehouseBlockEntity;
import net.mca.aw2.worker.WorkerManager;
import net.mca.aw2.worksite.WorksiteBlockEntity;
import net.mca.block.TownHallBlockEntity;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.*;

public final class NationDebugCommand {
    private NationDebugCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("nation")
                .requires(src -> src.hasPermissionLevel(2) || src.getServer().isSingleplayer())
                .then(CommandManager.literal("debug")
                        .then(CommandManager.literal("merge_villages")
                                .executes(NationDebugCommand::mergeVillages))
                        .then(CommandManager.literal("aw2")
                                .then(CommandManager.literal("summary")
                                        .executes(NationDebugCommand::aw2Summary))
                                .then(CommandManager.literal("nearby")
                                        .executes(NationDebugCommand::aw2Nearby)))));
    }

    private static int aw2Summary(CommandContext<ServerCommandSource> ctx) {
        ServerWorld world = ctx.getSource().getWorld();
        BlockPos sourcePos = BlockPos.ofFloored(ctx.getSource().getPosition());

        WorksiteProductionTracker production = WorksiteProductionTracker.get(world);
        WorkerManager workers = WorkerManager.get(world);
        AW2ColonyManager colony = AW2ColonyManager.get(world);
        VillageManager villageManager = VillageManager.get(world);

        int registeredWorksites = production.getRegisteredWorksiteCount();
        int assignments = workers.getAssignmentCount();
        int trackedStatuses = colony.getTrackedWorkerStatusCount();

        int nearestVillageFood = 0;
        String nearestVillageName = "none";
        var nearestVillage = villageManager.findNearestVillage(sourcePos, Village.BORDER_MARGIN * 2);
        if (nearestVillage.isPresent()) {
            nearestVillageFood = colony.getVillageFoodPoints(nearestVillage.get().getVillageUuid());
            nearestVillageName = nearestVillage.get().getName();
        }

        final int nearestVillageFoodFinal = nearestVillageFood;
        final String nearestVillageNameFinal = nearestVillageName;

        ctx.getSource().sendFeedback(() -> Text.literal(String.format(
                "[AW2 Debug] worksites=%d, assignments=%d, workerStatuses=%d, nearestVillage=%s, villageFood=%d",
                registeredWorksites, assignments, trackedStatuses, nearestVillageNameFinal, nearestVillageFoodFinal
        )), false);

        return 1;
    }

    private static int aw2Nearby(CommandContext<ServerCommandSource> ctx) {
        ServerWorld world = ctx.getSource().getWorld();
        BlockPos center = BlockPos.ofFloored(ctx.getSource().getPosition());
        int radius = 24;

        int worksites = 0;
        int warehouses = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -8; dy <= 8; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    var be = world.getBlockEntity(pos);
                    if (be instanceof WorksiteBlockEntity worksite) {
                        worksites++;
                        if (worksites <= 8) {
                            ctx.getSource().sendFeedback(() -> Text.literal(String.format(
                                    "[AW2 Nearby] Worksite %s @ %s | workers=%d | active=%s | workDone=%d",
                                    worksite.getWorksiteType().name(), pos,
                                    worksite.getWorkerCount(), worksite.isActive(), worksite.getTotalWorkDone()
                            )), false);
                        }
                    } else if (be instanceof WarehouseBlockEntity warehouse) {
                        warehouses++;
                        if (warehouses <= 4) {
                            ctx.getSource().sendFeedback(() -> Text.literal(String.format(
                                    "[AW2 Nearby] Warehouse @ %s | hauled=%d | supplied=%d | stockTypes=%d",
                                    pos,
                                    warehouse.getLastItemsHauledFromWorksites(),
                                    warehouse.getLastItemsSuppliedToWorksites(),
                                    warehouse.getStockLevels().size()
                            )), false);
                        }
                    }
                }
            }
        }

        int finalWorksites = worksites;
        int finalWarehouses = warehouses;
        ctx.getSource().sendFeedback(() -> Text.literal(String.format(
                "[AW2 Nearby] scanned radius=%d -> worksites=%d, warehouses=%d",
                radius, finalWorksites, finalWarehouses
        )), false);

        return 1;
    }

    private static int mergeVillages(CommandContext<ServerCommandSource> ctx) {
        ServerWorld world = ctx.getSource().getWorld();
        VillageManager manager = VillageManager.get(world);

        List<Village> all = new ArrayList<>();
        manager.forEach(all::add);
        all.sort(Comparator.comparingInt(Village::getId));

        int mergedCount = 0;
        Map<Integer, Integer> remap = new HashMap<>();

        for (int i = 0; i < all.size(); i++) {
            Village target = all.get(i);
            if (target == null) continue;
            for (int j = i + 1; j < all.size(); j++) {
                Village candidate = all.get(j);
                if (candidate == null) continue;
                if (target.getCenter().getSquaredDistance(candidate.getCenter()) <= (Village.MERGE_MARGIN * Village.MERGE_MARGIN)) {
                    target.merge(candidate);
                    remap.put(candidate.getId(), target.getId());
                    manager.removeVillage(candidate.getId());
                    all.set(j, null);
                    mergedCount++;
                }
            }
        }

        int townHallsUpdated = applyTownHallRemap(world, remap, all);
        if (mergedCount > 0 || townHallsUpdated > 0) {
            manager.markDirty();
        }

        final int mergedVillageCount = mergedCount;
        final int updatedTownHallCount = townHallsUpdated;
        ctx.getSource().sendFeedback(() -> Text.literal("[NationDebug] Merged villages=" + mergedVillageCount + ", updated town halls=" + updatedTownHallCount), true);
        return 1;
    }

    private static int applyTownHallRemap(ServerWorld world, Map<Integer, Integer> remap, List<Village> villages) {
        if (remap.isEmpty()) return 0;

        Set<BlockPos> toCheck = new HashSet<>();
        for (Village village : villages) {
            if (village == null) continue;
            if (village.getTownHallPos() != null) {
                toCheck.add(village.getTownHallPos());
            }
            Vec3i c = village.getCenter();
            int radius = 64;
            for (int x = -radius; x <= radius; x += 4) {
                for (int z = -radius; z <= radius; z += 4) {
                    int y = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, c.getX() + x, c.getZ() + z);
                    toCheck.add(new BlockPos(c.getX() + x, y, c.getZ() + z));
                    toCheck.add(new BlockPos(c.getX() + x, y + 1, c.getZ() + z));
                }
            }
        }

        int updated = 0;
        for (BlockPos pos : toCheck) {
            if (world.getBlockEntity(pos) instanceof TownHallBlockEntity townHall) {
                int current = townHall.getVillageId();
                if (remap.containsKey(current)) {
                    townHall.setVillageId(remap.get(current));
                    updated++;
                }
            }
        }
        return updated;
    }
}
