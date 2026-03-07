package net.mca.aw2.torque;

import net.minecraft.nbt.NbtCompound;

/**
 * Core torque energy storage cell, ported from AW2's ITorque.TorqueCell.
 * Stores rotational energy used to power worksites and automation blocks.
 */
public class TorqueCell {
    private double storedEnergy;
    private final double maxEnergy;
    private final double maxInput;
    private final double maxOutput;
    private final double efficiencyFactor;

    public TorqueCell(double maxEnergy, double maxInput, double maxOutput, double efficiencyFactor) {
        this.maxEnergy = maxEnergy;
        this.maxInput = maxInput;
        this.maxOutput = maxOutput;
        this.efficiencyFactor = efficiencyFactor;
        this.storedEnergy = 0;
    }

    public double getStoredEnergy() {
        return storedEnergy;
    }

    public double getMaxEnergy() {
        return maxEnergy;
    }

    public double getEnergyFillRatio() {
        return maxEnergy > 0 ? storedEnergy / maxEnergy : 0;
    }

    public double addEnergy(double amount) {
        double accepted = Math.min(amount, maxInput);
        accepted = Math.min(accepted, maxEnergy - storedEnergy);
        if (accepted > 0) {
            storedEnergy += accepted * efficiencyFactor;
        }
        return accepted;
    }

    public double drainEnergy(double amount) {
        double drained = Math.min(amount, maxOutput);
        drained = Math.min(drained, storedEnergy);
        if (drained > 0) {
            storedEnergy -= drained;
        }
        return drained;
    }

    public boolean hasEnergy(double amount) {
        return storedEnergy >= amount;
    }

    public void writeNbt(NbtCompound nbt) {
        nbt.putDouble("TorqueStored", storedEnergy);
    }

    public void readNbt(NbtCompound nbt) {
        this.storedEnergy = nbt.getDouble("TorqueStored");
    }
}
