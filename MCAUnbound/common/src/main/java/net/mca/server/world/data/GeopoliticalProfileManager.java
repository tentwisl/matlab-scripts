package net.mca.server.world.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * World-level persistence for per-NPC political profile data (LFP/NFP/Compass/Nation membership).
 */
public class GeopoliticalProfileManager extends PersistentState {

    public record PoliticalProfile(double lfp, double nfp, double nationalist, double communist,
                                   double authoritarian, double libertarian, UUID nationFounder) {
    }

    private final Map<UUID, PoliticalProfile> profiles = new HashMap<>();

    public static GeopoliticalProfileManager get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(GeopoliticalProfileManager::fromNbt, GeopoliticalProfileManager::new, "mca_unbound_geopolitical_profiles");
    }

    public Optional<PoliticalProfile> getProfile(UUID villagerId) {
        return Optional.ofNullable(profiles.get(villagerId));
    }

    public void setProfile(UUID villagerId, PoliticalProfile profile) {
        profiles.put(villagerId, profile);
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, PoliticalProfile> e : profiles.entrySet()) {
            NbtCompound row = new NbtCompound();
            row.putUuid("villager", e.getKey());
            row.putDouble("lfp", e.getValue().lfp());
            row.putDouble("nfp", e.getValue().nfp());
            row.putDouble("nationalist", e.getValue().nationalist());
            row.putDouble("communist", e.getValue().communist());
            row.putDouble("authoritarian", e.getValue().authoritarian());
            row.putDouble("libertarian", e.getValue().libertarian());
            if (e.getValue().nationFounder() != null) row.putUuid("nationFounder", e.getValue().nationFounder());
            list.add(row);
        }
        nbt.put("profiles", list);
        return nbt;
    }

    public static GeopoliticalProfileManager fromNbt(NbtCompound nbt) {
        GeopoliticalProfileManager manager = new GeopoliticalProfileManager();
        NbtList list = nbt.getList("profiles", 10);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound row = list.getCompound(i);
            manager.profiles.put(row.getUuid("villager"),
                    new PoliticalProfile(
                            row.getDouble("lfp"),
                            row.getDouble("nfp"),
                            row.getDouble("nationalist"),
                            row.getDouble("communist"),
                            row.getDouble("authoritarian"),
                            row.getDouble("libertarian"),
                            row.containsUuid("nationFounder") ? row.getUuid("nationFounder") : null
                    ));
        }
        return manager;
    }
}
