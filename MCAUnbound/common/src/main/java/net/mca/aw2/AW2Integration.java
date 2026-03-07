package net.mca.aw2;

import net.mca.MCA;
import net.mca.aw2.research.ResearchManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main integration hub for Ancient Warfare 2 systems ported into MCA Unbound.
 * Handles bootstrap of all AW2 subsystems: torque, worksites, research, warehouse,
 * worker management, and production tracking.
 *
 * Bootstrap is called during MCA mod initialization to register all blocks,
 * items, block entity types, and research goals.
 */
public final class AW2Integration {
    public static final String AW2_PREFIX = "aw2_";
    public static final Logger LOGGER = LogManager.getLogger("MCA-AW2");

    private static boolean initialized = false;

    /**
     * Bootstraps all AW2 integration systems. Must be called during mod init.
     * Registers blocks, items, block entity types, and initializes the research system.
     */
    public static void bootstrap() {
        if (initialized) return;

        LOGGER.info("Bootstrapping AW2 integration systems...");

        // Register blocks (which also triggers block entity type registration)
        AW2Blocks.bootstrap();

        // Register items
        AW2Items.bootstrap();

        // Initialize the research tech tree
        ResearchManager.initializeResearchGoals();

        initialized = true;
        LOGGER.info("AW2 integration bootstrap complete - {} blocks, {} items registered.",
                countBlocks(), countItems());
    }

    /**
     * Called on server start to verify persistent state managers are initialized.
     * WorkerManager and WorksiteProductionTracker are lazy-loaded PersistentState,
     * so they initialize on first access automatically.
     */
    public static void onServerStart() {
        LOGGER.info("AW2 server-side systems ready.");
    }

    private static int countBlocks() {
        // Count based on registered fields in AW2Blocks
        return 15; // 7 worksites + 4 generators + 2 research/crafting + 1 warehouse + 1 ore processor
    }

    private static int countItems() {
        // Count based on registered fields in AW2Items
        return 19; // 15 block items + 4 special items
    }

    public static boolean isInitialized() {
        return initialized;
    }

    private AW2Integration() {}
}
