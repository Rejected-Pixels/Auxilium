package com.rejectedpixels.auxilium.network;

import com.rejectedpixels.auxilium.Auxilium;
import com.rejectedpixels.auxilium.menu.EnergyMatrixMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: set the Energy Matrix's target item to a stack the player doesn't actually hold,
 * e.g. one dragged in from JEI. Only a ghost copy is stored, so no items are created or consumed.
 */
public record SetEnergyMatrixTargetPayload(int containerId, ItemStack stack) implements CustomPacketPayload {
    public static final Type<SetEnergyMatrixTargetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Auxilium.MODID, "set_energy_matrix_target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetEnergyMatrixTargetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetEnergyMatrixTargetPayload::containerId,
            ItemStack.OPTIONAL_STREAM_CODEC, SetEnergyMatrixTargetPayload::stack,
            SetEnergyMatrixTargetPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetEnergyMatrixTargetPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player.containerMenu instanceof EnergyMatrixMenu menu
                && menu.containerId == payload.containerId()
                && menu.stillValid(player)
                && (payload.stack().isEmpty() || EnergyMatrixMenu.canTargetUnheld(player, payload.stack()))) {
            menu.setTarget(payload.stack());
        }
    }
}
