package com.rejectedpixels.auxilium.datagen;

import com.rejectedpixels.auxilium.Auxilium;
import com.rejectedpixels.auxilium.registry.BlockRegistry;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;

public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Auxilium.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // Formed blocks are invisible (drawn by EnergyMatrixRenderer), but still use this model for break particles.
        cubeAllWithItem(BlockRegistry.ENERGY_MATRIX);    }

    private void cubeAllWithItem(DeferredBlock<? extends Block> block) {
        simpleBlockWithItem(block.get(), cubeAll(block.get()));
    }
}
