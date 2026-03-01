package net.mca.nation.congress;

import net.minecraft.nbt.NbtCompound;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/** One NPC (or player) sitting in the congress. */
public class CongressMember implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID entityId;    // NPC UUID or player UUID
    private final boolean isPlayer;
    private String partyName = "";   // empty = independent
    /** 0-100 — how likely this member votes with the ruling party. */
    private int loyalty = 50;
    /** Civic reputation within the nation (higher = more influential). */
    private int civicRep = 0;
    /** In-game day this term expires (re-election in Republics). */
    private long termEndDay = 0;

    public CongressMember(UUID entityId, boolean isPlayer) {
        this.entityId = entityId;
        this.isPlayer = isPlayer;
    }

    public CongressMember(NbtCompound nbt) {
        entityId   = nbt.getUuid("entityId");
        isPlayer   = nbt.getBoolean("isPlayer");
        partyName  = nbt.getString("partyName");
        loyalty    = nbt.getInt("loyalty");
        civicRep   = nbt.getInt("civicRep");
        termEndDay = nbt.getLong("termEndDay");
    }

    public NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("entityId",     entityId);
        nbt.putBoolean("isPlayer",  isPlayer);
        nbt.putString("partyName",  partyName);
        nbt.putInt("loyalty",       loyalty);
        nbt.putInt("civicRep",      civicRep);
        nbt.putLong("termEndDay",   termEndDay);
        return nbt;
    }

    public UUID getEntityId()               { return entityId; }
    public boolean isPlayer()               { return isPlayer; }
    public String getPartyName()            { return partyName; }
    public void setPartyName(String p)      { this.partyName = p; }
    public int getLoyalty()                 { return loyalty; }
    public void adjustLoyalty(int delta)    { this.loyalty = Math.max(0, Math.min(100, loyalty + delta)); }
    public int getCivicRep()                { return civicRep; }
    public void addCivicRep(int v)          { this.civicRep += v; }
    public long getTermEndDay()             { return termEndDay; }
    public void setTermEndDay(long day)     { this.termEndDay = day; }
}
