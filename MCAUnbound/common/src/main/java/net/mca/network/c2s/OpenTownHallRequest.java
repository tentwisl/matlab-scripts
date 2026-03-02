package net.mca.network.c2s;

import net.mca.block.TownHallBlockEntity;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Memories;
import net.mca.network.s2c.TownHallDataResponse;
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

        // Gather per-villager hearts for this player
        Map<UUID, Integer> villagerHearts = new LinkedHashMap<>();
        Map<UUID, String> villagerNames = new LinkedHashMap<>();

        if (village.isPresent()) {
            Village v = village.get();
            List<VillagerEntityMCA> residents = v.getResidents(world);
            for (VillagerEntityMCA villager : residents) {
                Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);
                villagerHearts.put(villager.getUuid(), memory.getHearts());
                villagerNames.put(villager.getUuid(), villager.getName().getString());
            }

            // Include residents not currently loaded (from residentNames map)
            for (Map.Entry<UUID, String> entry : v.getResidentNames().entrySet()) {
                if (!villagerNames.containsKey(entry.getKey())) {
                    villagerNames.put(entry.getKey(), entry.getValue());
                    // Use village reputation data for unloaded villagers
                    villagerHearts.putIfAbsent(entry.getKey(), 0);
                }
            }
        }

        NetworkHandler.sendToPlayer(new TownHallDataResponse(
                village.orElse(null),
                townHall,
                villagerHearts,
                villagerNames,
                player.getUuid()
        ), player);
    }
}
