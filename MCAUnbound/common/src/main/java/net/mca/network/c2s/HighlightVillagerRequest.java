package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.io.Serial;
import java.util.Optional;
import java.util.UUID;

/**
 * C2S: Player clicks a villager name in the Town Hall screen.
 * The server applies Glowing to the villager entity for 30 seconds
 * so the player can spot them through walls.
 */
public class HighlightVillagerRequest implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long mostSigBits;
    private final long leastSigBits;

    public HighlightVillagerRequest(UUID villagerUUID) {
        this.mostSigBits  = villagerUUID.getMostSignificantBits();
        this.leastSigBits = villagerUUID.getLeastSignificantBits();
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        UUID villagerUUID = new UUID(mostSigBits, leastSigBits);
        ServerWorld world = player.getServerWorld();

        // Find the villager in the same world — search all villages the player is near
        VillageManager vm = VillageManager.get(world);
        vm.findVillages(v -> v.hasResident(villagerUUID)).findFirst().ifPresent(village -> {
            // Entity may or may not be loaded
            Optional.ofNullable(world.getEntity(villagerUUID))
                    .ifPresent(entity -> {
                        // Apply glowing status effect for 30 seconds (600 ticks)
                        if (entity instanceof net.minecraft.entity.LivingEntity living) {
                            living.addStatusEffect(
                                    new StatusEffectInstance(StatusEffects.GLOWING, 600, 0, false, false),
                                    null
                            );
                        }
                    });
        });
    }
}
