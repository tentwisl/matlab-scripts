package net.mca.nation;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class District implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID districtId;
    private String name;
    private UUID governorId;
    private UUID parentNationId;
    private final List<Integer> cityVillageIds = new ArrayList<>();
    private int taxRateOverride = -1;   // -1 means inherit from nation policy
    private NationPolicy.ProductionFocus focusOverride = null;

    public District(UUID districtId, String name, UUID parentNationId) {
        this.districtId     = districtId;
        this.name           = name;
        this.parentNationId = parentNationId;
    }

    public District(NbtCompound nbt) {
        districtId     = nbt.getUuid("districtId");
        name           = nbt.getString("name");
        parentNationId = nbt.getUuid("parentNationId");
        if (nbt.contains("governorId")) governorId = nbt.getUuid("governorId");
        taxRateOverride = nbt.getInt("taxRateOverride");
        if (nbt.contains("focusOverride")) {
            focusOverride = NationPolicy.ProductionFocus.valueOf(nbt.getString("focusOverride"));
        }
        NbtList list = nbt.getList("cities", NbtElement.INT_TYPE);
        for (int i = 0; i < list.size(); i++) cityVillageIds.add(list.getInt(i));
    }

    public NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("districtId",     districtId);
        nbt.putString("name",         name);
        nbt.putUuid("parentNationId", parentNationId);
        if (governorId != null) nbt.putUuid("governorId", governorId);
        nbt.putInt("taxRateOverride", taxRateOverride);
        if (focusOverride != null) nbt.putString("focusOverride", focusOverride.name());
        NbtList list = new NbtList();
        cityVillageIds.forEach(id -> list.add(NbtInt.of(id)));
        nbt.put("cities", list);
        return nbt;
    }

    public UUID getDistrictId()                          { return districtId; }
    public String getName()                              { return name; }
    public void setName(String name)                     { this.name = name; }
    public UUID getParentNationId()                      { return parentNationId; }
    public Optional<UUID> getGovernorId()                { return Optional.ofNullable(governorId); }
    public void setGovernorId(UUID id)                   { this.governorId = id; }
    public List<Integer> getCityVillageIds()             { return cityVillageIds; }
    public void addCity(int villageId)                   { if (!cityVillageIds.contains(villageId)) cityVillageIds.add(villageId); }
    public void removeCity(int villageId)                { cityVillageIds.remove((Integer) villageId); }
    public int getTaxRateOverride()                      { return taxRateOverride; }
    public void setTaxRateOverride(int v)                { this.taxRateOverride = v; }
    public Optional<NationPolicy.ProductionFocus> getFocusOverride() { return Optional.ofNullable(focusOverride); }
    public void setFocusOverride(NationPolicy.ProductionFocus f)     { this.focusOverride = f; }
}
