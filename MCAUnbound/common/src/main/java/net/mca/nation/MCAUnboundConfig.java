package net.mca.nation;

/**
 * Centralised configuration for MCA Unbound nation / village / politics systems.
 * <p>
 * Values are mutable so they can be changed via commands or a config GUI at
 * runtime. A singleton is kept per server instance.
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
    /** Base percentage chance (0–100) that a leadership attempt fails. */
    public int leaderElectionBaseFailChance = 30;

    // ── Nation Formation ──────────────────────────────────────────────────────

    /** Heart level the player must reach with another village's leader to include that village. */
    public int nationFormationLeaderHeartThreshold = 100;
    /** Minimum number of villages (including your own) required to form a nation. */
    public int minVillagesForNation = 2;
    /** Minecraft-day wait time after confirming nation formation before receiving result mail. */
    public int nationFormationWaitDays = 1;
    /** Base percentage chance (0–100) that nation formation fails per extra village beyond 2. */
    public int nationFormationExtraVillageFailChance = 10;

    // ── Republic / Elections ──────────────────────────────────────────────────

    /** Length of a presidential term in Minecraft days (configurable via /SetPresidentTerm). */
    public int presidentTermLengthDays = 30;
    /** Number of congressional representatives randomly selected per village. */
    public int congressionalRepsPerVillage = 2;

    // ── Reputation / Hearts ──────────────────────────────────────────────────

    /** Starting heart value when a baby is born in a village. */
    public int newbornStartingHearts = 25;
    /** Hearts gained by all village members each time the player completes a favour quest. */
    public int favourQuestHeartsGain = 5;

    // ── Chunk Claiming ────────────────────────────────────────────────────────

    /** Base radius (in chunks) a village auto-claims around its Town Hall. */
    public int villageAutoClaimRadiusBase = 3;
    /** Extra chunk radius per 10 population. */
    public int villageAutoClaimRadiusPerTenPop = 1;

    // ── Population Cap ────────────────────────────────────────────────────────

    /** Default nation-wide population cap (0 = unlimited). */
    public int defaultNationPopCap = 0;

    // ── Villager Summoning ────────────────────────────────────────────────────

    /** Seconds a summoned villager waits at the Town Hall before the interaction window. */
    public int summonPreInteractWaitSec = 15;
    /** Seconds after the interaction before the villager returns to their previous position. */
    public int summonPostInteractWaitSec = 15;

    // ── Favour Quests ─────────────────────────────────────────────────────────

    /** Whether favour quests are enabled (grants heart bonuses for resource fetch quests). */
    public boolean favourQuestsEnabled = true;

    // ── Local / National Favour Points ────────────────────────────────────────

    /** Starting NFP for all villagers when a Republic is created (neutral). */
    public int startingNfp = 0;

    // ── Debug ─────────────────────────────────────────────────────────────────

    /** When true, extra nation/village debug info is logged to server console. */
    public boolean debugLogging = false;
}
