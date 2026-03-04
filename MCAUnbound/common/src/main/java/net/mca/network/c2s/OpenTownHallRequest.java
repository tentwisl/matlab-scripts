package net.mca.network.c2s;

import net.mca.block.TownHallBlockEntity;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Memories;
import net.mca.network.s2c.TownHallDataResponse;
import net.mca.server.world.data.GeopoliticalManager;
import net.mca.server.world.data.GeopoliticalNation;
import net.mca.server.world.data.GeopoliticalProfileManager;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
import java.util.*;

public class OpenTownHallRequest implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long posLong;

    public OpenTownHallRequest(BlockPos pos) {
        this.posLong = pos.asLong();
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        BlockPos pos = BlockPos.fromLong(posLong);
        ServerWorld world = player.getServerWorld();
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof TownHallBlockEntity townHall)) return;

        int villageId = townHall.getVillageId();
        VillageManager vm = VillageManager.get(world);

        // Auto-detect village if not yet assigned
        if (villageId == -1) {
            Optional<Village> nearest = vm.findNearestVillage(pos, 64);
            if (nearest.isPresent()) {
                villageId = nearest.get().getId();
                townHall.setVillageId(villageId);
            }
        }

        Optional<Village> village = vm.getOrEmpty(villageId);
        GeopoliticalManager geo = GeopoliticalManager.get(world);
        GeopoliticalProfileManager profiles = GeopoliticalProfileManager.get(world);

        // Gather per-villager hearts for this player
        Map<UUID, Integer> villagerHearts = new LinkedHashMap<>();
        Map<UUID, String> villagerNames = new LinkedHashMap<>();
        Map<UUID, String> villagerMoods = new LinkedHashMap<>();
        Map<UUID, String> villagerJobs = new LinkedHashMap<>();
        Map<UUID, Boolean> villagerMarried = new LinkedHashMap<>();
        Map<UUID, Boolean> villagerLoaded = new LinkedHashMap<>();

        if (village.isPresent()) {
            Village v = village.get();
            List<VillagerEntityMCA> residents = v.getResidents(world);
            for (VillagerEntityMCA villager : residents) {
                Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);
                int effectiveHearts = memory.getHearts();
                GeopoliticalProfileManager.PoliticalProfile profile = profiles.getProfile(villager.getUuid()).orElse(null);
                if (profile != null && profile.nationFounder() != null) {
                    GeopoliticalNation nation = geo.getNation(profile.nationFounder()).orElse(null);
                    if (nation != null) {
                        effectiveHearts = (int) Math.round(geo.computeNationLikenessPercent(world, nation, player) * 100.0D);
                    }
                }
                villagerHearts.put(villager.getUuid(), effectiveHearts);
                villagerNames.put(villager.getUuid(), villager.getName().getString());
                villagerMoods.put(villager.getUuid(), villager.getVillagerBrain().getMood().getName());
                villagerJobs.put(villager.getUuid(), villager.getProfession().id());
                villagerMarried.put(villager.getUuid(), villager.getRelationships().isMarried());
                villagerLoaded.put(villager.getUuid(), true);
            }

            // Include residents not currently loaded (from residentNames map)
            for (Map.Entry<UUID, String> entry : v.getResidentNames().entrySet()) {
                if (!villagerNames.containsKey(entry.getKey())) {
                    villagerNames.put(entry.getKey(), entry.getValue());
                    // Use village reputation data for unloaded villagers
                    villagerHearts.putIfAbsent(entry.getKey(), 0);
                    villagerMoods.putIfAbsent(entry.getKey(), "unknown");
                    villagerJobs.putIfAbsent(entry.getKey(), "none");
                    villagerMarried.putIfAbsent(entry.getKey(), false);
                    villagerLoaded.putIfAbsent(entry.getKey(), false);
                }
            }
        }

        // Count how many other village leaders this player has convinced
        int convincedLeaderCount = (int) vm.findVillages(
                v -> player.getUuid().equals(v.getConvincedByPlayerUUID())
        ).count();

        NetworkHandler.sendToPlayer(new TownHallDataResponse(
                village.orElse(null),
                townHall,
                villagerHearts,
                villagerNames,
                villagerMoods,
                villagerJobs,
                villagerMarried,
                villagerLoaded,
                player.getUuid(),
                convincedLeaderCount
        ), player);
    }
}
