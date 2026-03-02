package net.mca.network.c2s;

import net.mca.block.TownHallBlockEntity;
import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Memories;
import net.mca.nation.MCAUnboundConfig;
import net.mca.server.world.data.PlayerSaveData;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
import java.util.List;
import java.util.Optional;

/**
 * C2S: Player clicks "Attempt Leadership" in the Town Hall screen.
 * Validates heart requirements, starts election timer, sends mail upon completion.
 */
public class AttemptLeadershipRequest implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long posLong;

    public AttemptLeadershipRequest(BlockPos pos) {
        this.posLong = pos.asLong();
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        BlockPos pos = BlockPos.fromLong(posLong);
        ServerWorld world = player.getServerWorld();
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof TownHallBlockEntity townHall)) return;

        MCAUnboundConfig config = MCAUnboundConfig.get();

        // Already has a leader
        if (townHall.hasLeader()) {
            player.sendMessage(Text.translatable("townhall.election.already_has_leader"), false);
            return;
        }

        // Election already in progress
        if (townHall.getElectionState() != TownHallBlockEntity.ElectionState.NONE) {
            player.sendMessage(Text.translatable("townhall.election.already_in_progress"), false);
            return;
        }

        // Get village
        int villageId = townHall.getVillageId();
        VillageManager vm = VillageManager.get(world);
        Optional<Village> optVillage = vm.getOrEmpty(villageId);
        if (optVillage.isEmpty()) {
            player.sendMessage(Text.translatable("townhall.election.no_village"), false);
            return;
        }

        Village village = optVillage.get();
        List<VillagerEntityMCA> residents = village.getResidents(world);

        if (residents.isEmpty()) {
            player.sendMessage(Text.translatable("townhall.election.no_residents"), false);
            return;
        }

        // Check heart requirements: player must have >= leaderHeartThreshold with ALL loaded residents
        int threshold = config.leaderHeartThreshold;
        int belowThreshold = 0;
        for (VillagerEntityMCA villager : residents) {
            Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);
            if (memory.getHearts() < threshold) {
                belowThreshold++;
            }
        }

        if (belowThreshold > 0) {
            player.sendMessage(Text.translatable("townhall.election.low_hearts",
                    belowThreshold, threshold), false);
            return;
        }

        // Start election! Set to PENDING, then resolve after wait period
        townHall.startElection(world.getTime());

        // Calculate result immediately but deliver via mail after wait
        boolean success = world.random.nextInt(100) >= config.leaderElectionBaseFailChance;

        // Schedule the result via a simple approach: store result in block entity
        // and resolve when the time comes. For now, resolve immediately with mail.
        PlayerSaveData psd = PlayerSaveData.get(player);

        if (success) {
            townHall.setLeader(player.getUuid(), player.getName().getString());
            psd.sendLetter(List.of("Congratulations! The villagers of " + village.getName()
                    + " have chosen you as their leader. Visit the Town Hall to see your new role."));
            player.sendMessage(Text.translatable("townhall.election.started_success"), false);
        } else {
            townHall.clearLeader();
            psd.sendLetter(List.of("Unfortunately, the villagers of " + village.getName()
                    + " have not chosen you as their leader this time. Try building more trust."));
            player.sendMessage(Text.translatable("townhall.election.started_failure"), false);
        }
    }
}
