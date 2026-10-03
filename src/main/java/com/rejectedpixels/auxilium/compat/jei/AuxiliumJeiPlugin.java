package com.rejectedpixels.auxilium.compat.jei;

import com.rejectedpixels.auxilium.Auxilium;
import com.rejectedpixels.auxilium.client.screen.EnergyMatrixScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class AuxiliumJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Auxilium.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(EnergyMatrixScreen.class, new EnergyMatrixTargetGhostHandler());
    }
}
