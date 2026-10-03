package com.rejectedpixels.auxilium.datagen;

import com.rejectedpixels.auxilium.registry.ItemRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        Item collector = projectE("collector_mk3");
        Item relay = projectE("relay_mk3");

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ItemRegistry.ENERGY_MATRIX.get())
                .pattern("DRD")
                .pattern("CGL")
                .pattern("DRD")
                .define('D', projectE("dark_matter"))
                .define('R', projectE("red_matter"))
                .define('C', collector)
                .define('L', relay)
                .define('G', Items.GLOWSTONE)
                .unlockedBy("has_collector_mk3", has(collector))
                .unlockedBy("has_relay_mk3", has(relay))
                .save(output);
    }

    private static Item projectE(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("projecte", path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("ProjectE item not found: " + id);
        }
        return item;
    }
}
