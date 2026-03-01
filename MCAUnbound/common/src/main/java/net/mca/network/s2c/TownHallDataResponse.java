package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.nation.CityData;
import net.mca.nation.NationManager;
import net.mca.network.NbtDataMessage;
import net.mca.server.world.data.Village;
import net.minecraft.nbt.NbtCompound;

import java.io.Serial;
import java.util.UUID;

/**
 * S2C: data for the Town Hall screen.
 * Contains village info, city metadata, and owning nation summary.
 */
public class TownHallDataResponse extends NbtDataMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    public TownHallDataResponse(Village village, CityData city, NationManager nm, UUID playerId) {
        super(buildNbt(village, city, nm, playerId));
    }

    private static NbtCompound buildNbt(Village village, CityData city, NationManager nm, UUID playerId) {
        NbtCompound root = new NbtCompound();

        if (village != null) {
            root.put("village", village.save());
            root.putString("villageName", village.getName());
        }

        root.put("city", city.save());

        // Owning nation summary
        city.getOwningNationId().flatMap(nm::getNation).ifPresent(nation -> {
            NbtCompound ns = new NbtCompound();
            ns.putString("name",      nation.getName());
            ns.putString("flagColor", nation.getFlagColorHex());
            ns.putString("gov",       nation.getGovernmentType().name());
            ns.putInt("science",      nation.getStats().getScienceLevel());
            root.put("owningNation",  ns);
        });

        // Player's reputation in this city
        root.putInt("playerReputation", city.getPlayerOpinion(playerId));

        return root;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleTownHallResponse(this);
    }
}
