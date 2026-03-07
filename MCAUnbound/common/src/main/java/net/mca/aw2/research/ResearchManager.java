package net.mca.aw2.research;

import net.mca.aw2.AW2Integration;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * Server-side manager for the AW2 research/tech tree system.
 * Tracks which researches each player has completed and which are in progress.
 *
 * Integrates with the nation system: a nation's science level is derived
 * from the total number of completed researches across all its member players.
 */
public class ResearchManager extends PersistentState {
    private static final String DATA_KEY = "mca_aw2_research";

    // Player UUID -> set of completed research IDs
    private final Map<UUID, Set<Identifier>> completedResearch = new HashMap<>();
    // Player UUID -> currently active research + progress
    private final Map<UUID, ActiveResearch> activeResearch = new HashMap<>();

    // Cached research tree
    private transient Map<Identifier, ResearchGoal> researchTree;

    public ResearchManager() {
        this.researchTree = ResearchGoal.buildDefaultTree();
    }

    public static ResearchManager get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager()
                .getOrCreate(ResearchManager::fromNbt, ResearchManager::new, DATA_KEY);
    }

    /**
     * Initializes the static research goal tree. Called during bootstrap to
     * ensure research goals are loaded before any player accesses them.
     */
    public static void initializeResearchGoals() {
        Map<Identifier, ResearchGoal> tree = ResearchGoal.buildDefaultTree();
        AW2Integration.LOGGER.info("Initialized {} research goals.", tree.size());
    }

    public Map<Identifier, ResearchGoal> getResearchTree() {
        if (researchTree == null) {
            researchTree = ResearchGoal.buildDefaultTree();
        }
        return researchTree;
    }

    // ==================== RESEARCH OPERATIONS ====================

    /**
     * Starts researching a goal for a player.
     */
    public boolean startResearch(UUID playerUuid, Identifier researchId) {
        ResearchGoal goal = getResearchTree().get(researchId);
        if (goal == null) return false;

        // Check prerequisites
        Set<Identifier> completed = completedResearch.getOrDefault(playerUuid, Collections.emptySet());
        for (Identifier prereq : goal.getPrerequisites()) {
            if (!completed.contains(prereq)) {
                return false;
            }
        }

        // Check not already completed
        if (completed.contains(researchId)) return false;

        // Set as active research
        activeResearch.put(playerUuid, new ActiveResearch(researchId, 0, goal.getResearchTime()));
        markDirty();
        AW2Integration.LOGGER.info("Player {} started researching {}", playerUuid, researchId);
        return true;
    }

    /**
     * Advances research progress by one tick. Called by the Research Table block entity
     * when a researcher NPC or player is actively working.
     *
     * @param progressAmount how much progress to add (modified by researcher skill)
     * @return true if the research was completed this tick
     */
    public boolean tickResearch(UUID playerUuid, int progressAmount) {
        ActiveResearch active = activeResearch.get(playerUuid);
        if (active == null) return false;

        active.addProgress(progressAmount);

        if (active.isComplete()) {
            completeResearch(playerUuid, active.getResearchId());
            activeResearch.remove(playerUuid);
            markDirty();
            return true;
        }

        markDirty();
        return false;
    }

    private void completeResearch(UUID playerUuid, Identifier researchId) {
        completedResearch.computeIfAbsent(playerUuid, k -> new HashSet<>()).add(researchId);
        AW2Integration.LOGGER.info("Player {} completed research {}", playerUuid, researchId);
    }

    /**
     * Checks if a player has completed a specific research.
     */
    public boolean hasCompleted(UUID playerUuid, Identifier researchId) {
        return completedResearch.getOrDefault(playerUuid, Collections.emptySet()).contains(researchId);
    }

    /**
     * Gets all completed research IDs for a player.
     */
    public Set<Identifier> getCompletedResearch(UUID playerUuid) {
        return Collections.unmodifiableSet(
                completedResearch.getOrDefault(playerUuid, Collections.emptySet()));
    }

    /**
     * Gets the currently active research for a player, if any.
     */
    public Optional<ActiveResearch> getActiveResearch(UUID playerUuid) {
        return Optional.ofNullable(activeResearch.get(playerUuid));
    }

    /**
     * Calculates a player's effective science level based on completed researches.
     */
    public int calculateScienceLevel(UUID playerUuid) {
        Set<Identifier> completed = completedResearch.getOrDefault(playerUuid, Collections.emptySet());
        if (completed.isEmpty()) return 0;

        int maxLevel = 0;
        for (Identifier id : completed) {
            ResearchGoal goal = getResearchTree().get(id);
            if (goal != null) {
                maxLevel = Math.max(maxLevel, goal.getScienceLevelRequired() + 1);
            }
        }
        return Math.min(maxLevel, 10);
    }

    /**
     * Returns all available (unlockable) researches for a player.
     */
    public List<ResearchGoal> getAvailableResearch(UUID playerUuid) {
        Set<Identifier> completed = getCompletedResearch(playerUuid);
        List<ResearchGoal> available = new ArrayList<>();

        for (Map.Entry<Identifier, ResearchGoal> entry : getResearchTree().entrySet()) {
            if (completed.contains(entry.getKey())) continue;

            ResearchGoal goal = entry.getValue();
            boolean allPrereqsMet = goal.getPrerequisites().stream().allMatch(completed::contains);
            if (allPrereqsMet) {
                available.add(goal);
            }
        }
        return available;
    }

    // ==================== SERIALIZATION ====================

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        // Completed research
        NbtCompound completedNbt = new NbtCompound();
        for (Map.Entry<UUID, Set<Identifier>> entry : completedResearch.entrySet()) {
            NbtList list = new NbtList();
            for (Identifier id : entry.getValue()) {
                list.add(NbtString.of(id.toString()));
            }
            completedNbt.put(entry.getKey().toString(), list);
        }
        nbt.put("Completed", completedNbt);

        // Active research
        NbtCompound activeNbt = new NbtCompound();
        for (Map.Entry<UUID, ActiveResearch> entry : activeResearch.entrySet()) {
            activeNbt.put(entry.getKey().toString(), entry.getValue().writeNbt());
        }
        nbt.put("Active", activeNbt);

        return nbt;
    }

    public static ResearchManager fromNbt(NbtCompound nbt) {
        ResearchManager manager = new ResearchManager();

        // Completed research
        if (nbt.contains("Completed")) {
            NbtCompound completedNbt = nbt.getCompound("Completed");
            for (String key : completedNbt.getKeys()) {
                UUID playerUuid = UUID.fromString(key);
                Set<Identifier> ids = new HashSet<>();
                NbtList list = completedNbt.getList(key, NbtElement.STRING_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    ids.add(new Identifier(list.getString(i)));
                }
                manager.completedResearch.put(playerUuid, ids);
            }
        }

        // Active research
        if (nbt.contains("Active")) {
            NbtCompound activeNbt = nbt.getCompound("Active");
            for (String key : activeNbt.getKeys()) {
                UUID playerUuid = UUID.fromString(key);
                manager.activeResearch.put(playerUuid, ActiveResearch.fromNbt(activeNbt.getCompound(key)));
            }
        }

        return manager;
    }

    // ==================== ACTIVE RESEARCH DATA ====================

    public static class ActiveResearch {
        private final Identifier researchId;
        private int progress;
        private final int totalRequired;

        public ActiveResearch(Identifier researchId, int progress, int totalRequired) {
            this.researchId = researchId;
            this.progress = progress;
            this.totalRequired = totalRequired;
        }

        public Identifier getResearchId() { return researchId; }
        public int getProgress() { return progress; }
        public int getTotalRequired() { return totalRequired; }
        public float getProgressPercent() { return totalRequired > 0 ? (float) progress / totalRequired : 0; }
        public boolean isComplete() { return progress >= totalRequired; }

        public void addProgress(int amount) {
            this.progress = Math.min(progress + amount, totalRequired);
        }

        public NbtCompound writeNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("Id", researchId.toString());
            nbt.putInt("Progress", progress);
            nbt.putInt("Total", totalRequired);
            return nbt;
        }

        public static ActiveResearch fromNbt(NbtCompound nbt) {
            return new ActiveResearch(
                    new Identifier(nbt.getString("Id")),
                    nbt.getInt("Progress"),
                    nbt.getInt("Total")
            );
        }
    }
}
