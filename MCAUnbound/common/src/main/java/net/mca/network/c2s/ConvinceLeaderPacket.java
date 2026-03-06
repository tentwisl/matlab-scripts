package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Memories;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.Serial;
import java.util.Optional;
import java.util.UUID;

/**
 * C2S: Player attempts to convince an NPC village leader to join the nation
 * they are forming.
 *
 * <p>Requirements (all checked server-side):
 * <ol>
 *   <li>The target entity is an MCA villager.</li>
 *   <li>That villager IS the NPC leader of their home village.</li>
 *   <li>The player has >= 100 hearts with that villager.</li>
 *   <li>The player is the player-leader of a DIFFERENT village.</li>
 *   <li>The leader's village has not already been convinced by someone.</li>
 * </ol>
 */
public class ConvinceLeaderPacket implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID villagerUUID;

    public ConvinceLeaderPacket(UUID villagerUUID) {
        this.villagerUUID = villagerUUID;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Entity entity = world.getEntity(villagerUUID);

        if (!(entity instanceof VillagerEntityMCA villager)) {
            player.sendMessage(Text.literal("Could not find that villager.").formatted(Formatting.RED), false);
            return;
        }

        VillageManager vm = VillageManager.get(world);

        // Villager must be the NPC leader of their home village
        Optional<Village> leaderVillageOpt = villager.getResidency().getHomeVillage();
        if (leaderVillageOpt.isEmpty()) {
            player.sendMessage(Text.literal("This villager doesn't belong to a village.").formatted(Formatting.RED), false);
            return;
        }
        Village leaderVillage = leaderVillageOpt.get();
        if (!villagerUUID.equals(leaderVillage.getNpcLeaderUUID())) {
            player.sendMessage(Text.literal("This villager is not a village leader.").formatted(Formatting.RED), false);
            return;
        }

        // Player must have >= 100 hearts with this villager
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);
        if (memory.getHearts() < net.mca.MCAUnboundConfig.get().nationAllianceHeartThreshold) {
            player.sendMessage(Text.literal("You need 100 ♥ with this leader first.").formatted(Formatting.RED), false);
            return;
        }

        // Player must be the player-leader of their OWN village (a different one)
        boolean playerIsLeader = vm.findVillages(v -> true)
                .filter(v -> v.getId() != leaderVillage.getId())
                .anyMatch(v -> v.getTownHallPos() != null
                        && world.getBlockEntity(v.getTownHallPos()) instanceof net.mca.block.TownHallBlockEntity th
                        && th.hasLeader() && th.isLeaderPlayer()
                        && player.getUuid().equals(th.getLeaderUUID()));

        if (!playerIsLeader) {
            player.sendMessage(Text.literal("You must be a village leader yourself first.").formatted(Formatting.RED), false);
            return;
        }

        // Already convinced?
        if (leaderVillage.getConvincedByPlayerUUID() != null) {
            if (leaderVillage.getConvincedByPlayerUUID().equals(player.getUuid())) {
                player.sendMessage(Text.literal("You have already convinced this leader.").formatted(Formatting.YELLOW), false);
            } else {
                player.sendMessage(Text.literal("This leader has already agreed to join another player's nation.").formatted(Formatting.RED), false);
            }
            return;
        }

        // All checks passed — mark the village as convinced
        leaderVillage.setConvincedByPlayerUUID(player.getUuid());
        player.sendMessage(Text.literal(villager.getName().getString() + " has agreed to join your nation!")
                .formatted(Formatting.GREEN), false);

        // Count how many leaders the player has now convinced
        long count = vm.findVillages(v -> true)
                .filter(v -> player.getUuid().equals(v.getConvincedByPlayerUUID()))
                .count();
        if (count >= 2) {
            player.sendMessage(
                    Text.literal("You have convinced " + count + " leaders! Return to your Town Hall to form the nation.")
                            .formatted(Formatting.GOLD),
                    false);
        }
    }
}
