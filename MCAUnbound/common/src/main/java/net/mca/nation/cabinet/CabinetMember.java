package net.mca.nation.cabinet;

import net.minecraft.nbt.NbtCompound;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/** A single cabinet position filled by an MCA NPC. */
public class CabinetMember implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID npcId;
    private final CabinetRole role;
    /** When true the NPC autonomously acts on the role without player input. */
    private boolean autonomous = false;
    /** Loyalty 0-100; drops if player ignores their advice, rises on success. */
    private int loyalty = 50;

    public CabinetMember(UUID npcId, CabinetRole role) {
        this.npcId = npcId;
        this.role  = role;
    }

    public CabinetMember(NbtCompound nbt) {
        npcId     = nbt.getUuid("npcId");
        role      = CabinetRole.valueOf(nbt.getString("role"));
        autonomous = nbt.getBoolean("autonomous");
        loyalty   = nbt.getInt("loyalty");
    }

    public NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("npcId",     npcId);
        nbt.putString("role",    role.name());
        nbt.putBoolean("autonomous", autonomous);
        nbt.putInt("loyalty",    loyalty);
        return nbt;
    }

    public UUID getNpcId()               { return npcId; }
    public CabinetRole getRole()         { return role; }
    public boolean isAutonomous()        { return autonomous; }
    public void setAutonomous(boolean v) { this.autonomous = v; }
    public int getLoyalty()              { return loyalty; }
    public void adjustLoyalty(int delta) { this.loyalty = Math.max(0, Math.min(100, loyalty + delta)); }
}
