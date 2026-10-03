package com.rejectedpixels.auxilium;

import com.rejectedpixels.auxilium.config.AuxiliumConfig;
import com.rejectedpixels.auxilium.registry.BlockEntityTypeRegistry;
import com.rejectedpixels.auxilium.registry.BlockRegistry;
import com.rejectedpixels.auxilium.registry.CreativeTabRegistry;
import com.rejectedpixels.auxilium.registry.ItemRegistry;
import com.rejectedpixels.auxilium.registry.MenuTypeRegistry;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Auxilium.MODID)
public class Auxilium {
    public static final String MODID = "auxilium";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Auxilium(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, AuxiliumConfig.SPEC);

        BlockRegistry.register(modEventBus);
        ItemRegistry.register(modEventBus);
        BlockEntityTypeRegistry.register(modEventBus);
        MenuTypeRegistry.register(modEventBus);
        CreativeTabRegistry.register(modEventBus);
    }
}
