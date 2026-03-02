package net.mca.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Block entity for the Town Hall.
 * Stores the village ID, current leader UUID/name, and election state.
 */
public class TownHallBlockEntity extends BlockEntity {

    private int villageId = -1;

    /** UUID of the current village leader (player). Null if no leader. */
    private UUID leaderUUID = null;
    private String leaderName = "";

    /** Election state: NONE, PENDING, ACTIVE */
    private ElectionState electionState = ElectionState.NONE;
    /** Game tick when the election was initiated */
    private long electionStartTick = 0;

    public enum ElectionState {
        NONE,
        PENDING,   // Player requested leadership, waiting period
        ACTIVE     // Election in progress (mail sent to villagers)
    }

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypesMCA.TOWN_HALL.get(), pos, state);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        villageId = nbt.getInt("villageId");
        if (nbt.containsUuid("leaderUUID")) {
            leaderUUID = nbt.getUuid("leaderUUID");
        }
        leaderName = nbt.getString("leaderName");
        electionState = ElectionState.values()[nbt.getInt("electionState")];
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
        nbt.putInt("electionState", electionState.ordinal());
        nbt.putLong("electionStartTick", electionStartTick);
    }

    public int getVillageId()               { return villageId; }
    public void setVillageId(int id)        { this.villageId = id; markDirty(); }

    public UUID getLeaderUUID()             { return leaderUUID; }
    public String getLeaderName()           { return leaderName; }

    public void setLeader(UUID uuid, String name) {
        this.leaderUUID = uuid;
        this.leaderName = name;
        this.electionState = ElectionState.NONE;
        markDirty();
    }

    public void clearLeader() {
        this.leaderUUID = null;
        this.leaderName = "";
        this.electionState = ElectionState.NONE;
        markDirty();
    }

    public boolean hasLeader()              { return leaderUUID != null; }

    public ElectionState getElectionState() { return electionState; }
    public long getElectionStartTick()      { return electionStartTick; }

    public void startElection(long currentTick) {
        this.electionState = ElectionState.PENDING;
        this.electionStartTick = currentTick;
        markDirty();
    }
}
