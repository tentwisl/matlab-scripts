package net.mca.nation;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Nation-layer metadata attached to an existing MCA Village.
 * The villageId corresponds to Village.getId() in VillageManager.
 */
public class CityData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int villageId;
    private UUID districtId;       // null = independent city-state
    private UUID owningNationId;   // null = independent
    /** Per-player opinion: -100 (hostile) to +100 (allied). Default 0. */
    private final Map<UUID, Integer> playerOpinion = new HashMap<>();
    /** Average mood of all MCA residents (derived, updated on tick) */
    private int baseOpinion = 0;
    private CityTier tier = CityTier.HAMLET;
    /** Whether this city is independent (no nation affiliation) */
    private boolean isIndependent = true;
    /**
     * How strongly the city resists annexation (0-100).
     * Higher value = citizens will revolt if annexed.
     * Decays 2 points/day after annexation.
     */
    private int independenceScore = 50;
    /** In-game day of last market refresh */
    private long lastMarketRefreshDay = 0;

    public CityData(int villageId) {
        this.villageId = villageId;
    }

    public CityData(NbtCompound nbt) {
        villageId         = nbt.getInt("villageId");
        if (nbt.contains("districtId"))     districtId     = nbt.getUuid("districtId");
        if (nbt.contains("owningNationId")) owningNationId = nbt.getUuid("owningNationId");
        baseOpinion       = nbt.getInt("baseOpinion");
        tier              = CityTier.valueOf(nbt.getString("tier"));
        isIndependent     = nbt.getBoolean("isIndependent");
        independenceScore = nbt.getInt("independenceScore");
        lastMarketRefreshDay = nbt.getLong("lastMarketRefreshDay");

        NbtList opinionList = nbt.getList("playerOpinion", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < opinionList.size(); i++) {
            NbtCompound entry = opinionList.getCompound(i);
            playerOpinion.put(entry.getUuid("player"), entry.getInt("opinion"));
        }
    }

    public NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("villageId",       villageId);
        if (districtId     != null) nbt.putUuid("districtId",     districtId);
        if (owningNationId != null) nbt.putUuid("owningNationId", owningNationId);
        nbt.putInt("baseOpinion",     baseOpinion);
        nbt.putString("tier",         tier.name());
        nbt.putBoolean("isIndependent", isIndependent);
        nbt.putInt("independenceScore", independenceScore);
        nbt.putLong("lastMarketRefreshDay", lastMarketRefreshDay);
        NbtList opinionList = new NbtList();
        playerOpinion.forEach((player, opinion) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("player",  player);
            entry.putInt("opinion",  opinion);
            opinionList.add(entry);
        });
        nbt.put("playerOpinion", opinionList);
        return nbt;
    }

    public int getVillageId()                               { return villageId; }
    public Optional<UUID> getDistrictId()                   { return Optional.ofNullable(districtId); }
    public void setDistrictId(UUID id)                      { this.districtId = id; }
    public Optional<UUID> getOwningNationId()               { return Optional.ofNullable(owningNationId); }
    public void setOwningNationId(UUID id)                  { this.owningNationId = id; this.isIndependent = (id == null); }
    public int getPlayerOpinion(UUID playerId)              { return playerOpinion.getOrDefault(playerId, 0); }
    public void adjustPlayerOpinion(UUID playerId, int delta) {
        int current = playerOpinion.getOrDefault(playerId, 0);
        playerOpinion.put(playerId, Math.max(-100, Math.min(100, current + delta)));
    }
    public int getBaseOpinion()                             { return baseOpinion; }
    public void setBaseOpinion(int v)                       { this.baseOpinion = Math.max(-100, Math.min(100, v)); }
    public CityTier getTier()                               { return tier; }
    public void setTier(CityTier tier)                      { this.tier = tier; }
    public boolean isIndependent()                          { return isIndependent; }
    public int getIndependenceScore()                       { return independenceScore; }
    public void decayIndependenceScore(int amount)          { this.independenceScore = Math.max(0, independenceScore - amount); }
    public long getLastMarketRefreshDay()                   { return lastMarketRefreshDay; }
    public void setLastMarketRefreshDay(long day)           { this.lastMarketRefreshDay = day; }
}
