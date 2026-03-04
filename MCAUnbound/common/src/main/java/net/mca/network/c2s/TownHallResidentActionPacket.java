package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.TownHallCallManager;
import net.mca.entity.ai.MoveState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
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
        VillagerEntityMCA villager = world.getEntitiesByClass(VillagerEntityMCA.class,
                        new net.minecraft.util.math.Box(BlockPos.fromLong(townHallPos)).expand(128),
                        v -> v.getUuid().equals(villagerUuid))
                .stream().findFirst().orElse(null);

        if (villager == null) {
            player.sendMessage(Text.literal("Resident could not be located."), false);
            return;
        }

        switch (action.toLowerCase()) {
            case "call" -> {
                if (!villager.getWorld().getRegistryKey().equals(player.getWorld().getRegistryKey())) {
                    player.sendMessage(Text.literal("Cannot call residents across dimensions."), false);
                    return;
                }
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
            }
            case "follow" -> villager.getVillagerBrain().setMoveState(MoveState.FOLLOW, player);
            case "stay" -> villager.getVillagerBrain().setMoveState(MoveState.STAY, player);
            case "mount" -> villager.startRiding(player, true);
            case "inventory" -> player.sendMessage(Text.literal("Inventory access requested for " + villager.getName().getString() + "."), false);
            default -> {
            }
        }
    }
}
