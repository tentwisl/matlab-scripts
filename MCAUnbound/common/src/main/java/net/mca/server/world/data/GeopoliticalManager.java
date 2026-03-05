package net.mca.server.world.data;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.*;

public class GeopoliticalManager extends PersistentState {
    private final Map<UUID, GeopoliticalNation> nationsByFounder = new HashMap<>();
    private final Map<Integer, Set<UUID>> congressionalReps = new HashMap<>();

    public static GeopoliticalManager get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(GeopoliticalManager::fromNbt, GeopoliticalManager::new, "mca_unbound_geopolitics");
    }

    public Optional<GeopoliticalNation> getNation(UUID founder) {
        return Optional.ofNullable(nationsByFounder.get(founder));
    }

    public Optional<GeopoliticalNation> findNationByVillage(int villageId) {
        return nationsByFounder.values().stream().filter(n -> n.getVillageIds().contains(villageId)).findFirst();
    }

    public GeopoliticalNation getOrCreateNation(UUID founder, String name) {
        GeopoliticalNation nation = nationsByFounder.computeIfAbsent(founder, f -> new GeopoliticalNation(f, name));
        markDirty();
        return nation;
    }

    public double computeNationLikenessPercent(ServerWorld world, GeopoliticalNation nation, ServerPlayerEntity player) {
        VillageManager vm = VillageManager.get(world);
        int residents = 0;
        int hearts = 0;
        for (Integer villageId : nation.getVillageIds()) {
            Village village = vm.getOrEmpty(villageId).orElse(null);
            if (village == null) continue;
            for (VillagerEntityMCA villager : village.getResidents(world)) {
                residents++;
                hearts += villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
            }
        }
        return nation.computeReputationPercent(residents, hearts);
    }

    public void ensureCongressionalReps(ServerWorld world) {
        VillageManager vm = VillageManager.get(world);
        for (GeopoliticalNation nation : nationsByFounder.values()) {
            if (nation.getGovernmentType() != GeopoliticalNation.GovernmentType.REPUBLIC) continue;
            for (Integer villageId : nation.getVillageIds()) {
                Village v = vm.getOrEmpty(villageId).orElse(null);
                if (v == null) continue;
                List<VillagerEntityMCA> residents = v.getResidents(world);
                Set<UUID> reps = congressionalReps.computeIfAbsent(villageId, k -> new HashSet<>());
                reps.removeIf(uuid -> residents.stream().noneMatch(r -> r.getUuid().equals(uuid)));
                if (reps.size() < 2) {
                    residents.stream().map(VillagerEntityMCA::getUuid).filter(uuid -> !reps.contains(uuid)).limit(2 - reps.size()).forEach(reps::add);
                    markDirty();
                }
            }
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        nationsByFounder.values().forEach(n -> list.add(n.toNbt()));
        nbt.put("nations", list);

        NbtCompound repsTag = new NbtCompound();
        for (Map.Entry<Integer, Set<UUID>> e : congressionalReps.entrySet()) {
            NbtList repList = new NbtList();
            e.getValue().forEach(id -> repList.add(net.minecraft.nbt.NbtString.of(id.toString())));
            repsTag.put(Integer.toString(e.getKey()), repList);
        }
        nbt.put("congressionalReps", repsTag);
        return nbt;
    }

    public static GeopoliticalManager fromNbt(NbtCompound nbt) {
        GeopoliticalManager manager = new GeopoliticalManager();
        NbtList list = nbt.getList("nations", 10);
        for (int i = 0; i < list.size(); i++) {
            GeopoliticalNation n = GeopoliticalNation.fromNbt(list.getCompound(i));
            manager.nationsByFounder.put(n.founder(), n);
        }

        if (nbt.contains("congressionalReps")) {
            NbtCompound repsTag = nbt.getCompound("congressionalReps");
            for (String villageId : repsTag.getKeys()) {
                NbtList repList = repsTag.getList(villageId, 8);
                Set<UUID> ids = new HashSet<>();
                for (int i = 0; i < repList.size(); i++) ids.add(UUID.fromString(repList.getString(i)));
                manager.congressionalReps.put(Integer.parseInt(villageId), ids);
            }
        }
        return manager;
    }
}
