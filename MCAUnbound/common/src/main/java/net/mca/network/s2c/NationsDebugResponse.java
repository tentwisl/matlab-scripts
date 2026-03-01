package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.nation.NationManager;
import net.mca.network.NbtDataMessage;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.io.Serial;

/**
 * S2C: full nations debug data for the Nations Debug Screen.
 * Contains all nations, all relations, all cities, and AI seeded state.
 */
public class NationsDebugResponse extends NbtDataMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    public NationsDebugResponse(NationManager nm) {
        super(buildNbt(nm));
    }

    private static NbtCompound buildNbt(NationManager nm) {
        NbtCompound root = new NbtCompound();

        NbtList nations = new NbtList();
        nm.getAllNations().forEach(n -> nations.add(n.save()));
        root.put("nations", nations);

        NbtList relations = new NbtList();
        nm.getAllRelations().forEach(r -> relations.add(r.save()));
        root.put("relations", relations);

        NbtList cities = new NbtList();
        nm.getAllCities().forEach(c -> cities.add(c.save()));
        root.put("cities", cities);

        root.putBoolean("aiSeeded", nm.isAiNationsSeeded());
        root.putInt("nationCount", nm.getAllNations().size());

        return root;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleNationsDebugResponse(this);
    }
}
