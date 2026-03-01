package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.nation.CityData;
import net.mca.nation.NationManager;
import net.mca.network.s2c.TownHallDataResponse;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
import java.util.Optional;

import net.mca.block.TownHallBlockEntity;

public class OpenTownHallRequest implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long posLong;

    public OpenTownHallRequest(BlockPos pos) {
        this.posLong = pos.asLong();
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        BlockPos pos = BlockPos.fromLong(posLong);
        BlockEntity be = player.getServerWorld().getBlockEntity(pos);
        if (!(be instanceof TownHallBlockEntity townHall)) return;

        int villageId = townHall.getVillageId();
        NationManager nm = NationManager.get(player.getServerWorld());
        VillageManager vm = VillageManager.get(player.getServerWorld());

        Optional<Village> village = vm.getOrEmpty(villageId);
        CityData city = nm.getOrCreateCity(villageId);

        NetworkHandler.sendToPlayer(new TownHallDataResponse(village.orElse(null), city, nm, player.getUuid()), player);
    }
}
