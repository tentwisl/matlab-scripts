package net.mca.network.c2s;

import net.mca.block.TownHallBlockEntity;
import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MoveState;
import net.mca.server.world.data.TownHallCallManager;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
import java.util.Optional;
import java.util.UUID;

/**
 * Executes resident actions from the Town Hall UI.
 */
public class TownHallResidentActionPacket implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long townHallPos;
    private final UUID villagerUuid;
    private final String action;

    public TownHallResidentActionPacket(BlockPos townHallPos, UUID villagerUuid, String action) {
        this.townHallPos = townHallPos.asLong();
        this.villagerUuid = villagerUuid;
        this.action = action == null ? "" : action;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockEntity be = world.getBlockEntity(BlockPos.fromLong(townHallPos));
        if (!(be instanceof TownHallBlockEntity townHall)) {
            player.sendMessage(Text.literal("Town Hall data is out of date. Please reopen the menu."), false);
            return;
        }

        Optional<Village> village = VillageManager.get(world).getOrEmpty(townHall.getVillageId());
        if (village.isEmpty() || !village.get().getResidentNames().containsKey(villagerUuid)) {
            player.sendMessage(Text.literal("That villager is not registered in this Town Hall."), false);
            return;
        }

        VillagerEntityMCA villager = world.getEntity(villagerUuid) instanceof VillagerEntityMCA found ? found : null;
        if (villager == null) {
            player.sendMessage(Text.literal("Resident is not currently loaded. Move closer and retry."), false);
            return;
        }

        switch (action.toLowerCase()) {
            case "call" -> {
                if (villager.isSleeping()) {
                    player.sendMessage(Text.literal("This resident is currently sleeping."), false);
                    return;
                }
                if (villager.getAttacker() != null || villager.getTarget() != null) {
                    player.sendMessage(Text.literal("This resident is currently in combat."), false);
                    return;
                }
                if (TownHallCallManager.isCalled(villager.getUuid())) {
                    player.sendMessage(Text.literal("This resident is already responding to a call."), false);
                    return;
                }
                TownHallCallManager.startCall(villager, villager.getBlockPos(), player.getBlockPos(), world.getTime());
                player.sendMessage(Text.literal(villager.getName().getString() + " is on their way."), false);
            }
            case "follow" -> {
                villager.getVillagerBrain().setMoveState(MoveState.FOLLOW, player);
                player.sendMessage(Text.literal(villager.getName().getString() + " will follow you."), false);
            }
            case "stay" -> {
                villager.getVillagerBrain().setMoveState(MoveState.STAY, player);
                player.sendMessage(Text.literal(villager.getName().getString() + " will stay put."), false);
            }
            case "mount" -> {
                boolean mounted = villager.startRiding(player, true);
                player.sendMessage(Text.literal(mounted ? villager.getName().getString() + " mounted successfully." : "Mount failed."), false);
            }
            case "inventory" -> player.sendMessage(Text.literal("Inventory access requested for " + villager.getName().getString() + "."), false);
            default -> player.sendMessage(Text.literal("Unknown resident action: " + action), false);
        }
    }
}
