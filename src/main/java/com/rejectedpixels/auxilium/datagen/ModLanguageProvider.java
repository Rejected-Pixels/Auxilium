package com.rejectedpixels.auxilium.datagen;

import com.rejectedpixels.auxilium.Auxilium;
import com.rejectedpixels.auxilium.registry.BlockRegistry;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, Auxilium.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        addBlock(BlockRegistry.ENERGY_MATRIX, "Energy Matrix");
        add("itemGroup.auxilium", "Auxilium");

        add("message.auxilium.energy_matrix.missing_blocks", "You need %s more Energy Matrix blocks");
        add("message.auxilium.energy_matrix.obstructed", "Not enough room: the Energy Matrix needs a clear 3x4x3 space");

        add("gui.auxilium.energy_matrix.stored", "EMC: %s / %s");
        add("gui.auxilium.energy_matrix.light", "Light Level: %s / %s");
        add("gui.auxilium.energy_matrix.rate", "+%s EMC/s");
        add("gui.auxilium.energy_matrix.cost", "%s EMC per item");
        add("gui.auxilium.energy_matrix.progress", "Next item: %s / %s EMC");
        add("gui.auxilium.energy_matrix.mode.condensing", "Condensing: generated and stored EMC is used to make the target item");
        add("gui.auxilium.energy_matrix.mode.relaying", "Relaying: generated EMC is stored and sent to adjacent blocks");
        add("gui.auxilium.energy_matrix.target", "Target Item");
        add("gui.auxilium.energy_matrix.target.hint", "Click with an item, or drag a learned item from JEI, to set the target");
        add("gui.auxilium.energy_matrix.charge", "Charge an EMC-storing item");
    }
}
