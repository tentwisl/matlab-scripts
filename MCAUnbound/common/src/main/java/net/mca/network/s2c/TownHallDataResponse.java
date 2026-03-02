package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.block.TownHallBlockEntity;
import net.mca.network.NbtDataMessage;
import net.mca.server.world.data.Village;
import net.minecraft.nbt.NbtCompound;

import java.io.Serial;
import java.util.Map;
import java.util.UUID;

/**
 * S2C: data for the Town Hall screen.
 * Contains village info, per-villager hearts for the requesting player,
 * and leadership state.
 */
public class TownHallDataResponse extends NbtDataMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    public TownHallDataResponse(Village village, TownHallBlockEntity townHall,
                                 Map<UUID, Integer> villagerHearts,
                                 Map<UUID, String> villagerNames,
                                 UUID playerId) {
        super(buildNbt(village, townHall, villagerHearts, villagerNames, playerId));
    }

    private static NbtCompound buildNbt(Village village, TownHallBlockEntity townHall,
                                         Map<UUID, Integer> villagerHearts,
                                         Map<UUID, String> villagerNames,
                                         UUID playerId) {
        NbtCompound root = new NbtCompound();

        if (village != null) {
            root.putString("villageName", village.getName());
            root.putInt("villageId", village.getId());
            root.putInt("population", village.getPopulation());
            root.putInt("maxPopulation", village.getMaxPopulation());
        }

        // Per-villager hearts for the requesting player
        NbtCompound heartsNbt = new NbtCompound();
        for (Map.Entry<UUID, Integer> entry : villagerHearts.entrySet()) {
            heartsNbt.putInt(entry.getKey().toString(), entry.getValue());
        }
        root.put("villagerHearts", heartsNbt);

        // Villager names
        NbtCompound namesNbt = new NbtCompound();
        for (Map.Entry<UUID, String> entry : villagerNames.entrySet()) {
            namesNbt.putString(entry.getKey().toString(), entry.getValue());
        }
        root.put("villagerNames", namesNbt);

        // Leadership data
        root.putBoolean("hasLeader", townHall.hasLeader());
        if (townHall.hasLeader()) {
            root.putUuid("leaderUUID", townHall.getLeaderUUID());
            root.putString("leaderName", townHall.getLeaderName());
        }
        root.putInt("electionState", townHall.getElectionState().ordinal());

        // Is the requesting player the leader?
        root.putBoolean("isPlayerLeader", townHall.hasLeader()
                && townHall.getLeaderUUID().equals(playerId));

        // Block position so the screen can send AttemptLeadershipRequest back
        root.putLong("blockPos", townHall.getPos().asLong());

        return root;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleTownHallResponse(this);
    }
}
