package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.nation.Nation;
import net.mca.nation.NationManager;
import net.mca.network.NbtDataMessage;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.io.Serial;
import java.util.Optional;
import java.util.UUID;

/**
 * S2C: full nation data for the Diplomacy Table screen.
 * Contains the player's own nation (if any) + a list of all known nations.
 */
public class DiplomacyTableDataResponse extends NbtDataMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    public DiplomacyTableDataResponse(NationManager nm, UUID playerId) {
        super(buildNbt(nm, playerId));
    }

    private static NbtCompound buildNbt(NationManager nm, UUID playerId) {
        NbtCompound root = new NbtCompound();

        // Player's own nation
        Optional<Nation> playerNation = nm.getPlayerNation(playerId);
        playerNation.ifPresent(n -> root.put("playerNation", n.save()));
        root.putBoolean("hasPlayerNation", playerNation.isPresent());

        // All known nations (for diplomacy overview)
        NbtList allList = new NbtList();
        nm.getAllNations().forEach(n -> allList.add(n.save()));
        root.put("allNations", allList);

        // All relations (so client can show diplomatic status)
        NbtList relList = new NbtList();
        nm.getAllRelations().forEach(r -> relList.add(r.save()));
        root.put("relations", relList);

        return root;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleDiplomacyTableResponse(this);
    }
}
