package net.mca.server.world.data;

import net.mca.MCAUnboundConfig;
import net.mca.block.TownHallBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Resolves pending Town Hall elections after configured wait time. */
public final class TownHallElectionResolver {

    private TownHallElectionResolver() {
    }

    public static void tick(ServerWorld world, VillageManager manager) {
        long now = world.getTime();
        long requiredTicks = 24000L * Math.max(1, MCAUnboundConfig.get().leaderElectionWaitDays);

        manager.findVillages(v -> v.getTownHallPos() != null).forEach(village -> {
            BlockPos pos = village.getTownHallPos();
            if (pos == null) return;
            if (!(world.getBlockEntity(pos) instanceof TownHallBlockEntity townHall)) return;
            if (townHall.getElectionState() != TownHallBlockEntity.ElectionState.PENDING) return;
            if (now - townHall.getElectionStartTick() < requiredTicks) return;

            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(townHall.getPendingCandidateUUID());
            boolean success = world.random.nextDouble() < townHall.getPendingSuccessChance();

            if (success) {
                townHall.setPlayerLeader(townHall.getPendingCandidateUUID(), townHall.getPendingCandidateName());
                if (player != null) {
                    PlayerSaveData.get(player).sendLetter(java.util.List.of(
                            "Appointed: The villagers of " + village.getName() + " have elected you as their leader."
                    ));
                    player.sendMessage(Text.literal("Election result: Appointed."), false);
                }
            } else {
                townHall.clearElectionPendingData();
                if (player != null) {
                    PlayerSaveData.get(player).sendLetter(java.util.List.of(
                            "Rejected: The villagers of " + village.getName() + " declined your leadership petition."
                    ));
                    player.sendMessage(Text.literal("Election result: Rejected."), false);
                }
            }
        });
    }
}
