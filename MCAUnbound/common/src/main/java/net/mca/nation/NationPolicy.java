package net.mca.nation;

import net.minecraft.nbt.NbtCompound;

import java.io.Serial;
import java.io.Serializable;

public class NationPolicy implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 0-50%; above 20% negatively affects town opinion */
    private int taxRate = 10;
    /** 0-100%; percentage of adults eligible for conscription */
    private int militaryLevy = 25;
    /** Overall production focus */
    private ProductionFocus focus = ProductionFocus.BALANCED;
    private boolean openBorders = false;
    private boolean conscriptionEnabled = false;
    private boolean freeTradeEnabled = true;

    public enum ProductionFocus {
        MILITARY, ECONOMIC, CULTURAL, SCIENTIFIC, BALANCED
    }

    public NationPolicy() {}

    public NationPolicy(NbtCompound nbt) {
        taxRate             = nbt.getInt("taxRate");
        militaryLevy        = nbt.getInt("militaryLevy");
        focus               = ProductionFocus.valueOf(nbt.getString("focus"));
        openBorders         = nbt.getBoolean("openBorders");
        conscriptionEnabled = nbt.getBoolean("conscriptionEnabled");
        freeTradeEnabled    = nbt.getBoolean("freeTradeEnabled");
    }

    public NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("taxRate",              taxRate);
        nbt.putInt("militaryLevy",         militaryLevy);
        nbt.putString("focus",             focus.name());
        nbt.putBoolean("openBorders",      openBorders);
        nbt.putBoolean("conscriptionEnabled", conscriptionEnabled);
        nbt.putBoolean("freeTradeEnabled", freeTradeEnabled);
        return nbt;
    }

    public int getTaxRate()               { return taxRate; }
    public void setTaxRate(int v)         { this.taxRate = Math.max(0, Math.min(50, v)); }
    public int getMilitaryLevy()          { return militaryLevy; }
    public void setMilitaryLevy(int v)    { this.militaryLevy = Math.max(0, Math.min(100, v)); }
    public ProductionFocus getFocus()     { return focus; }
    public void setFocus(ProductionFocus f) { this.focus = f; }
    public boolean isOpenBorders()        { return openBorders; }
    public void setOpenBorders(boolean v) { this.openBorders = v; }
    public boolean isConscriptionEnabled() { return conscriptionEnabled; }
    public void setConscriptionEnabled(boolean v) { this.conscriptionEnabled = v; }
    public boolean isFreeTradeEnabled()   { return freeTradeEnabled; }
    public void setFreeTradeEnabled(boolean v) { this.freeTradeEnabled = v; }
}
