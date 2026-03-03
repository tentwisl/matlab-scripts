package net.mca.entity.ai.relationship;

import net.mca.advancement.criterion.CriterionMCA;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public interface EntityRelationship {

    default Gender getGender() {
        return Gender.MALE;
    }

    default FamilyTree getFamilyTree() {
        return FamilyTree.get(getWorld());
    }

    ServerWorld getWorld();

    UUID getUUID();

    @NotNull
    FamilyTreeNode getFamilyEntry();

    default Stream<Entity> getFamily(int parents, int children) {
        return getFamilyEntry()
                .getRelatives(parents, children)
                .map(getWorld()::getEntity)
                .filter(Objects::nonNull)
                .filter(e -> !e.getUuid().equals(getUUID()));
    }

    default Stream<Entity> getParents() {
        return getFamilyEntry().streamParents().map(getWorld()::getEntity).filter(Objects::nonNull);
    }

    default Optional<Entity> getPartner() {
        // Returns the primary (most recently set) spouse entity, if present in the world.
        UUID primary = getFamilyEntry().partner();
        if (primary.equals(Util.NIL_UUID)) {
            // Fall back to first entry in the spouses set
            return getFamilyEntry().getSpouses().stream()
                    .map(getWorld()::getEntity)
                    .filter(java.util.Objects::nonNull)
                    .findFirst();
        }
        return Optional.ofNullable(getWorld().getEntity(primary));
    }

    //try to load a PlayerSaveData before loading the entity
    //that way, offline players are also considered
    default Stream<EntityRelationship> getRelationshipStream(Stream<UUID> uuids) {
        return uuids.map(uuid -> PlayerSaveData.getIfPresent(getWorld(), uuid)
                        .map(p -> (EntityRelationship)p)
                        .or(() -> EntityRelationship.of(getWorld().getEntity(uuid))))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    default void onTragedy(DamageSource cause, @Nullable BlockPos burialSite, RelationshipType type, Entity victim) {
        if (type == RelationshipType.STRANGER) {
            return; // effects don't propagate from strangers
        }

        // notify family
        if (type == RelationshipType.SELF) {
            getRelationshipStream(getFamilyEntry().streamParents())
                    .forEach(r -> r.onTragedy(cause, burialSite, RelationshipType.CHILD, victim));

            getRelationshipStream(getFamilyEntry().siblings().stream())
                    .forEach(r -> r.onTragedy(cause, burialSite, RelationshipType.SIBLING, victim));

            // Notify ALL spouses (supports polygamy)
            getRelationshipStream(getFamilyEntry().getSpouses().stream())
                    .forEach(r -> r.onTragedy(cause, burialSite, RelationshipType.SPOUSE, victim));
        }

        // Handle marriage endings for death events
        if (type == RelationshipType.SELF) {
            // This entity died – end all its marriages
            if (getRelationshipState().isMarried()) {
                endRelationShip(RelationshipState.WIDOW);
            } else {
                endRelationShip(RelationshipState.SINGLE);
            }
        } else if (type == RelationshipType.SPOUSE) {
            // A specific spouse died – remove only that spouse from our list
            if (victim != null) {
                endRelationshipWith(victim.getUuid());
            }
            // Become WIDOW only if we have no remaining spouses
            if (getFamilyEntry().getSpouses().isEmpty() && getRelationshipState().isMarried()) {
                getFamilyEntry().updatePartner(null, RelationshipState.WIDOW);
            }
        }
    }

    default void marry(Entity spouse) {
        RelationshipState state = spouse instanceof PlayerEntity ? RelationshipState.MARRIED_TO_PLAYER : RelationshipState.MARRIED_TO_VILLAGER;
        if (spouse instanceof ServerPlayerEntity spouseEntity) {
            CriterionMCA.GENERIC_EVENT_CRITERION.trigger(spouseEntity, "marriage");
        }
        getFamilyEntry().updatePartner(spouse, state);
    }

    default void engage(Entity spouse) {
        if (spouse instanceof ServerPlayerEntity spouseEntity) {
            CriterionMCA.GENERIC_EVENT_CRITERION.trigger(spouseEntity, "engage");
        }
        getFamilyEntry().updatePartner(spouse, RelationshipState.ENGAGED);
    }

    default void promise(Entity spouse) {
        if (spouse instanceof ServerPlayerEntity spouseEntity) {
            CriterionMCA.GENERIC_EVENT_CRITERION.trigger(spouseEntity, "promise");
        }
        getFamilyEntry().updatePartner(spouse, RelationshipState.PROMISED);
    }

    default void endRelationShip(RelationshipState newState) {
        getFamilyEntry().updatePartner(null, newState);
    }

    /**
     * Remove a single spouse relationship without affecting other marriages.
     */
    default void endRelationshipWith(UUID partnerUUID) {
        getFamilyEntry().removeSpouseById(partnerUUID);
        getFamilyTree().getOrEmpty(partnerUUID).ifPresent(n -> n.removeSpouseById(getUUID()));
    }

    default RelationshipState getRelationshipState() {
        return getFamilyEntry().getRelationshipState();
    }

    default Optional<UUID> getPartnerUUID() {
        // Return primary spouse UUID for backwards compat; if absent, try first in spouses set
        UUID primary = getFamilyEntry().partner();
        if (!primary.equals(Util.NIL_UUID)) {
            return Optional.of(primary);
        }
        return getFamilyEntry().getSpouses().stream().findFirst();
    }

    default Optional<Text> getPartnerName() {
        return getPartnerUUID()
                .flatMap(id -> getFamilyTree().getOrEmpty(id))
                .map(FamilyTreeNode::getName)
                .map(Text::literal);
    }

    /** True if the entity has at least one current spouse. */
    default boolean isMarried() {
        return !getFamilyEntry().getSpouses().isEmpty();
    }

    default boolean isEngaged() {
        return getRelationshipState() == RelationshipState.ENGAGED;
    }

    default boolean isPromised() {
        return getRelationshipState() == RelationshipState.PROMISED;
    }

    default boolean isPromisedTo(UUID uuid) {
        return getPartnerUUID().orElse(Util.NIL_UUID).equals(uuid) && isPromised();
    }

    /** True if the given UUID is in this entity's spouses set. */
    default boolean isMarriedTo(UUID uuid) {
        return getFamilyEntry().isSpouse(uuid);
    }

    default boolean isEngagedWith(UUID uuid) {
        return getPartnerUUID().orElse(Util.NIL_UUID).equals(uuid) && isEngaged();
    }

    static Optional<EntityRelationship> of(Entity entity) {
        if (entity instanceof ServerPlayerEntity player) {
            return Optional.ofNullable(PlayerSaveData.get(player));
        }

        if (entity instanceof CompassionateEntity<?> compassionateEntity) {
            return Optional.ofNullable(compassionateEntity.getRelationships());
        }

        return Optional.empty();
    }
}
