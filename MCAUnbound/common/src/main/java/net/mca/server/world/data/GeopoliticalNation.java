package net.mca.server.world.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GeopoliticalNation {
    public enum GovernmentType { UNSET, MONARCHY, REPUBLIC }

    private final UUID founder;
    private String name;
    private boolean capitalToggle;
    private int populationCap;
    private GovernmentType governmentType = GovernmentType.UNSET;
    private final Set<Integer> villageIds = new HashSet<>();

    public GeopoliticalNation(UUID founder, String name) {
        this.founder = founder;
        this.name = name;
        this.populationCap = 100;
    }

    public UUID founder() { return founder; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isCapitalToggle() { return capitalToggle; }
    public void setCapitalToggle(boolean capitalToggle) { this.capitalToggle = capitalToggle; }
    public int getPopulationCap() { return populationCap; }
    public void setPopulationCap(int populationCap) { this.populationCap = Math.max(1, populationCap); }
    public GovernmentType getGovernmentType() { return governmentType; }
    public void setGovernmentType(GovernmentType governmentType) { if (this.governmentType == GovernmentType.UNSET) this.governmentType = governmentType; }
    public Set<Integer> getVillageIds() { return villageIds; }

    public double computeReputationPercent(int residentCount, int heartSum, int birthedChildrenCount) {
        int pool = Math.max(1, residentCount * 100);
        int adjusted = heartSum + (birthedChildrenCount * 25);
        return Math.max(0.0D, Math.min(1.0D, adjusted / (double) pool));
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("founder", founder);
        nbt.putString("name", name);
        nbt.putBoolean("capitalToggle", capitalToggle);
        nbt.putInt("populationCap", populationCap);
        nbt.putInt("governmentType", governmentType.ordinal());
        NbtList villages = new NbtList();
        villageIds.forEach(id -> villages.add(NbtString.of(Integer.toString(id))));
        nbt.put("villageIds", villages);
        return nbt;
    }

    public static GeopoliticalNation fromNbt(NbtCompound nbt) {
        GeopoliticalNation nation = new GeopoliticalNation(nbt.getUuid("founder"), nbt.getString("name"));
        nation.capitalToggle = nbt.getBoolean("capitalToggle");
        nation.populationCap = nbt.getInt("populationCap");
        nation.governmentType = GovernmentType.values()[Math.min(nbt.getInt("governmentType"), GovernmentType.values().length - 1)];
        NbtList villages = nbt.getList("villageIds", 8);
        for (int i = 0; i < villages.size(); i++) nation.villageIds.add(Integer.parseInt(villages.getString(i)));
        return nation;
    }
}
