package com.rejectedpixels.auxilium.compat.jei;

import com.rejectedpixels.auxilium.client.screen.EnergyMatrixScreen;
import com.rejectedpixels.auxilium.menu.EnergyMatrixMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import com.rejectedpixels.auxilium.network.SetEnergyMatrixTargetPayload;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/** Lets items be dragged from JEI's ingredient list onto the Energy Matrix's ghost target slot. */
public class EnergyMatrixTargetGhostHandler implements IGhostIngredientHandler<EnergyMatrixScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(EnergyMatrixScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        // Only items with an EMC value that the player has learned in a Transmutation Table get a drop target
        Optional<ItemStack> dragged = ingredient.getItemStack();
        Player player = Minecraft.getInstance().player;
        if (dragged.isEmpty() || player == null
                || !IEMCProxy.INSTANCE.hasValue(dragged.get())
                || !EnergyMatrixMenu.canTargetUnheld(player, dragged.get())) {
            return List.of();
        }
        ItemStack stack = dragged.get().copyWithCount(1);
        Rect2i area = screen.getTargetSlotArea();
        return List.of(new Target<>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I ignored) {
                PacketDistributor.sendToServer(new SetEnergyMatrixTargetPayload(screen.getMenu().containerId, stack));
            }
        });
    }

    @Override
    public void onComplete() {
    }
}
