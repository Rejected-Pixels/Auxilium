package com.rejectedpixels.auxilium.client;

import com.rejectedpixels.auxilium.Auxilium;
import com.rejectedpixels.auxilium.client.model.EnergyMatrixModel;
import com.rejectedpixels.auxilium.client.renderer.EnergyMatrixRenderer;
import com.rejectedpixels.auxilium.client.renderer.EnergyMatrixTraceGlow;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import com.rejectedpixels.auxilium.client.screen.EnergyMatrixScreen;
import com.rejectedpixels.auxilium.registry.BlockEntityTypeRegistry;
import com.rejectedpixels.auxilium.registry.MenuTypeRegistry;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = Auxilium.MODID, value = Dist.CLIENT)
public class AuxiliumClient {
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(EnergyMatrixModel.LAYER, EnergyMatrixModel::createLayer);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MenuTypeRegistry.ENERGY_MATRIX.get(), EnergyMatrixScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntityTypeRegistry.ENERGY_MATRIX_BLOCK_ENTITY.get(), EnergyMatrixRenderer::new);
    }

    // Re-read the entity texture's traces after a resource reload (F3+T, resource pack changes).
    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> EnergyMatrixTraceGlow.reset());
    }
}
