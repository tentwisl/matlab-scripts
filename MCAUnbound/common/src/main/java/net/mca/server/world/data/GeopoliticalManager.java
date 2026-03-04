package net.mca.server.world.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class GeopoliticalManager extends PersistentState {
    private final Map<UUID, GeopoliticalNation> nationsByFounder = new HashMap<>();

    public static GeopoliticalManager get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(GeopoliticalManager::fromNbt, GeopoliticalManager::new, "mca_unbound_geopolitics");
    }

    public Optional<GeopoliticalNation> getNation(UUID founder) {
        return Optional.ofNullable(nationsByFounder.get(founder));
    }

    public GeopoliticalNation getOrCreateNation(UUID founder, String name) {
        GeopoliticalNation nation = nationsByFounder.computeIfAbsent(founder, f -> new GeopoliticalNation(f, name));
        markDirty();
        return nation;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        nationsByFounder.values().forEach(n -> list.add(n.toNbt()));
        nbt.put("nations", list);
        return nbt;
    }

    public static GeopoliticalManager fromNbt(NbtCompound nbt) {
        GeopoliticalManager manager = new GeopoliticalManager();
        NbtList list = nbt.getList("nations", 10);
        for (int i = 0; i < list.size(); i++) {
            GeopoliticalNation n = GeopoliticalNation.fromNbt(list.getCompound(i));
            manager.nationsByFounder.put(n.founder(), n);
        }
        return manager;
    }
}
