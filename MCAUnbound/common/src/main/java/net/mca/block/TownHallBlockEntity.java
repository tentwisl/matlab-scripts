package net.mca.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Block entity for the Town Hall.
 * Tracks village ID, current leader (NPC villager or player), and election state.
 */
public class TownHallBlockEntity extends BlockEntity {

    private int villageId = -1;

    /** UUID of the current leader (NPC villager or player). Null if vacant. */
    private UUID leaderUUID = null;
    private String leaderName = "";

    /**
     * True  = leaderUUID is a player UUID.
     * False = leaderUUID is an NPC villager UUID (default for natural villages).
     */
    private boolean leaderIsPlayer = false;

    public enum ElectionState { NONE, PENDING }

    private ElectionState electionState = ElectionState.NONE;
    private long electionStartTick = 0;

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypesMCA.TOWN_HALL.get(), pos, state);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        villageId = nbt.getInt("villageId");
        if (nbt.containsUuid("leaderUUID")) {
            leaderUUID = nbt.getUuid("leaderUUID");
        }
        leaderName = nbt.getString("leaderName");
        leaderIsPlayer = nbt.getBoolean("leaderIsPlayer");
        electionState = ElectionState.values()[Math.min(nbt.getInt("electionState"), ElectionState.values().length - 1)];
        electionStartTick = nbt.getLong("electionStartTick");
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("villageId", villageId);
        if (leaderUUID != null) {
            nbt.putUuid("leaderUUID", leaderUUID);
        }
        nbt.putString("leaderName", leaderName);
        nbt.putBoolean("leaderIsPlayer", leaderIsPlayer);
        nbt.putInt("electionState", electionState.ordinal());
        nbt.putLong("electionStartTick", electionStartTick);
    }

    // ── Village ───────────────────────────────────────────────────────────────

    public int getVillageId()          { return villageId; }
    public void setVillageId(int id)   { this.villageId = id; markDirty(); }

    // ── Leadership ────────────────────────────────────────────────────────────

    public UUID getLeaderUUID()        { return leaderUUID; }
    public String getLeaderName()      { return leaderName; }
    public boolean isLeaderPlayer()    { return leaderIsPlayer; }
    public boolean hasLeader()         { return leaderUUID != null; }

    /** Set a player as village leader (replaces any NPC or previous player leader). */
    public void setPlayerLeader(UUID uuid, String name) {
        this.leaderUUID = uuid;
        this.leaderName = name;
        this.leaderIsPlayer = true;
        this.electionState = ElectionState.NONE;
        markDirty();
    }

    /** Set an NPC villager as village leader (used by natural village auto-spawn). */
    public void setNpcLeader(UUID villagerUUID, String name) {
        this.leaderUUID = villagerUUID;
        this.leaderName = name;
        this.leaderIsPlayer = false;
        this.electionState = ElectionState.NONE;
        markDirty();
    }

    public void clearLeader() {
        this.leaderUUID = null;
        this.leaderName = "";
        this.leaderIsPlayer = false;
        this.electionState = ElectionState.NONE;
        markDirty();
    }

    // ── Election ──────────────────────────────────────────────────────────────

    public ElectionState getElectionState()  { return electionState; }
    public long getElectionStartTick()       { return electionStartTick; }

    public void startElection(long currentTick) {
        this.electionState = ElectionState.PENDING;
        this.electionStartTick = currentTick;
        markDirty();
    }
}
