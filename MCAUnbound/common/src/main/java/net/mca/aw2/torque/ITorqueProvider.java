package net.mca.aw2.torque;

import net.minecraft.util.math.Direction;

/**
 * Interface for blocks that generate or store torque energy.
 * Ported from AW2's ITorque system, adapted for 1.20.1 Architectury.
 */
public interface ITorqueProvider {
    double getTorqueStored(Direction side);

    double getMaxTorque(Direction side);

    double addTorque(Direction side, double amount);

    double drainTorque(Direction side, double amount);

    boolean canOutputTorque(Direction side);

    boolean canInputTorque(Direction side);

    TorqueTier getTorqueTier();
}
