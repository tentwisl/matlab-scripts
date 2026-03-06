package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.entity.interaction.Constraint;
import net.mca.cobalt.network.Message;
import net.mca.entity.ai.relationship.RelationshipState;

import java.io.Serial;
import java.util.Set;

public class GetInteractDataResponse implements Message {
    @Serial
    private static final long serialVersionUID = -4168503424192658779L;

    public final Set<Constraint> constraints;
    public final String father;
    public final String mother;
    public final String spouse;
    public final RelationshipState marriageState;
    /** Formatted profession string, or "Village Leader" if NPC leader, or "Jobless" for none. */
    public final String profession;
    /** Name of the villager's home village, or empty string. */
    public final String villageName;
    /** True if this villager is the NPC leader of their village. */
    public final boolean isNpcLeader;
    /** True if this leader's village has already been convinced by this player. */
    public final boolean leaderConvinced;
    /** True if the requesting player is the player-leader of their own (different) village. */
    public final boolean playerIsOwnVillageLeader;

    public GetInteractDataResponse(Set<Constraint> constraints, String father, String mother,
                                   String spouse, RelationshipState marriageState,
                                   String profession, String villageName, boolean isNpcLeader,
                                   boolean leaderConvinced, boolean playerIsOwnVillageLeader) {
        this.constraints = constraints;
        this.father = father;
        this.mother = mother;
        this.spouse = spouse;
        this.marriageState = marriageState;
        this.profession = profession;
        this.villageName = villageName;
        this.isNpcLeader = isNpcLeader;
        this.leaderConvinced = leaderConvinced;
        this.playerIsOwnVillageLeader = playerIsOwnVillageLeader;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleInteractDataResponse(this);
    }
}
