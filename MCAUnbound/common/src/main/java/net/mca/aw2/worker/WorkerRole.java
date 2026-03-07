package net.mca.aw2.worker;

import net.mca.aw2.worksite.WorksiteType;

import java.util.Arrays;
import java.util.List;

/**
 * Worker roles that MCA villagers can take on when assigned to AW2 worksites.
 * Maps directly to AW2's NPC specialization system but uses MCA villagers.
 */
public enum WorkerRole {
    FARMER("Farmer", "Tends crops and animals at farms",
            WorksiteType.CROP_FARM, WorksiteType.ANIMAL_FARM),
    MINER("Miner", "Operates quarries and ore processors",
            WorksiteType.QUARRY, WorksiteType.ORE_PROCESSOR),
    WOODSMAN("Woodsman", "Manages tree farms and lumber operations",
            WorksiteType.TREE_FARM),
    CRAFTSMAN("Craftsman", "Operates auto-crafting stations",
            WorksiteType.AUTO_CRAFTING),
    FISHERMAN("Fisherman", "Operates fish farms",
            WorksiteType.FISH_FARM),
    RESEARCHER("Researcher", "Conducts research at the research table",
            (WorksiteType[]) null),
    COURIER("Courier", "Transports goods between locations",
            (WorksiteType[]) null);

    private final String displayName;
    private final String description;
    private final List<WorksiteType> compatibleWorksites;

    WorkerRole(String displayName, String description, WorksiteType... compatibleWorksites) {
        this.displayName = displayName;
        this.description = description;
        this.compatibleWorksites = compatibleWorksites != null ? Arrays.asList(compatibleWorksites) : List.of();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public List<WorksiteType> getCompatibleWorksites() {
        return compatibleWorksites;
    }

    public boolean isCompatibleWith(WorksiteType type) {
        return compatibleWorksites.contains(type);
    }

    /**
     * Determines the best worker role for a given worksite type.
     */
    public static WorkerRole getBestRoleFor(WorksiteType type) {
        for (WorkerRole role : values()) {
            if (role.isCompatibleWith(type)) {
                return role;
            }
        }
        return FARMER; // Default fallback
    }
}
