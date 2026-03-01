package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.nation.*;
import net.mca.network.s2c.DiplomacyTableDataResponse;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * C2S: player submits the nation founding form from the Diplomacy Table.
 * Validates that the player doesn't already own a nation, then creates it.
 *
 * {@code flagLayersEncoded} is a comma-separated list of "PATTERN:COLORHEX" tokens,
 * e.g. {@code "SOLID:3355AA,STRIPE_V:FFDD00,BORDER:FFFFFF"}.
 */
public class FoundNationPacket implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String nationName;
    private final String governmentTypeName;
    private final String flagColorHex;
    private final String flagLayersEncoded;

    public FoundNationPacket(String nationName, String governmentTypeName,
                             String flagColorHex, String flagLayersEncoded) {
        this.nationName         = nationName;
        this.governmentTypeName = governmentTypeName;
        this.flagColorHex       = flagColorHex;
        this.flagLayersEncoded  = flagLayersEncoded;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        NationManager nm = NationManager.get(player.getServerWorld());

        // One nation per player
        if (nm.getPlayerNation(player.getUuid()).isPresent()) {
            return;
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
        nation.setFlagLayers(decodeLayers(flagLayersEncoded, flagColorHex));

        // Republic mandate timer
        if (gov.hasElections()) {
            nation.getCongress().setLeaderMandateEndDay(
                    nation.getFoundedDay() + nation.getCongress().getElectionCycleLength()
            );
        }

        nm.addNation(nation);
        NetworkHandler.sendToPlayer(new DiplomacyTableDataResponse(nm, player.getUuid()), player);
    }

    private static List<FlagLayer> decodeLayers(String encoded, String baseColor) {
        List<FlagLayer> layers = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            layers.add(new FlagLayer(FlagLayer.Pattern.SOLID, baseColor));
            return layers;
        }
        for (String token : encoded.split(",")) {
            String[] parts = token.split(":");
            if (parts.length != 2) continue;
            try {
                FlagLayer.Pattern pattern = FlagLayer.Pattern.valueOf(parts[0].trim());
                layers.add(new FlagLayer(pattern, parts[1].trim()));
            } catch (IllegalArgumentException ignored) { /* skip unknown patterns */ }
        }
        if (layers.isEmpty()) layers.add(new FlagLayer(FlagLayer.Pattern.SOLID, baseColor));
        return layers;
    }
}
