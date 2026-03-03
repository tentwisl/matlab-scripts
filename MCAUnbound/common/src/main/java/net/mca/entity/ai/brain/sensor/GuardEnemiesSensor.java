package net.mca.entity.ai.brain.sensor;

import com.google.common.collect.ImmutableSet;
import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.LivingTargetCache;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.Optional;
import java.util.Set;

public class GuardEnemiesSensor extends Sensor<LivingEntity> {
    @Override
    public Set<MemoryModuleType<?>> getOutputMemoryModules() {
        return ImmutableSet.of(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY.get());
    }

    @Override
    protected void sense(ServerWorld world, LivingEntity entity) {
        entity.getBrain().remember(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY.get(), this.getNearestHostile(entity));
    }

    private Optional<LivingEntity> getNearestHostile(LivingEntity entity) {
        return getVisibleMobs(entity).flatMap((list) -> list.stream(this::isHostile).min((a, b) -> this.compareEntities(entity, a, b)));
    }

    private Optional<LivingTargetCache> getVisibleMobs(LivingEntity entity) {
        return entity.getBrain().getOptionalMemory(MemoryModuleType.VISIBLE_MOBS);
    }

    private int compareEntities(LivingEntity entity, LivingEntity hostile1, LivingEntity hostile2) {
        int i = getPriority(hostile2, entity) - getPriority(hostile1, entity);
        return i == 0 ? compareDistances(entity, hostile1, hostile2) : i;
    }

    private int compareDistances(LivingEntity entity, LivingEntity hostile1, LivingEntity hostile2) {
        return MathHelper.floor(hostile1.squaredDistanceTo(entity) - hostile2.squaredDistanceTo(entity));
    }

    private int getPriority(LivingEntity entity, LivingEntity guard) {
        if (entity instanceof VillagerEntityMCA villager) {
            return villager.isHostile() ? 10 : -1;
        } else if (guard != null && entity instanceof MobEntity && (((MobEntity)entity).getTarget() == guard)) {
            //priority is irrelevant if this entity is currently an active threat
            return 9;
        } else {
            Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
            if (Config.getInstance().guardsTargetEntities.containsKey(id.toString())) {
                return Config.getInstance().guardsTargetEntities.get(id.toString());
            } else if (Config.getInstance().guardsTargetMonsters && entity instanceof Monster) {
                return 3;
            } else {
                return -1;
            }
        }
    }

    private boolean isHostile(LivingEntity entity) {
        return getPriority(entity, null) >= 0;
    }
}
