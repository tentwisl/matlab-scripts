package net.mca.server.world.data;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Keeps Town Hall highlight toggles active until disabled. */
public final class TownHallHighlightManager {
    private static final Set<UUID> HIGHLIGHTED = new HashSet<>();

    private TownHallHighlightManager() {
    }

    public static boolean toggle(UUID villagerId, ServerWorld sourceWorld) {
        if (HIGHLIGHTED.contains(villagerId)) {
            HIGHLIGHTED.remove(villagerId);
            if (sourceWorld.getEntity(villagerId) instanceof LivingEntity living) {
                living.setGlowing(false);
            }
            return false;
        }
        HIGHLIGHTED.add(villagerId);
        return true;
    }

    public static boolean isHighlighted(UUID villagerId) {
        return HIGHLIGHTED.contains(villagerId);
    }

    public static void tick(ServerWorld world) {
        for (UUID uuid : HIGHLIGHTED) {
            if (world.getEntity(uuid) instanceof LivingEntity living) {
                living.setGlowing(true);
            }
        }
    }
}
