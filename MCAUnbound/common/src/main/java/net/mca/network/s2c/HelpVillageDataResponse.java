package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.block.TownHallBlockEntity;
import net.mca.network.NbtDataMessage;
import net.mca.server.world.data.VillageRequest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
import java.util.UUID;

/**
 * S2C: Sends the village supply request data to the client.
 * Used by Help Village screen to display open and pending requests.
 */
public class HelpVillageDataResponse extends NbtDataMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    public HelpVillageDataResponse(TownHallBlockEntity townHall, UUID playerUUID) {
        super(buildNbt(townHall, playerUUID));
    }

    private static NbtCompound buildNbt(TownHallBlockEntity townHall, UUID playerUUID) {
        NbtCompound root = new NbtCompound();
        root.putLong("blockPos", townHall.getPos().asLong());
        root.putString("leaderName", townHall.getLeaderName());

        // Open requests (not yet accepted)
        NbtList openList = new NbtList();
        for (VillageRequest r : townHall.getOpenRequests()) {
            NbtCompound entry = r.save();
            entry.putString("itemName", friendlyName(r.getItemId()));
            openList.add(entry);
        }
        root.put("openRequests", openList);

        // Pending requests (accepted by any player)
        NbtList pendingList = new NbtList();
        for (VillageRequest r : townHall.getPendingRequests()) {
            NbtCompound entry = r.save();
            entry.putString("itemName", friendlyName(r.getItemId()));
            entry.putBoolean("mine", playerUUID.equals(r.getAcceptedBy()));
            pendingList.add(entry);
        }
        root.put("pendingRequests", pendingList);

        return root;
    }

    /** Converts item identifier path to a friendly display name. */
    private static String friendlyName(String itemId) {
        var item = Registries.ITEM.get(new Identifier(itemId));
        return item.getName().getString();
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleHelpVillageResponse(this);
    }
}
