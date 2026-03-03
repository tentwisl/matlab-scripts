package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.network.NbtDataMessage;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S2C: List of nearby MCA villagers (name, profession, UUID) for the appointment UI.
 */
public class NearbyVillagersResponse extends NbtDataMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Lightweight snapshot of one villager visible to the diplomacy UI. */
    public record VillagerEntry(UUID uuid, String name, String profession) {}

    public NearbyVillagersResponse(List<VillagerEntry> entries) {
        super(buildNbt(entries));
    }

    private static NbtCompound buildNbt(List<VillagerEntry> entries) {
        NbtCompound root = new NbtCompound();
        NbtList list = new NbtList();
        for (VillagerEntry e : entries) {
            NbtCompound tag = new NbtCompound();
            tag.putUuid("uuid",       e.uuid());
            tag.putString("name",     e.name());
            tag.putString("profession", e.profession());
            list.add(tag);
        }
        root.put("villagers", list);
        return root;
    }

    /** Decode the villager list on the client side. */
    public List<VillagerEntry> getEntries() {
        List<VillagerEntry> result = new ArrayList<>();
        NbtList list = getData().getList("villagers", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i);
            result.add(new VillagerEntry(
                    tag.getUuid("uuid"),
                    tag.getString("name"),
                    tag.getString("profession")
            ));
        }
        return result;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleNearbyVillagersResponse(this);
    }
}
