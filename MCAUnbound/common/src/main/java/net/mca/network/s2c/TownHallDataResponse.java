package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.block.TownHallBlockEntity;
import net.mca.network.NbtDataMessage;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageRequest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

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
                                 UUID playerId, int convincedLeaderCount) {
        super(buildNbt(village, townHall, villagerHearts, villagerNames, playerId, convincedLeaderCount));
    }

    private static NbtCompound buildNbt(Village village, TownHallBlockEntity townHall,
                                         Map<UUID, Integer> villagerHearts,
                                         Map<UUID, String> villagerNames,
                                         UUID playerId, int convincedLeaderCount) {
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
            root.putBoolean("leaderIsPlayer", townHall.isLeaderPlayer());
        }
        root.putInt("electionState", townHall.getElectionState().ordinal());

        // Is the requesting player currently the player-leader?
        root.putBoolean("isPlayerLeader", townHall.hasLeader()
                && townHall.isLeaderPlayer()
                && townHall.getLeaderUUID().equals(playerId));

        // Block position so the screen can send AttemptLeadershipRequest / FormNationPacket back
        root.putLong("blockPos", townHall.getPos().asLong());

        // Nation formation: how many other village leaders the player has convinced
        root.putInt("convincedLeaderCount", convincedLeaderCount);

        // Pending requests for display in the Town Hall screen
        NbtList pendingList = new NbtList();
        for (VillageRequest r : townHall.getPendingRequests()) {
            NbtCompound entry = r.save();
            var item = Registries.ITEM.get(new Identifier(r.getItemId()));
            entry.putString("itemName", item.getName().getString());
            pendingList.add(entry);
        }
        root.put("pendingRequests", pendingList);

        return root;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleTownHallResponse(this);
    }
}
