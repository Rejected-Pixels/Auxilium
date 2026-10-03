package com.rejectedpixels.auxilium.registry;

import com.rejectedpixels.auxilium.Auxilium;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CreativeTabRegistry {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Auxilium.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> AUXILIUM = CREATIVE_MODE_TABS.register(
            "auxilium",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.auxilium"))
                    .icon(() -> ItemRegistry.ENERGY_MATRIX.get().getDefaultInstance())
                    // Every Auxilium item, so new items show up here without extra work.
                    .displayItems((parameters, output) -> ItemRegistry.ITEMS.getEntries()
                            .forEach(item -> output.accept(item.get())))
                    .build()
    );

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
