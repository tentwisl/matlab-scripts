package net.mca.aw2;

import net.mca.MCA;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main integration hub for Ancient Warfare 2 systems ported into MCA Unbound.
 * Handles bootstrap of all AW2 subsystems: torque, worksites, research, warehouse.
 */
public final class AW2Integration {
    public static final String AW2_PREFIX = "aw2_";
    public static final Logger LOGGER = LogManager.getLogger("MCA-AW2");

    public static void bootstrap() {
        LOGGER.info("Bootstrapping AW2 integration systems...");
        AW2Blocks.bootstrap();
        AW2Items.bootstrap();
        LOGGER.info("AW2 integration bootstrap complete.");
    }

    private AW2Integration() {}
}
