package com.rejectedpixels.auxilium.registry;

import com.rejectedpixels.auxilium.Auxilium;
import com.rejectedpixels.auxilium.menu.EnergyMatrixMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MenuTypeRegistry {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, Auxilium.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<EnergyMatrixMenu>> ENERGY_MATRIX = MENU_TYPES.register(
            "energy_matrix",
            () -> IMenuTypeExtension.create(EnergyMatrixMenu::new)
            );

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
