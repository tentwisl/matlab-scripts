package net.mca.block;

import net.mca.server.world.data.VillageRequest;
import net.mca.util.NbtHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Block entity for the Town Hall.
 * Tracks village ID, leader, election state, and village supply requests.
 */
public class TownHallBlockEntity extends BlockEntity {

    private int villageId = -1;
    private UUID leaderUUID = null;
    private String leaderName = "";
    private boolean leaderIsPlayer = false;

    public enum ElectionState { NONE, PENDING }
    private ElectionState electionState = ElectionState.NONE;
    private long electionStartTick = 0;
    private UUID pendingCandidateUUID = null;
    private String pendingCandidateName = "";
    private double pendingSuccessChance = 0.0D;

    /** Available requests players can accept. */
    private final List<VillageRequest> openRequests   = new ArrayList<>();
    /** Requests a player has accepted but not yet fully delivered. */
    private final List<VillageRequest> pendingRequests = new ArrayList<>();
    private int nextRequestId = 1;

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypesMCA.TOWN_HALL.get(), pos, state);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        villageId = nbt.getInt("villageId");
        if (nbt.containsUuid("leaderUUID")) leaderUUID = nbt.getUuid("leaderUUID");
        leaderName = nbt.getString("leaderName");
        leaderIsPlayer = nbt.getBoolean("leaderIsPlayer");
        electionState = ElectionState.values()[Math.min(nbt.getInt("electionState"), ElectionState.values().length - 1)];
        electionStartTick = nbt.getLong("electionStartTick");
        if (nbt.containsUuid("pendingCandidateUUID")) pendingCandidateUUID = nbt.getUuid("pendingCandidateUUID");
        pendingCandidateName = nbt.getString("pendingCandidateName");
        pendingSuccessChance = nbt.getDouble("pendingSuccessChance");
        nextRequestId = nbt.getInt("nextRequestId");

        openRequests.clear();
        NbtList openList = nbt.getList("openRequests", 10);
        for (int i = 0; i < openList.size(); i++) openRequests.add(new VillageRequest(openList.getCompound(i)));

        pendingRequests.clear();
        NbtList pendingList = nbt.getList("pendingRequests", 10);
        for (int i = 0; i < pendingList.size(); i++) pendingRequests.add(new VillageRequest(pendingList.getCompound(i)));
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("villageId", villageId);
        if (leaderUUID != null) nbt.putUuid("leaderUUID", leaderUUID);
        nbt.putString("leaderName", leaderName);
        nbt.putBoolean("leaderIsPlayer", leaderIsPlayer);
        nbt.putInt("electionState", electionState.ordinal());
        nbt.putLong("electionStartTick", electionStartTick);
        if (pendingCandidateUUID != null) nbt.putUuid("pendingCandidateUUID", pendingCandidateUUID);
        nbt.putString("pendingCandidateName", pendingCandidateName);
        nbt.putDouble("pendingSuccessChance", pendingSuccessChance);
        nbt.putInt("nextRequestId", nextRequestId);
        nbt.put("openRequests",    NbtHelper.fromList(openRequests,    VillageRequest::save));
        nbt.put("pendingRequests", NbtHelper.fromList(pendingRequests, VillageRequest::save));
    }

    // ── Village ───────────────────────────────────────────────────────────────

    public int getVillageId()        { return villageId; }
    public void setVillageId(int id) { this.villageId = id; markDirty(); }

    // ── Leadership ────────────────────────────────────────────────────────────

    public UUID   getLeaderUUID()   { return leaderUUID; }
    public String getLeaderName()   { return leaderName; }
    public boolean isLeaderPlayer() { return leaderIsPlayer; }
    public boolean hasLeader()      { return leaderUUID != null; }

    public void setPlayerLeader(UUID uuid, String name) {
        leaderUUID = uuid; leaderName = name; leaderIsPlayer = true; clearElectionPendingData(); markDirty();
    }

    public void setNpcLeader(UUID uuid, String name) {
        leaderUUID = uuid; leaderName = name; leaderIsPlayer = false; clearElectionPendingData();
        generateOpenRequests();
        markDirty();
    }

    public void clearLeader() {
        leaderUUID = null; leaderName = ""; leaderIsPlayer = false; clearElectionPendingData(); markDirty();
    }

    // ── Election ──────────────────────────────────────────────────────────────

    public ElectionState getElectionState()  { return electionState; }
    public long getElectionStartTick()       { return electionStartTick; }

    public void startElection(long tick, UUID candidateUUID, String candidateName, double successChance) {
        electionState = ElectionState.PENDING;
        electionStartTick = tick;
        pendingCandidateUUID = candidateUUID;
        pendingCandidateName = candidateName == null ? "" : candidateName;
        pendingSuccessChance = Math.max(0.0D, Math.min(1.0D, successChance));
        markDirty();
    }

    public UUID getPendingCandidateUUID() { return pendingCandidateUUID; }
    public String getPendingCandidateName() { return pendingCandidateName; }
    public double getPendingSuccessChance() { return pendingSuccessChance; }

    public void clearElectionPendingData() {
        pendingCandidateUUID = null;
        pendingCandidateName = "";
        pendingSuccessChance = 0.0D;
        electionState = ElectionState.NONE;
        electionStartTick = 0;
        markDirty();
    }

    // ── Requests ──────────────────────────────────────────────────────────────

    public List<VillageRequest> getOpenRequests()    { return openRequests; }
    public List<VillageRequest> getPendingRequests() { return pendingRequests; }

    /** Generate the initial pool of village supply requests. */
    public void generateOpenRequests() {
        openRequests.clear();
        Random rng = new Random();
        for (int i = 0; i < 3; i++) {
            openRequests.add(VillageRequest.generate(nextRequestId++, rng));
        }
        markDirty();
    }

    /** Move a request from open → pending after a player accepts it. */
    public boolean acceptRequest(int requestId, UUID playerUUID) {
        for (VillageRequest r : openRequests) {
            if (r.getId() == requestId && !r.isAccepted()) {
                r.accept(playerUUID);
                openRequests.remove(r);
                pendingRequests.add(r);
                markDirty();
                return true;
            }
        }
        return false;
    }

    /** Called when fulfillment changes. If complete, remove and regenerate. Returns true if now complete. */
    public boolean tryFulfill(int requestId, net.minecraft.server.network.ServerPlayerEntity player) {
        for (VillageRequest r : pendingRequests) {
            if (r.getId() == requestId && player.getUuid().equals(r.getAcceptedBy())) {
                r.tryFulfill(player);
                markDirty();
                if (r.isComplete()) {
                    pendingRequests.remove(r);
                    openRequests.add(VillageRequest.generate(nextRequestId++, new Random()));
                    markDirty();
                    return true;
                }
                return false;
            }
        }
        return false;
    }
}
