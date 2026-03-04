package net.mca.server.world.data;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.world.ServerWorld;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/** Keeps Town Hall highlight toggles active until disabled. */
public final class TownHallHighlightManager {
    private static final Set<UUID> HIGHLIGHTED = new HashSet<>();

    private TownHallHighlightManager() {
    }

    public static boolean toggle(UUID villagerId) {
        if (HIGHLIGHTED.contains(villagerId)) {
            HIGHLIGHTED.remove(villagerId);
            return false;
        }
        HIGHLIGHTED.add(villagerId);
        return true;
    }

    public static boolean isHighlighted(UUID villagerId) {
        return HIGHLIGHTED.contains(villagerId);
    }

    public static void tick(ServerWorld world) {
        Iterator<UUID> it = HIGHLIGHTED.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            if (!(world.getEntity(uuid) instanceof LivingEntity living)) {
                it.remove();
                continue;
            }
            // Refresh short glow continuously while toggled on.
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, false, false), null);
        }
    }
}
