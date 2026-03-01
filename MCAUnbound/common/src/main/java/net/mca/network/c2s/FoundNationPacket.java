package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.nation.*;
import net.mca.network.s2c.DiplomacyTableDataResponse;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Serial;
import java.util.UUID;

/**
 * C2S: player submits the nation founding form from the Diplomacy Table.
 * Validates that the player doesn't already own a nation, then creates it.
 */
public class FoundNationPacket implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String nationName;
    private final String governmentTypeName;
    private final String flagColorHex;

    public FoundNationPacket(String nationName, String governmentTypeName, String flagColorHex) {
        this.nationName         = nationName;
        this.governmentTypeName = governmentTypeName;
        this.flagColorHex       = flagColorHex;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        NationManager nm = NationManager.get(player.getServerWorld());

        // One nation per player
        if (nm.getPlayerNation(player.getUuid()).isPresent()) {
            return; // silently ignore; client should prevent this
        }

        GovernmentType gov;
        try {
            gov = GovernmentType.valueOf(governmentTypeName);
        } catch (IllegalArgumentException e) {
            return;
        }

        UUID nationId = UUID.randomUUID();
        Nation nation = new Nation(nationId, nationName.trim(), player.getUuid(), gov);
        nation.setOwnerPlayerId(player.getUuid());
        nation.setAiControlled(false);
        nation.setFlagColorHex(flagColorHex);
        nation.setFoundedDay(player.getServerWorld().getTime() / 24000L);

        // Set up Republic mandate timer if democratic
        if (gov.hasElections()) {
            nation.getCongress().setLeaderMandateEndDay(
                    nation.getFoundedDay() + nation.getCongress().getElectionCycleLength()
            );
        }

        nm.addNation(nation);

        // Send updated data back to client
        NetworkHandler.sendToPlayer(new DiplomacyTableDataResponse(nm, player.getUuid()), player);
    }
}
