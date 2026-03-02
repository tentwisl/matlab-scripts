package net.mca.entity.interaction;

import net.mca.MCA;
import net.mca.ProfessionsMCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.MoveState;
import net.mca.entity.ai.Relationship;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.resources.Rank;
import net.mca.resources.Tasks;
import net.mca.server.world.data.PlayerSaveData;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.village.VillagerProfession;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Constraint implements BiPredicate<VillagerLike<?>, ServerPlayerEntity> {
    FAMILY("family", Relationship.IS_FAMILY.asConstraint()),
    NOT_FAMILY("!family", Relationship.IS_FAMILY.negate().asConstraint()),

    BABY("baby", (villager, player) -> villager.getAgeState() == AgeState.BABY),
    NOT_BABY("!baby", (villager, player) -> villager.getAgeState() != AgeState.BABY),

    TODDLER("toddler", (villager, player) -> villager.getAgeState() == AgeState.TODDLER),
    NOT_TODDLER("!toddler", (villager, player) -> villager.getAgeState() != AgeState.TODDLER),

    TEEN("teen", (villager, player) -> villager.getAgeState() == AgeState.TEEN),
    NOT_TEEN("!teen", (villager, player) -> villager.getAgeState() != AgeState.TEEN),

    ADULT("adult", (villager, player) -> villager.getAgeState() == AgeState.ADULT || villager.getAgeState() == AgeState.TEEN),
    NOT_ADULT("!adult", (villager, player) -> villager.getAgeState() != AgeState.ADULT && villager.getAgeState() != AgeState.TEEN),

    SPOUSE("spouse", Relationship.IS_MARRIED.asConstraint()),
    NOT_SPOUSE("!spouse", Relationship.IS_MARRIED.negate().asConstraint()),

    ENGAGED("engaged", Relationship.IS_ENGAGED.asConstraint()),
    NOT_ENGAGED("!engaged", Relationship.IS_ENGAGED.negate().asConstraint()),

    PROMISED("promised", Relationship.IS_PROMISED.asConstraint()),
    NOT_PROMISED("!promised", Relationship.IS_PROMISED.negate().asConstraint()),

    KIDS("kids", Relationship.IS_PARENT.asConstraint()),
    NOT_KIDS("!kids", Relationship.IS_PARENT.negate().asConstraint()),

    PARENT("parent", Relationship.IS_KID.asConstraint()),
    NOT_PARENT("!parent", Relationship.IS_KID.negate().asConstraint()),

    CLERIC("cleric", (villager, player) -> villager.getVillagerData().getProfession() == VillagerProfession.CLERIC),
    NOT_CLERIC("!cleric", (villager, player) -> villager.getVillagerData().getProfession() != VillagerProfession.CLERIC),

    ADVENTURER("adventurer", (villager, player) -> villager.getVillagerData().getProfession() == ProfessionsMCA.ADVENTURER.get()),
    NOT_ADVENTURER("!adventurer", (villager, player) -> villager.getVillagerData().getProfession() != ProfessionsMCA.ADVENTURER.get()),

    MERCENARY("mercenary", (villager, player) -> villager.getVillagerData().getProfession() == ProfessionsMCA.MERCENARY.get()),
    NOT_MERCENARY("!mercenary", (villager, player) -> villager.getVillagerData().getProfession() != ProfessionsMCA.MERCENARY.get()),

    OUTLAWED("outlawed", (villager, player) -> villager.getVillagerData().getProfession() == ProfessionsMCA.OUTLAW.get()),
    NOT_OUTLAWED("!outlawed", (villager, player) -> villager.getVillagerData().getProfession() != ProfessionsMCA.OUTLAW.get()),

    TRADER("trader", (villager, player) -> villager.canTradeWithProfession()),
    NOT_TRADER("!trader", (villager, player) -> !villager.canTradeWithProfession()),

    PEASANT("peasant", (villager, player) -> isRankAtLeast(villager, player, Rank.OUTSIDER)),
    NOT_PEASANT("!peasant", (villager, player) -> !isRankAtLeast(villager, player, Rank.OUTSIDER)),

    NOBLE("noble", (villager, player) -> isRankAtLeast(villager, player, Rank.NOBLE)),
    NOT_NOBLE("!noble", (villager, player) -> !isRankAtLeast(villager, player, Rank.NOBLE)),

    MAYOR("mayor", (villager, player) -> isRankAtLeast(villager, player, Rank.MAYOR)),
    NOT_MAYOR("!mayor", (villager, player) -> !isRankAtLeast(villager, player, Rank.MAYOR)),

    MONARCH("monarch", (villager, player) -> isRankAtLeast(villager, player, Rank.MONARCH)),
    NOT_MONARCH("!monarch", (villager, player) -> !isRankAtLeast(villager, player, Rank.MONARCH)),

    ORPHAN("orphan", Relationship.IS_ORPHAN.asConstraint()),
    NOT_ORPHAN("!orphan", Relationship.IS_ORPHAN.negate().asConstraint()),

    FOLLOWING("following", (villager, player) -> villager.getVillagerBrain().getMoveState() == MoveState.FOLLOW),
    NOT_FOLLOWING("!following", (villager, player) -> villager.getVillagerBrain().getMoveState() != MoveState.FOLLOW),

    STAYING("staying", (villager, player) -> villager.getVillagerBrain().getMoveState() == MoveState.STAY),
    NOT_STAYING("!staying", (villager, player) -> villager.getVillagerBrain().getMoveState() != MoveState.STAY),

    VILLAGE_HAS_SPACE("village_has_space", (villager, player) -> PlayerSaveData.get(player).getLastSeenVillage(VillageManager.get((ServerWorld)player.getWorld())).filter(Village::hasSpace).isPresent()),
    NOT_VILLAGE_HAS_SPACE("!village_has_space", (villager, player) -> PlayerSaveData.get(player).getLastSeenVillage(VillageManager.get((ServerWorld)player.getWorld())).filter(Village::hasSpace).isEmpty()),

    HAS_VILLAGE("has_village", (villager, player) -> villager instanceof VillagerEntityMCA mcaVillager && mcaVillager.getResidency().getHomeVillage().isPresent()),
    NOT_HAS_VILLAGE("!has_village", (villager, player) -> villager instanceof VillagerEntityMCA mcaVillager && mcaVillager.getResidency().getHomeVillage().isEmpty()),

    // MCAUnbound: Player has >= 100 hearts with this villager (gates kiss)
    HEARTS_100("hearts_100", (villager, player) -> {
        if (villager instanceof VillagerEntityMCA v && player != null) {
            return v.getVillagerBrain().getMemoriesForPlayer(player).getHearts() >= 100;
        }
        return false;
    }),
    NOT_HEARTS_100("!hearts_100", (villager, player) -> !HEARTS_100.test(villager, player)),

    // MCAUnbound: This villager IS the NPC village leader of their home village
    NPC_VILLAGE_LEADER("npc_village_leader", (villager, player) -> {
        if (villager instanceof VillagerEntityMCA v) {
            return v.getResidency().getHomeVillage()
                    .map(village -> {
                        java.util.UUID leaderUUID = village.getNpcLeaderUUID();
                        return leaderUUID != null && leaderUUID.equals(v.getUuid());
                    }).orElse(false);
        }
        return false;
    }),
    NOT_NPC_VILLAGE_LEADER("!npc_village_leader", (villager, player) -> !NPC_VILLAGE_LEADER.test(villager, player)),

    // MCAUnbound: Village residency — player has >= residentHeartThreshold hearts with this villager
    RESIDENT("resident", (villager, player) -> {
        if (villager instanceof VillagerEntityMCA v && player != null) {
            int hearts = v.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
            return hearts >= net.mca.nation.MCAUnboundConfig.get().residentHeartThreshold;
        }
        return false;
    }),
    NOT_RESIDENT("!resident", (villager, player) -> !RESIDENT.test(villager, player)),

    // MCAUnbound: Village leader — player has >= leaderHeartThreshold hearts with this villager
    VILLAGE_LEADER("village_leader", (villager, player) -> {
        if (villager instanceof VillagerEntityMCA v && player != null) {
            int hearts = v.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
            return hearts >= net.mca.nation.MCAUnboundConfig.get().leaderHeartThreshold;
        }
        return false;
    }),
    NOT_VILLAGE_LEADER("!village_leader", (villager, player) -> !VILLAGE_LEADER.test(villager, player)),

    HIT_BY("hit_by", (villager, player) -> {
        if (villager instanceof VillagerEntityMCA v) {
            return v.isHitBy(player);
        } else {
            return false;
        }
    }),
    NOT_HIT_BY("!hit_by", (villager, player) -> !HIT_BY.test(villager, player));

    private static boolean isRankAtLeast(VillagerLike<?> villager, ServerPlayerEntity player, Rank rank) {
        return player != null && villager instanceof VillagerEntityMCA && ((VillagerEntityMCA)villager).getResidency().getHomeVillage()
                .filter(village -> Tasks.getRank(village, player).isAtLeast(rank)).isPresent();
    }

    public static final Map<String, Constraint> REGISTRY = Stream.of(values()).collect(Collectors.toMap(a -> a.id, Function.identity()));

    private final String id;
    private final BiPredicate<VillagerLike<?>, ServerPlayerEntity> check;

    Constraint(String id, BiPredicate<VillagerLike<?>, ServerPlayerEntity> check) {
        this.id = id;
        this.check = check;
    }

    @Override
    public boolean test(VillagerLike<?> t, ServerPlayerEntity u) {
        return check.test(t, u);
    }

    public static Set<Constraint> all() {
        return new HashSet<>(REGISTRY.values());
    }

    public static Set<Constraint> allMatching(VillagerLike<?> villager, ServerPlayerEntity player) {
        return Stream.of(values()).filter(c -> c.test(villager, player)).collect(Collectors.toSet());
    }

    public static List<Constraint> fromStringList(String constraints) {
        if (MCA.isBlankString(constraints)) {
            return new ArrayList<>();
        }
        return Stream.of(constraints.split(","))
                .map(REGISTRY::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}

