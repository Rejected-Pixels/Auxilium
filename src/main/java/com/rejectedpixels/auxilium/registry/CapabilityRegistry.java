package com.rejectedpixels.auxilium.registry;

import com.rejectedpixels.auxilium.Auxilium;
import com.rejectedpixels.auxilium.block.EnergyMatrixBlock;
import com.rejectedpixels.auxilium.block.entity.EnergyMatrixBlockEntity;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@EventBusSubscriber(modid = Auxilium.MODID)
public class CapabilityRegistry {
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(PECapabilities.EMC_STORAGE_CAPABILITY, (level, pos, state, blockEntity, side) -> {
            if (!state.getValue(EnergyMatrixBlock.FORMED)) return null;
            BlockPos corePos = state.getValue(EnergyMatrixBlock.CORE)
                    ? pos
                    : EnergyMatrixBlockEntity.findCorePos(level, pos).orElse(null);
            if (corePos != null && level.getBlockEntity(corePos) instanceof EnergyMatrixBlockEntity core) {
                return core.getEmcStorage();
            }
            return null;
        }, BlockRegistry.ENERGY_MATRIX.get());

        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, blockEntity, side) -> {
            if (!state.getValue(EnergyMatrixBlock.FORMED)) return null;
            BlockPos corePos = state.getValue(EnergyMatrixBlock.CORE)
                    ? pos
                    : EnergyMatrixBlockEntity.findCorePos(level, pos).orElse(null);
            if (corePos != null && level.getBlockEntity(corePos) instanceof EnergyMatrixBlockEntity core) {
                return core.getAutomationItemHandler();
            }
            return null;
        }, BlockRegistry.ENERGY_MATRIX.get());
    }
}
