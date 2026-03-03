package net.mca.network.c2s;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.interaction.Constraint;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.CompassionateEntity;
import net.mca.entity.ai.relationship.EntityRelationship;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.block.TownHallBlockEntity;
import net.mca.network.s2c.GetInteractDataResponse;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class GetInteractDataRequest implements Message {
    @Serial
    private static final long serialVersionUID = -4363277735373237564L;

    final UUID uuid;

    public GetInteractDataRequest(UUID villager) {
        this.uuid = villager;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Entity entity = world.getEntity(uuid);

        if (entity instanceof VillagerLike<?> villager) {
            Set<Constraint> constraints = Constraint.allMatching(villager, player);

            EntityRelationship relationship = ((CompassionateEntity<?>) villager).getRelationships();
            FamilyTreeNode family = relationship.getFamilyEntry();

            String fatherName = relationship.getFamilyTree().getOrEmpty(family.father()).map(FamilyTreeNode::getName).orElse(null);
            String motherName = relationship.getFamilyTree().getOrEmpty(family.mother()).map(FamilyTreeNode::getName).orElse(null);
            String spouseName = relationship.getFamilyTree().getOrEmpty(family.partner()).map(FamilyTreeNode::getName).orElse(null);
            RelationshipState marriageState = relationship.getRelationshipState();

            // Profession + village + leader status
            String professionStr = "Jobless";
            String villageName = "";
            boolean isNpcLeader = false;
            boolean leaderConvinced = false;

            if (entity instanceof VillagerEntityMCA mca) {
                Optional<Village> homeVillage = mca.getResidency().getHomeVillage();

                if (homeVillage.isPresent()) {
                    Village hv = homeVillage.get();
                    villageName = hv.getName();
                    isNpcLeader = entity.getUuid().equals(hv.getNpcLeaderUUID());
                    if (isNpcLeader) {
                        leaderConvinced = player.getUuid().equals(hv.getConvincedByPlayerUUID());
                    }
                }

                if (isNpcLeader) {
                    professionStr = "Village Leader";
                } else {
                    var prof = mca.getProfession();
                    var profId = Registries.VILLAGER_PROFESSION.getId(prof);
                    if (profId != null) {
                        String path = profId.getPath();
                        if (!path.equals("none") && !path.isEmpty()) {
                            professionStr = Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
                        }
                    }
                }
            }

            // Check if the requesting player is the player-leader of any village
            VillageManager vm = VillageManager.get(world);
            boolean playerIsOwnVillageLeader = vm.findVillages(v -> {
                BlockPos thPos = v.getTownHallPos();
                if (thPos == null) return false;
                BlockEntity be = world.getBlockEntity(thPos);
                return be instanceof TownHallBlockEntity th
                        && th.hasLeader() && th.isLeaderPlayer()
                        && player.getUuid().equals(th.getLeaderUUID());
            }).findAny().isPresent();

            NetworkHandler.sendToPlayer(new GetInteractDataResponse(
                    constraints, fatherName, motherName, spouseName, marriageState,
                    professionStr, villageName, isNpcLeader, leaderConvinced, playerIsOwnVillageLeader), player);
        }
    }
}
