package net.mca.item;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Memories;
import net.mca.entity.ai.Relationship;
import net.minecraft.server.network.ServerPlayerEntity;

public abstract class RelationshipItem extends TooltippedItem implements SpecialCaseGift {
    public RelationshipItem(Settings properties) {
        super(properties);
    }

    abstract int getHeartsRequired();

    @Override
    public boolean handle(ServerPlayerEntity player, VillagerEntityMCA villager) {
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);
        String response;

        // Only block if the target is a baby or if this exact player is already
        // married/engaged to this specific villager, or hearts are too low.
        if (villager.isBaby()) {
            response = "interaction.relationship.fail.isbaby";
        } else if (Relationship.IS_MARRIED.test(villager, player)) {
            response = "interaction.relationship.fail.marriedtogiver";
        } else if (memory.getHearts() < getHeartsRequired()) {
            response = "interaction.relationship.fail.lowhearts";
        } else {
            return false;
        }

        villager.sendChatMessage(player, response);
        return true;
    }
}
