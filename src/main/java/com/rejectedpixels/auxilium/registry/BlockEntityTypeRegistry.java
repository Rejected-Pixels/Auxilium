package com.rejectedpixels.auxilium.registry;

import com.rejectedpixels.auxilium.Auxilium;
import com.rejectedpixels.auxilium.block.entity.EnergyMatrixBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BlockEntityTypeRegistry {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Auxilium.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyMatrixBlockEntity>> ENERGY_MATRIX_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "energy_matrix",
                    () -> BlockEntityType.Builder.of(
                            EnergyMatrixBlockEntity::new,
                            BlockRegistry.ENERGY_MATRIX.get()
                    ).build(null)

            );

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
