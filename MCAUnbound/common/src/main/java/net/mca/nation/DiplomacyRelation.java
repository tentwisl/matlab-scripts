package net.mca.nation;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DiplomacyRelation implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID nationA;
    private final UUID nationB;
    private DiplomacyStatus status;
    /** -100 (deep hostility) to +100 (deep trust) */
    private int trustValue;
    private final List<String> eventLog = new ArrayList<>();

    public DiplomacyRelation(UUID nationA, UUID nationB) {
        this.nationA    = nationA;
        this.nationB    = nationB;
        this.status     = DiplomacyStatus.NEUTRAL;
        this.trustValue = 0;
    }

    public DiplomacyRelation(NbtCompound nbt) {
        nationA    = nbt.getUuid("nationA");
        nationB    = nbt.getUuid("nationB");
        status     = DiplomacyStatus.valueOf(nbt.getString("status"));
        trustValue = nbt.getInt("trustValue");
        NbtList log = nbt.getList("log", 8);
        for (int i = 0; i < log.size(); i++) eventLog.add(log.getString(i));
    }

    public NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("nationA",   nationA);
        nbt.putUuid("nationB",   nationB);
        nbt.putString("status",  status.name());
        nbt.putInt("trustValue", trustValue);
        NbtList log = new NbtList();
        eventLog.forEach(e -> log.add(NbtString.of(e)));
        nbt.put("log", log);
        return nbt;
    }

    /** Returns the canonical key for indexing this relation: smaller UUID first */
    public static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    /** Instance version of {@link #key(UUID, UUID)} for this relation. */
    public String getKey() { return key(nationA, nationB); }

    public UUID getNationA()                     { return nationA; }
    public UUID getNationB()                     { return nationB; }
    public UUID getOther(UUID self)              { return self.equals(nationA) ? nationB : nationA; }
    public DiplomacyStatus getStatus()           { return status; }
    public void setStatus(DiplomacyStatus s)     { this.status = s; }
    public int getTrustValue()                   { return trustValue; }
    public void adjustTrust(int delta) {
        trustValue = Math.max(-100, Math.min(100, trustValue + delta));
        // Auto-update status from trust thresholds
        if (status != DiplomacyStatus.AT_WAR) {
            if      (trustValue >= 60)  status = DiplomacyStatus.FRIENDLY;
            else if (trustValue >= 20)  status = DiplomacyStatus.NEUTRAL;
            else if (trustValue < -20)  status = DiplomacyStatus.HOSTILE;
        }
    }
    public List<String> getEventLog()            { return eventLog; }
    public void logEvent(String event) {
        eventLog.add(event);
        if (eventLog.size() > 20) eventLog.remove(0); // keep last 20 events
    }
}
