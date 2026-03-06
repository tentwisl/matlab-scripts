package net.mca.network.c2s;

import net.mca.block.TownHallBlockEntity;
import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Memories;
import net.mca.MCAUnboundConfig;
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

        // Already has a player leader — cannot override
        if (townHall.hasLeader() && townHall.isLeaderPlayer()) {
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

        // Dynamic success chance = total current hearts / max potential hearts.
        int totalCurrentHearts = residents.stream()
                .map(v -> v.getVillagerBrain().getMemoriesForPlayer(player).getHearts())
                .reduce(0, Integer::sum);
        int maxPotentialHearts = Math.max(1, residents.size() * 100);
        double successChance = Math.max(0.0D, Math.min(1.0D, totalCurrentHearts / (double) maxPotentialHearts));

        // Start election; resolution is deferred and processed from VillageManager tick.
        townHall.startElection(world.getTime(), player.getUuid(), player.getName().getString(), successChance);
        player.sendMessage(Text.literal("Election petition submitted. Results will arrive by mail in about 1 Minecraft day."), false);
    }
}
