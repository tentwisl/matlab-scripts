package net.mca.nation;

import net.minecraft.server.world.ServerWorld;

import java.util.List;
import java.util.UUID;

/**
 * Drives all nation simulation logic.
 * Called every game tick from {@link NationManager#tick()}.
 * Heavy daily processing is rate-limited to once per in-game day (24000 ticks).
 */
public final class NationTickManager {

    /** One in-game day in ticks (20 ticks/sec × 60 sec × 20 min). */
    private static final long TICKS_PER_DAY = 24000L;
    /** Diplomacy trust decays/rises by this amount each day between nations not interacting. */
    private static final int TRUST_DRIFT_PER_DAY = 1;
    /** Daily political capital income for player-led nations with active congress. */
    private static final int PC_INCOME_BASE = 5;

    private NationTickManager() {}

    public static void tick(ServerWorld world, NationManager manager) {
        long currentTick = world.getTime();
        long currentDay  = currentTick / TICKS_PER_DAY;

        // Run per-tick lightweight jobs
        checkHeirInactivity(manager, currentTick);

        // Run daily jobs once per day (on the first tick of each new day)
        if (currentTick % TICKS_PER_DAY == 0) {
            dailyTick(world, manager, currentDay);
        }
    }

    // ── Daily simulation ──────────────────────────────────────────────────────

    private static void dailyTick(ServerWorld world, NationManager manager, long day) {
        for (Nation nation : manager.getAllNations()) {
            tickNationDaily(world, manager, nation, day);
        }
        tickDiplomacyDaily(manager, day);
        tickCitiesDaily(manager, day);
    }

    private static void tickNationDaily(ServerWorld world, NationManager manager, Nation nation, long day) {
        NationStats stats = nation.getStats();

        // Political capital income
        int pcIncome = PC_INCOME_BASE;
        if (nation.getCongress().size() >= 3) pcIncome += 2;
        stats.addPoliticalCapital(pcIncome);

        // Approval decay toward neutral (simulates natural drift)
        int approval = stats.getApprovalRating();
        if (approval > 50) stats.setApprovalRating(approval - 1);
        else if (approval < 50) stats.setApprovalRating(approval + 1);

        // Track low-approval days for No Confidence risk
        if (stats.getApprovalRating() < 30) {
            stats.incrementApprovalBelowThresholdDays();
            if (stats.getApprovalBelowThresholdDays() >= 7 && nation.getGovernmentType().hasElections()) {
                triggerNoConfidenceRisk(manager, nation, day);
            }
        } else {
            stats.resetApprovalBelowThresholdDays();
        }

        // Republic election check
        if (nation.getGovernmentType().hasElections()) {
            long mandateEnd = nation.getCongress().getLeaderMandateEndDay();
            if (mandateEnd > 0 && day >= mandateEnd) {
                triggerElection(manager, nation, day);
            }
        }

        // Heir inactivity → auto-activate
        if (nation.getHeir().hasHeir() && !nation.getHeir().isActive()) {
            long lastActive = nation.getHeir().getLastActiveTick();
            if (world.getTime() - lastActive > TICKS_PER_DAY * 7) {
                nation.getHeir().setActive(true);
                nation.logEvent("Day " + day + ": Heir activated due to leader inactivity.");
                manager.markDirty();
            }
        }

        // AI nation behaviour (simple expansion/war tendency simulation)
        if (nation.isAiControlled()) {
            tickAiNationDaily(manager, nation, day);
        }

        manager.markDirty();
    }

    private static void tickAiNationDaily(NationManager manager, Nation nation, long day) {
        NationPersonality p = nation.getPersonality();
        NationStats stats   = nation.getStats();

        // Economic growth simulation (very light)
        int growth = switch (p) {
            case MERCANTILE, SCIENTIFIC -> 3;
            case CULTURAL               -> 2;
            default                     -> 1;
        };
        stats.addEconomicOutput(growth);

        // Military build-up for aggressive nations
        if (p == NationPersonality.AGGRESSIVE || p == NationPersonality.MILITARIST) {
            stats.addMilitaryStrength(1);
        }
    }

    // ── Diplomacy daily tick ──────────────────────────────────────────────────

    private static void tickDiplomacyDaily(NationManager manager, long day) {
        for (DiplomacyRelation rel : manager.getAllRelations()) {
            // Passive trust drift toward neutral when no events
            int trust = rel.getTrustValue();
            if (trust > 0)       rel.adjustTrust(-TRUST_DRIFT_PER_DAY);
            else if (trust < 0)  rel.adjustTrust(TRUST_DRIFT_PER_DAY);
        }
        manager.markDirty();
    }

    // ── Cities daily tick ─────────────────────────────────────────────────────

    private static void tickCitiesDaily(NationManager manager, long day) {
        for (CityData city : manager.getAllCities()) {
            // Independence score decay for annexed cities
            if (!city.isIndependent()) {
                city.decayIndependenceScore(2);
            }
        }
        manager.markDirty();
    }

    // ── Heir inactivity check (per tick, cheap) ───────────────────────────────

    private static void checkHeirInactivity(NationManager manager, long currentTick) {
        // Nothing per-tick for now; handled in daily tick
    }

    // ── Election logic ────────────────────────────────────────────────────────

    private static void triggerElection(NationManager manager, Nation nation, long day) {
        // For AI nations: randomise new "leader" from congress members
        // For player nations: fire an event so the player can respond
        nation.logEvent("Day " + day + ": Election cycle began.");
        // Set next mandate (30 days from now)
        nation.getCongress().setLastElectionDay(day);
        nation.getCongress().setLeaderMandateEndDay(day + nation.getCongress().getElectionCycleLength());

        if (nation.isAiControlled() && !nation.getCongress().getMembers().isEmpty()) {
            // Pick a random congress member as new leader
            List<?> members = nation.getCongress().getMembers();
            int idx = (int)(Math.random() * members.size());
            nation.logEvent("Day " + day + ": New leader elected from congress.");
        }
        manager.markDirty();
    }

    private static void triggerNoConfidenceRisk(NationManager manager, Nation nation, long day) {
        nation.logEvent("Day " + day + ": Approval dangerously low — No Confidence risk!");
        manager.markDirty();
    }
}
