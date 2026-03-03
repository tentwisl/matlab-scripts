package net.mca.nation.congress;

import net.minecraft.nbt.NbtCompound;

import java.io.Serial;
import java.io.Serializable;

/** An open congress vote that hasn't resolved yet. */
public class PendingVote implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public enum VoteType { DECLARE_WAR, PEACE_TREATY, NEW_POLICY, TRADE_AGREEMENT, NO_CONFIDENCE, ANNEX_CITY }

    private final VoteType type;
    private final String subjectKey;   // NationId, policyKey, cityId etc.
    private int votesFor = 0;
    private int votesAgainst = 0;
    private long deadlineDay;

    public PendingVote(VoteType type, String subjectKey, long deadlineDay) {
        this.type        = type;
        this.subjectKey  = subjectKey;
        this.deadlineDay = deadlineDay;
    }

    public PendingVote(NbtCompound nbt) {
        type         = VoteType.valueOf(nbt.getString("type"));
        subjectKey   = nbt.getString("subjectKey");
        votesFor     = nbt.getInt("votesFor");
        votesAgainst = nbt.getInt("votesAgainst");
        deadlineDay  = nbt.getLong("deadlineDay");
    }

    public NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type",        type.name());
        nbt.putString("subjectKey",  subjectKey);
        nbt.putInt("votesFor",       votesFor);
        nbt.putInt("votesAgainst",   votesAgainst);
        nbt.putLong("deadlineDay",   deadlineDay);
        return nbt;
    }

    public VoteType getType()         { return type; }
    public String getSubjectKey()     { return subjectKey; }
    public int getVotesFor()          { return votesFor; }
    public int getVotesAgainst()      { return votesAgainst; }
    public long getDeadlineDay()      { return deadlineDay; }
    public void castFor()             { votesFor++; }
    public void castAgainst()         { votesAgainst++; }
    public boolean isPassed()         { return votesFor > votesAgainst; }
    public boolean isExpired(long day){ return day >= deadlineDay; }
}
