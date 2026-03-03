package net.mca;

/**
 * Configuration for MCA Unbound village / leadership systems.
 */
public class MCAUnboundConfig {

    private static final MCAUnboundConfig INSTANCE = new MCAUnboundConfig();

    public static MCAUnboundConfig get() {
        return INSTANCE;
    }

    // ── Village Leadership ────────────────────────────────────────────────────

    /** Minimum heart level a player must have with EVERY villager to become a resident. */
    public int residentHeartThreshold = 30;
    /** Minimum heart level a player must have with EVERY villager to attempt leadership. */
    public int leaderHeartThreshold = 50;
    /** Minecraft-day wait time after attempting leadership before receiving result mail. */
    public int leaderElectionWaitDays = 1;
    /** Base percentage chance (0-100) that a leadership attempt fails. */
    public int leaderElectionBaseFailChance = 30;

    // ── Nation Formation ──────────────────────────────────────────────────────

    /**
     * Heart level the player must reach with another village's NPC leader
     * to unlock the "Propose Nation Alliance" dialogue option.
     */
    public int nationAllianceHeartThreshold = 100;
}
