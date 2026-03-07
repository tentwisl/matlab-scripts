package net.mca.aw2;

import net.mca.aw2.worksite.WorksiteBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * Tracks all worksite production across the world for integration with
 * the nation economy system. This is the bridge between AW2's per-block
 * production and MCA's per-city/per-nation economic simulation.
 *
 * The NationTickManager queries this tracker to compute weekly production
 * values for each city, feeding into NationStats.weeklyProduction.
 */
public class WorksiteProductionTracker extends PersistentState {
    private static final String DATA_KEY = "mca_aw2_production";

    // City (village UUID) -> resource type -> cumulative production count
    private final Map<UUID, Map<String, Integer>> cityProduction = new HashMap<>();

    // Worksite position -> city UUID mapping
    private final Map<BlockPos, UUID> worksiteCityMap = new HashMap<>();

    // Registered worksites (position -> type name)
    private final Map<BlockPos, String> registeredWorksites = new HashMap<>();

    public WorksiteProductionTracker() {
        super();
    }

    public static WorksiteProductionTracker get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager()
                .getOrCreate(WorksiteProductionTracker::fromNbt,
                        WorksiteProductionTracker::new, DATA_KEY);
    }

    /**
     * Registers a worksite as belonging to a specific city.
     * Called when a worksite is placed within a village's bounds.
     */
    public void registerWorksite(BlockPos worksitePos, UUID cityId, String worksiteType) {
        worksiteCityMap.put(worksitePos, cityId);
        registeredWorksites.put(worksitePos, worksiteType);
        markDirty();
    }

    /**
     * Unregisters a worksite (when broken).
     */
    public void unregisterWorksite(BlockPos worksitePos) {
        worksiteCityMap.remove(worksitePos);
        registeredWorksites.remove(worksitePos);
        markDirty();
    }

    /**
     * Records production from a worksite. Called periodically by worksites
     * to feed their production logs into the city economy.
     */
    public void recordProduction(BlockPos worksitePos, Map<String, Integer> production) {
        UUID cityId = worksiteCityMap.get(worksitePos);
        if (cityId == null) return;

        Map<String, Integer> cityProd = cityProduction.computeIfAbsent(cityId, k -> new HashMap<>());
        for (Map.Entry<String, Integer> entry : production.entrySet()) {
            cityProd.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        markDirty();
    }

    /**
     * Gets the total production for a city since last reset.
     * Used by NationTickManager to compute weekly economic output.
     */
    public Map<String, Integer> getCityProduction(UUID cityId) {
        return Collections.unmodifiableMap(
                cityProduction.getOrDefault(cityId, Collections.emptyMap()));
    }

    /**
     * Gets all worksites registered to a specific city.
     */
    public List<BlockPos> getWorksitesForCity(UUID cityId) {
        List<BlockPos> result = new ArrayList<>();
        for (Map.Entry<BlockPos, UUID> entry : worksiteCityMap.entrySet()) {
            if (entry.getValue().equals(cityId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public int getRegisteredWorksiteCount() {
        return registeredWorksites.size();
    }

    public Map<BlockPos, String> getRegisteredWorksitesSnapshot() {
        return Collections.unmodifiableMap(new HashMap<>(registeredWorksites));
    }

    /**
     * Gets the count of active worksites for a city, by type.
     */
    public Map<String, Integer> getWorksiteCountsByType(UUID cityId) {
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<BlockPos, UUID> entry : worksiteCityMap.entrySet()) {
            if (entry.getValue().equals(cityId)) {
                String type = registeredWorksites.get(entry.getKey());
                if (type != null) {
                    counts.merge(type, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    /**
     * Resets the production log for all cities. Called at the start of
     * each weekly economic cycle.
     */
    public void resetAllProduction() {
        cityProduction.clear();
        markDirty();
    }

    /**
     * Calculates a numeric economic value from a city's production.
     * Used to feed into NationStats.economicOutput.
     */
    public int calculateCityEconomicValue(UUID cityId) {
        Map<String, Integer> prod = cityProduction.getOrDefault(cityId, Collections.emptyMap());
        int value = 0;
        for (Map.Entry<String, Integer> entry : prod.entrySet()) {
            value += entry.getValue() * getResourceValue(entry.getKey());
        }
        return value;
    }

    private int getResourceValue(String resourceId) {
        // Assign economic values to common resources
        if (resourceId.contains("diamond")) return 10;
        if (resourceId.contains("emerald")) return 8;
        if (resourceId.contains("gold")) return 5;
        if (resourceId.contains("iron")) return 3;
        if (resourceId.contains("copper")) return 2;
        if (resourceId.contains("coal") || resourceId.contains("redstone")) return 2;
        if (resourceId.contains("lapis")) return 3;
        if (resourceId.contains("log") || resourceId.contains("plank")) return 1;
        if (resourceId.contains("cobblestone") || resourceId.contains("stone")) return 1;
        if (resourceId.contains("wheat") || resourceId.contains("carrot")) return 1;
        if (resourceId.contains("beef") || resourceId.contains("pork")) return 2;
        if (resourceId.contains("leather") || resourceId.contains("wool")) return 2;
        if (resourceId.contains("cod") || resourceId.contains("salmon")) return 1;
        return 1; // Default value
    }

    // ==================== SERIALIZATION ====================

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        // Worksite -> city map
        NbtList wsMapList = new NbtList();
        for (Map.Entry<BlockPos, UUID> entry : worksiteCityMap.entrySet()) {
            NbtCompound tag = new NbtCompound();
            tag.putInt("X", entry.getKey().getX());
            tag.putInt("Y", entry.getKey().getY());
            tag.putInt("Z", entry.getKey().getZ());
            tag.putUuid("CityId", entry.getValue());
            tag.putString("Type", registeredWorksites.getOrDefault(entry.getKey(), "unknown"));
            wsMapList.add(tag);
        }
        nbt.put("WorksiteMap", wsMapList);

        // City production
        NbtCompound prodNbt = new NbtCompound();
        for (Map.Entry<UUID, Map<String, Integer>> entry : cityProduction.entrySet()) {
            NbtCompound cityProdNbt = new NbtCompound();
            for (Map.Entry<String, Integer> res : entry.getValue().entrySet()) {
                cityProdNbt.putInt(res.getKey(), res.getValue());
            }
            prodNbt.put(entry.getKey().toString(), cityProdNbt);
        }
        nbt.put("CityProduction", prodNbt);

        return nbt;
    }

    public static WorksiteProductionTracker fromNbt(NbtCompound nbt) {
        WorksiteProductionTracker tracker = new WorksiteProductionTracker();

        if (nbt.contains("WorksiteMap")) {
            NbtList wsMapList = nbt.getList("WorksiteMap", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < wsMapList.size(); i++) {
                NbtCompound tag = wsMapList.getCompound(i);
                BlockPos pos = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
                UUID cityId = tag.getUuid("CityId");
                String type = tag.getString("Type");
                tracker.worksiteCityMap.put(pos, cityId);
                tracker.registeredWorksites.put(pos, type);
            }
        }

        if (nbt.contains("CityProduction")) {
            NbtCompound prodNbt = nbt.getCompound("CityProduction");
            for (String key : prodNbt.getKeys()) {
                UUID cityId = UUID.fromString(key);
                NbtCompound cityProdNbt = prodNbt.getCompound(key);
                Map<String, Integer> cityProd = new HashMap<>();
                for (String resKey : cityProdNbt.getKeys()) {
                    cityProd.put(resKey, cityProdNbt.getInt(resKey));
                }
                tracker.cityProduction.put(cityId, cityProd);
            }
        }

        return tracker;
    }
}
