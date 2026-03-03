package net.mca.nation.cabinet;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.io.Serial;
import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Holds all filled (and empty) cabinet positions for one nation. */
public class CabinetData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<CabinetRole, CabinetMember> members = new EnumMap<>(CabinetRole.class);

    public CabinetData() {}

    public CabinetData(NbtCompound nbt) {
        NbtList list = nbt.getList("members", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            CabinetMember m = new CabinetMember(list.getCompound(i));
            members.put(m.getRole(), m);
        }
    }

    public NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        NbtList list = new NbtList();
        members.values().forEach(m -> list.add(m.save()));
        nbt.put("members", list);
        return nbt;
    }

    public Optional<CabinetMember> get(CabinetRole role)    { return Optional.ofNullable(members.get(role)); }
    public void appoint(UUID npcId, CabinetRole role)        { members.put(role, new CabinetMember(npcId, role)); }
    public void dismiss(CabinetRole role)                    { members.remove(role); }
    public boolean isFilled(CabinetRole role)                { return members.containsKey(role); }
    public Map<CabinetRole, CabinetMember> getAll()          { return members; }

    /** Returns true if the given role has an autonomous NPC assigned. */
    public boolean isAutonomous(CabinetRole role) {
        CabinetMember m = members.get(role);
        return m != null && m.isAutonomous();
    }
}
