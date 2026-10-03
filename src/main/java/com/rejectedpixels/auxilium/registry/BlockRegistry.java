package com.rejectedpixels.auxilium.registry;

import com.rejectedpixels.auxilium.Auxilium;
import com.rejectedpixels.auxilium.block.EnergyMatrixBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BlockRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Auxilium.MODID);

    public static final DeferredBlock<EnergyMatrixBlock> ENERGY_MATRIX = BLOCKS.registerBlock(
            "energy_matrix",
            EnergyMatrixBlock::new,
            BlockBehaviour.Properties.of()
                    .sound(SoundType.AMETHYST)
                    .pushReaction(PushReaction.BLOCK)
    );

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
