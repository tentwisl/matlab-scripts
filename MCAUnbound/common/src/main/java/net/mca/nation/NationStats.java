package net.mca.nation;

import net.minecraft.nbt.NbtCompound;

import java.io.Serial;
import java.io.Serializable;

public class NationStats implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private int totalPopulation    = 0;
    private int militaryStrength   = 0;
    private int economicOutput     = 0;  // resources/week
    private int culturalInfluence  = 0;
    private int scienceLevel       = 0;  // 0-10
    private int approvalRating     = 50; // 0-100; leader performance
    private int politicalCapital   = 0;  // earned through diplomacy/achievements
    private int approvalBelowThresholdDays = 0; // consecutive days below 20%

    public NationStats() {}

    public NationStats(NbtCompound nbt) {
        totalPopulation              = nbt.getInt("totalPopulation");
        militaryStrength             = nbt.getInt("militaryStrength");
        economicOutput               = nbt.getInt("economicOutput");
        culturalInfluence            = nbt.getInt("culturalInfluence");
        scienceLevel                 = nbt.getInt("scienceLevel");
        approvalRating               = nbt.getInt("approvalRating");
        politicalCapital             = nbt.getInt("politicalCapital");
        approvalBelowThresholdDays   = nbt.getInt("approvalBelowThresholdDays");
    }

    public NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("totalPopulation",            totalPopulation);
        nbt.putInt("militaryStrength",           militaryStrength);
        nbt.putInt("economicOutput",             economicOutput);
        nbt.putInt("culturalInfluence",          culturalInfluence);
        nbt.putInt("scienceLevel",               scienceLevel);
        nbt.putInt("approvalRating",             approvalRating);
        nbt.putInt("politicalCapital",           politicalCapital);
        nbt.putInt("approvalBelowThresholdDays", approvalBelowThresholdDays);
        return nbt;
    }

    public int getTotalPopulation()        { return totalPopulation; }
    public void setTotalPopulation(int v)  { this.totalPopulation = Math.max(0, v); }
    public int getMilitaryStrength()       { return militaryStrength; }
    public void setMilitaryStrength(int v) { this.militaryStrength = Math.max(0, v); }
    public int getEconomicOutput()         { return economicOutput; }
    public void setEconomicOutput(int v)   { this.economicOutput = v; }
    public int getCulturalInfluence()      { return culturalInfluence; }
    public void setCulturalInfluence(int v){ this.culturalInfluence = v; }
    public int getScienceLevel()           { return Math.max(0, Math.min(10, scienceLevel)); }
    public void setScienceLevel(int v)     { this.scienceLevel = Math.max(0, Math.min(10, v)); }
    public int getApprovalRating()         { return approvalRating; }
    public void setApprovalRating(int v)   { this.approvalRating = Math.max(0, Math.min(100, v)); }
    public int getPoliticalCapital()       { return politicalCapital; }
    public void addPoliticalCapital(int v) { this.politicalCapital = Math.max(0, politicalCapital + v); }
    public boolean spendPoliticalCapital(int cost) {
        if (politicalCapital >= cost) { politicalCapital -= cost; return true; }
        return false;
    }
    public int getApprovalBelowThresholdDays()        { return approvalBelowThresholdDays; }
    public void setApprovalBelowThresholdDays(int v)  { this.approvalBelowThresholdDays = v; }
    public void incrementApprovalBelowThresholdDays() { this.approvalBelowThresholdDays++; }
    public void resetApprovalBelowThresholdDays()     { this.approvalBelowThresholdDays = 0; }
    public void addMilitaryStrength(int v)            { this.militaryStrength = Math.max(0, militaryStrength + v); }
    public void addEconomicOutput(int v)              { this.economicOutput += v; }
}
