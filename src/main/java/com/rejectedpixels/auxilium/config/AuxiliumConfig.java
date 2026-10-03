package com.rejectedpixels.auxilium.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config (per world, synced to clients so the GUI shows the same numbers): serverconfig/auxilium-server.toml.
 */
public class AuxiliumConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Stored EMC is synced to the GUI in 60 bits, so cap storage there.
    private static final long MAX_SYNCABLE_EMC = (1L << 60) - 1;

    public static final ModConfigSpec.LongValue ENERGY_MATRIX_EMC_PER_SECOND;
    public static final ModConfigSpec.LongValue ENERGY_MATRIX_MAX_EMC;
    public static final ModConfigSpec.LongValue ENERGY_MATRIX_TRANSFER_PER_TICK;

    static {
        BUILDER.push("energy_matrix");
        ENERGY_MATRIX_EMC_PER_SECOND = BUILDER
                .comment("EMC generated per second at full light (light level 15). Scales down to 0 in complete darkness.")
                .defineInRange("emcPerSecond", 1_440L, 0L, Long.MAX_VALUE);
        ENERGY_MATRIX_MAX_EMC = BUILDER
                .comment("Maximum EMC the Energy Matrix can store.")
                .defineInRange("maxEmc", 362_160_000L, 1L, MAX_SYNCABLE_EMC);
        ENERGY_MATRIX_TRANSFER_PER_TICK = BUILDER
                .comment("Maximum EMC per tick sent to adjacent blocks, and used to charge the item in the charge slot.")
                .defineInRange("transferPerTick", 23_040L, 0L, Long.MAX_VALUE);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static long emcPerSecond() {
        return ENERGY_MATRIX_EMC_PER_SECOND.get();
    }

    public static long maxEmc() {
        return ENERGY_MATRIX_MAX_EMC.get();
    }

    public static long transferPerTick() {
        return ENERGY_MATRIX_TRANSFER_PER_TICK.get();
    }
}
