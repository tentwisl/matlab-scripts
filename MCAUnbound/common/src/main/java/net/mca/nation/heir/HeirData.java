package net.mca.nation.heir;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores information about a nation's designated Heir/Regent NPC.
 * Heirs do NOT die of old age — only combat/assassination can remove them.
 * The heir can designate their own preferred successor based on their AI personality.
 */
public class HeirData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** UUID of the MCA NPC designated as heir */
    private UUID heirNpcId;
    /** Whether the heir is currently actively managing the nation (player stepped aside or is inactive) */
    private boolean isActive = false;
    /** Player-configured instructions for autonomous management */
    private final List<String> regentDirectives = new ArrayList<>();
    /** Game tick when the player last touched their Diplomacy Table (for inactivity detection) */
    private long lastActiveTick = 0;
    /**
     * The heir's own preferred successor — set automatically by heir AI based on their personality.
     * If the heir is killed before the player designates a new heir, this NPC auto-assumes the role.
     */
    private UUID preferredSuccessorId;

    public HeirData() {}

    public HeirData(NbtCompound nbt) {
        if (nbt.contains("heirNpcId")) {
            heirNpcId = nbt.getUuid("heirNpcId");
        }
        if (nbt.contains("preferredSuccessorId")) {
            preferredSuccessorId = nbt.getUuid("preferredSuccessorId");
        }
        isActive       = nbt.getBoolean("isActive");
        lastActiveTick = nbt.getLong("lastActiveTick");
        NbtList list   = nbt.getList("directives", 8 /* NbtString */);
        for (int i = 0; i < list.size(); i++) {
            regentDirectives.add(list.getString(i));
        }
    }

    public NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        if (heirNpcId != null)           nbt.putUuid("heirNpcId", heirNpcId);
        if (preferredSuccessorId != null) nbt.putUuid("preferredSuccessorId", preferredSuccessorId);
        nbt.putBoolean("isActive",      isActive);
        nbt.putLong("lastActiveTick",   lastActiveTick);
        NbtList list = new NbtList();
        regentDirectives.forEach(d -> list.add(NbtString.of(d)));
        nbt.put("directives", list);
        return nbt;
    }

    public boolean hasHeir()                    { return heirNpcId != null; }
    public Optional<UUID> getHeirNpcId()        { return Optional.ofNullable(heirNpcId); }
    public void setHeirNpcId(UUID id)           { this.heirNpcId = id; }
    public void clearHeir()                     { this.heirNpcId = null; this.isActive = false; }
    public boolean isActive()                   { return isActive; }
    public void setActive(boolean active)       { this.isActive = active; }
    public long getLastActiveTick()             { return lastActiveTick; }
    public void setLastActiveTick(long tick)    { this.lastActiveTick = tick; }
    public List<String> getRegentDirectives()   { return regentDirectives; }
    public void addDirective(String d)          { if (!regentDirectives.contains(d)) regentDirectives.add(d); }
    public void removeDirective(String d)       { regentDirectives.remove(d); }
    public Optional<UUID> getPreferredSuccessorId() { return Optional.ofNullable(preferredSuccessorId); }
    public void setPreferredSuccessorId(UUID id){ this.preferredSuccessorId = id; }
}
