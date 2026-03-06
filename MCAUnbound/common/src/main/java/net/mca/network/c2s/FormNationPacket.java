package net.mca.network.c2s;

import net.mca.MCAUnboundConfig;
import net.mca.block.TownHallBlockEntity;
import net.mca.cobalt.network.Message;
import net.mca.item.ItemsMCA;
import net.mca.server.world.data.GeopoliticalManager;
import net.mca.server.world.data.GeopoliticalNation;
import net.mca.server.world.data.GeopoliticalProfileManager;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
import java.util.List;
import java.util.stream.Collectors;

/**
 * C2S: Player clicks "Form Nation" at their own Town Hall.
 *
 * <p>Requirements (all checked server-side):
 * <ol>
 *   <li>The Town Hall at {@code blockPos} has the player as its player-leader.</li>
 *   <li>At least 2 other villages have their {@code convincedByPlayerUUID} equal to this player.</li>
 * </ol>
 * On success the player receives one {@code nation_block} item and the
 * convinced-state on all participating villages is cleared.
 */
public class FormNationPacket implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long blockPosLong;

    public FormNationPacket(BlockPos pos) {
        this.blockPosLong = pos.asLong();
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos pos = BlockPos.fromLong(blockPosLong);
        BlockEntity be = world.getBlockEntity(pos);

        if (!(be instanceof TownHallBlockEntity townHall)) {
            player.sendMessage(Text.literal("This is not a Town Hall.").formatted(Formatting.RED), false);
            return;
        }

        // Player must be this Town Hall's player-leader
        if (!townHall.hasLeader() || !townHall.isLeaderPlayer()
                || !player.getUuid().equals(townHall.getLeaderUUID())) {
            player.sendMessage(Text.literal("You are not the leader of this village.").formatted(Formatting.RED), false);
            return;
        }

        VillageManager vm = VillageManager.get(world);

        // Gather villages whose leaders the player has convinced
        List<Village> convinced = vm.findVillages(
                v -> player.getUuid().equals(v.getConvincedByPlayerUUID())
        ).collect(Collectors.toList());

        int minAlliedVillages = Math.max(0, MCAUnboundConfig.get().nationFormationMinAlliedVillages);
        if (convinced.size() < minAlliedVillages) {
            player.sendMessage(
                    Text.literal("You need to convince more village leaders first. ("
                            + convinced.size() + "/" + minAlliedVillages + " convinced)").formatted(Formatting.RED),
                    false);
            return;
        }

        // Success — clear convinced state on all participating villages
        for (Village v : convinced) {
            v.clearConvincedByPlayer();
        }

        // Create/update geopolitical nation data.
        GeopoliticalManager geo = GeopoliticalManager.get(world);
        GeopoliticalNation nation = geo.getOrCreateNation(player.getUuid(), player.getName().getString() + "'s Nation");
        nation.getVillageIds().clear();
        nation.getVillageIds().add(townHall.getVillageId());
        for (Village v : convinced) {
            nation.getVillageIds().add(v.getId());
        }
        nation.setGovernmentType(GeopoliticalNation.GovernmentType.UNSET);
        geo.markDirty();

        GeopoliticalProfileManager profileManager = GeopoliticalProfileManager.get(world);
        vm.getOrEmpty(townHall.getVillageId()).ifPresent(v -> v.getResidents(world).forEach(resident -> {
            var existing = profileManager.getProfile(resident.getUuid()).orElse(null);
            double lfp = existing != null ? existing.lfp() : 20.0D;
            double nfp = existing != null ? existing.nfp() : 0.0D;
            double n = existing != null ? existing.nationalist() : world.random.nextDouble();
            double c = existing != null ? existing.communist() : world.random.nextDouble();
            double a = existing != null ? existing.authoritarian() : world.random.nextDouble();
            double l = existing != null ? existing.libertarian() : world.random.nextDouble();
            profileManager.setProfile(resident.getUuid(), new GeopoliticalProfileManager.PoliticalProfile(lfp, nfp, n, c, a, l, player.getUuid()));
        }));
        for (Village v : convinced) {
            v.getResidents(world).forEach(resident -> {
                var existing = profileManager.getProfile(resident.getUuid()).orElse(null);
                double lfp = existing != null ? existing.lfp() : 20.0D;
                double nfp = existing != null ? existing.nfp() : 0.0D;
                double n = existing != null ? existing.nationalist() : world.random.nextDouble();
                double c = existing != null ? existing.communist() : world.random.nextDouble();
                double a = existing != null ? existing.authoritarian() : world.random.nextDouble();
                double l = existing != null ? existing.libertarian() : world.random.nextDouble();
                profileManager.setProfile(resident.getUuid(), new GeopoliticalProfileManager.PoliticalProfile(lfp, nfp, n, c, a, l, player.getUuid()));
            });
        }

        // Give the player a Nation Block item
        ItemStack nationBlock = new ItemStack(ItemsMCA.NATION_BLOCK.get());
        if (!player.giveItemStack(nationBlock)) {
            player.dropItem(nationBlock, false);
        }

        player.sendMessage(
                Text.literal("Your nation has been founded! A Nation Block has been added to your inventory.")
                        .formatted(Formatting.GOLD),
                false);
    }
}
