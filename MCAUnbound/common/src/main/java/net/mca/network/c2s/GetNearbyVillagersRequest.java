package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.network.s2c.NearbyVillagersResponse;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * C2S: Request a list of MCA villagers within SEARCH_RADIUS blocks of the player.
 * Used by the Diplomacy Table cabinet/congress tabs to populate the appointment list.
 */
public class GetNearbyVillagersRequest implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final double SEARCH_RADIUS = 32.0;

    @Override
    public void receive(ServerPlayerEntity player) {
        List<NearbyVillagersResponse.VillagerEntry> entries = new ArrayList<>();

        player.getServerWorld()
                .getEntitiesByClass(
                        VillagerEntityMCA.class,
                        player.getBoundingBox().expand(SEARCH_RADIUS),
                        e -> true
                )
                .forEach(v -> {
                    String name       = v.getName().getString();
                    String profession = v.getVillagerData().getProfession().toString();
                    entries.add(new NearbyVillagersResponse.VillagerEntry(v.getUuid(), name, profession));
                });

        NetworkHandler.sendToPlayer(new NearbyVillagersResponse(entries), player);
    }
}
