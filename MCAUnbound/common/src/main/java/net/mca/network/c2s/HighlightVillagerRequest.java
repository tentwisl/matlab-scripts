package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.server.world.data.TownHallHighlightManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.Serial;
import java.util.UUID;

/**
 * C2S: Player clicks a villager name in the Town Hall screen.
 * Toggles persistent highlight tracking for that villager.
 */
public class HighlightVillagerRequest implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long mostSigBits;
    private final long leastSigBits;

    public HighlightVillagerRequest(UUID villagerUUID) {
        this.mostSigBits = villagerUUID.getMostSignificantBits();
        this.leastSigBits = villagerUUID.getLeastSignificantBits();
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        UUID villagerUUID = new UUID(mostSigBits, leastSigBits);
        boolean enabled = TownHallHighlightManager.toggle(villagerUUID);
        player.sendMessage(Text.literal(enabled ? "Town Hall highlight enabled." : "Town Hall highlight disabled."), false);
    }
}
