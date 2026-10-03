package com.rejectedpixels.auxilium.registry;

import com.rejectedpixels.auxilium.Auxilium;
import com.rejectedpixels.auxilium.item.EnergyMatrixItem;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ItemRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Auxilium.MODID);

    public static final DeferredItem<BlockItem> ENERGY_MATRIX = ITEMS.registerItem(
            "energy_matrix",
            properties -> new EnergyMatrixItem(BlockRegistry.ENERGY_MATRIX.get(), properties)
    );
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
