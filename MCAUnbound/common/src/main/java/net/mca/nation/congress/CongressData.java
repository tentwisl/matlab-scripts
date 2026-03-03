package net.mca.nation.congress;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Manages the congress / senate for one nation. */
public class CongressData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Max seats; grows with nation size. */
    public static final int DEFAULT_SEATS = 5;

    private final List<CongressMember> members  = new ArrayList<>();
    private final List<PendingVote>    votes    = new ArrayList<>();
    /** In-game day of last election cycle. */
    private long lastElectionDay = 0;
    /** How many in-game days between elections (default 30). */
    private long electionCycleLength = 30;
    /** Day current leader's mandate expires (Republic only). */
    private long leaderMandateEndDay = 0;

    public CongressData() {}

    public CongressData(NbtCompound nbt) {
        NbtList mList = nbt.getList("members", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < mList.size(); i++) members.add(new CongressMember(mList.getCompound(i)));

        NbtList vList = nbt.getList("votes", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < vList.size(); i++) votes.add(new PendingVote(vList.getCompound(i)));

        lastElectionDay      = nbt.getLong("lastElectionDay");
        electionCycleLength  = nbt.getLong("electionCycleLength");
        leaderMandateEndDay  = nbt.getLong("leaderMandateEndDay");
    }

    public NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        NbtList mList = new NbtList();
        members.forEach(m -> mList.add(m.save()));
        nbt.put("members", mList);

        NbtList vList = new NbtList();
        votes.forEach(v -> vList.add(v.save()));
        nbt.put("votes", vList);

        nbt.putLong("lastElectionDay",     lastElectionDay);
        nbt.putLong("electionCycleLength", electionCycleLength);
        nbt.putLong("leaderMandateEndDay", leaderMandateEndDay);
        return nbt;
    }

    public List<CongressMember> getMembers()          { return members; }
    public List<PendingVote>    getPendingVotes()      { return votes; }
    public long getLastElectionDay()                   { return lastElectionDay; }
    public void setLastElectionDay(long day)           { this.lastElectionDay = day; }
    public long getElectionCycleLength()               { return electionCycleLength; }
    public void setElectionCycleLength(long len)       { this.electionCycleLength = len; }
    public long getLeaderMandateEndDay()               { return leaderMandateEndDay; }
    public void setLeaderMandateEndDay(long day)       { this.leaderMandateEndDay = day; }

    public void addMember(CongressMember m)            { members.add(m); }
    public void removeMember(UUID entityId)            { members.removeIf(m -> m.getEntityId().equals(entityId)); }
    public boolean isMember(UUID entityId)             { return members.stream().anyMatch(m -> m.getEntityId().equals(entityId)); }
    public int size()                                  { return members.size(); }

    public void addVote(PendingVote v)                 { votes.add(v); }
    public void resolveVote(PendingVote v)             { votes.remove(v); }

    /** Simulate NPC congress members voting (simplified). */
    public void simulateNpcVotes(PendingVote vote) {
        for (CongressMember m : members) {
            if (!m.isPlayer()) {
                // NPCs vote based on loyalty to ruling party + some randomness
                boolean voteFor = (Math.random() * 100) < m.getLoyalty();
                if (voteFor) vote.castFor(); else vote.castAgainst();
            }
        }
    }
}
