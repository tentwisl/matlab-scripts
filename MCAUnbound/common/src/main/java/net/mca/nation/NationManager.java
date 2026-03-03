package net.mca.nation;

import net.mca.util.WorldUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.*;
import java.util.stream.Stream;

/**
 * World-saved store for all nation data.
 * Accessed via {@link #get(ServerWorld)}.
 */
public class NationManager extends PersistentState {

    // ── Storage maps ──────────────────────────────────────────────────────────
    private final Map<UUID, Nation>           nations    = new HashMap<>();
    private final Map<UUID, District>         districts  = new HashMap<>();
    /** Keyed by MCA villageId. */
    private final Map<Integer, CityData>      cities     = new HashMap<>();
    /** Keyed by DiplomacyRelation.key(a, b). */
    private final Map<String, DiplomacyRelation> relations = new HashMap<>();

    /** Whether AI nations have been seeded for this world. */
    private boolean aiNationsSeeded = false;

    private final ServerWorld world;

    // ── Singleton access ──────────────────────────────────────────────────────

    public static NationManager get(ServerWorld world) {
        return WorldUtils.loadData(
                world,
                nbt -> new NationManager(world, nbt),
                NationManager::new,
                "mca_nations"
        );
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    NationManager(ServerWorld world) {
        this.world = world;
    }

    NationManager(ServerWorld world, NbtCompound nbt) {
        this.world = world;
        aiNationsSeeded = nbt.getBoolean("aiNationsSeeded");

        NbtList nList = nbt.getList("nations", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < nList.size(); i++) {
            Nation n = new Nation(nList.getCompound(i));
            nations.put(n.getNationId(), n);
        }

        NbtList dList = nbt.getList("districts", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < dList.size(); i++) {
            District d = new District(dList.getCompound(i));
            districts.put(d.getDistrictId(), d);
        }

        NbtList cList = nbt.getList("cities", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < cList.size(); i++) {
            CityData c = new CityData(cList.getCompound(i));
            cities.put(c.getVillageId(), c);
        }

        NbtList rList = nbt.getList("relations", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < rList.size(); i++) {
            DiplomacyRelation r = new DiplomacyRelation(rList.getCompound(i));
            relations.put(r.getKey(), r);
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("aiNationsSeeded", aiNationsSeeded);

        NbtList nList = new NbtList();
        nations.values().forEach(n -> nList.add(n.save()));
        nbt.put("nations", nList);

        NbtList dList = new NbtList();
        districts.values().forEach(d -> dList.add(d.save()));
        nbt.put("districts", dList);

        NbtList cList = new NbtList();
        cities.values().forEach(c -> cList.add(c.save()));
        nbt.put("cities", cList);

        NbtList rList = new NbtList();
        relations.values().forEach(r -> rList.add(r.save()));
        nbt.put("relations", rList);

        return nbt;
    }

    // ── Nation operations ─────────────────────────────────────────────────────

    public void addNation(Nation n)                         { nations.put(n.getNationId(), n); markDirty(); }
    public void removeNation(UUID id)                       { nations.remove(id); markDirty(); }
    public Optional<Nation> getNation(UUID id)              { return Optional.ofNullable(nations.get(id)); }
    public Collection<Nation> getAllNations()               { return nations.values(); }
    public Stream<Nation> streamNations()                   { return nations.values().stream(); }
    public boolean hasNations()                             { return !nations.isEmpty(); }

    // ── District operations ───────────────────────────────────────────────────

    public void addDistrict(District d)                     { districts.put(d.getDistrictId(), d); markDirty(); }
    public void removeDistrict(UUID id)                     { districts.remove(id); markDirty(); }
    public Optional<District> getDistrict(UUID id)          { return Optional.ofNullable(districts.get(id)); }
    public Collection<District> getAllDistricts()           { return districts.values(); }

    public List<District> getDistrictsForNation(UUID nationId) {
        return districts.values().stream()
                .filter(d -> nationId.equals(d.getParentNationId()))
                .collect(java.util.stream.Collectors.toList());
    }

    // ── City operations ───────────────────────────────────────────────────────

    public CityData getOrCreateCity(int villageId) {
        return cities.computeIfAbsent(villageId, CityData::new);
    }

    public Optional<CityData> getCity(int villageId)        { return Optional.ofNullable(cities.get(villageId)); }
    public void saveCity(CityData c)                         { cities.put(c.getVillageId(), c); markDirty(); }
    public Collection<CityData> getAllCities()              { return cities.values(); }

    public List<CityData> getCitiesForNation(UUID nationId) {
        return cities.values().stream()
                .filter(c -> c.getOwningNationId().filter(nationId::equals).isPresent())
                .collect(java.util.stream.Collectors.toList());
    }

    // ── Diplomacy operations ──────────────────────────────────────────────────

    public DiplomacyRelation getOrCreateRelation(UUID a, UUID b) {
        String key = DiplomacyRelation.key(a, b);
        return relations.computeIfAbsent(key, k -> new DiplomacyRelation(a, b));
    }

    public Optional<DiplomacyRelation> getRelation(UUID a, UUID b) {
        return Optional.ofNullable(relations.get(DiplomacyRelation.key(a, b)));
    }

    public Collection<DiplomacyRelation> getAllRelations()  { return relations.values(); }

    public List<DiplomacyRelation> getRelationsFor(UUID nationId) {
        return relations.values().stream()
                .filter(r -> r.getNationA().equals(nationId) || r.getNationB().equals(nationId))
                .collect(java.util.stream.Collectors.toList());
    }

    // ── AI seeding flag ───────────────────────────────────────────────────────

    public boolean isAiNationsSeeded()           { return aiNationsSeeded; }
    public void    setAiNationsSeeded(boolean v) { aiNationsSeeded = v; markDirty(); }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public void tick() {
        NationTickManager.tick(world, this);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Find the nation that owns a given villageId, if any. */
    public Optional<Nation> getNationForCity(int villageId) {
        return getCity(villageId)
                .flatMap(c -> c.getOwningNationId())
                .flatMap(this::getNation);
    }

    /** Find the player's own founded nation (there can only be one per player). */
    public Optional<Nation> getPlayerNation(UUID playerId) {
        return nations.values().stream()
                .filter(n -> n.getOwnerPlayerId().filter(playerId::equals).isPresent())
                .findFirst();
    }
}
