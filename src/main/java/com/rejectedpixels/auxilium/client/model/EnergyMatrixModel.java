package com.rejectedpixels.auxilium.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rejectedpixels.auxilium.Auxilium;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * A single 48x64x48 pixel (3x4x3 block) box covering the whole formed Energy Matrix.
 */
public class EnergyMatrixModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Auxilium.MODID, "energy_matrix"), "main");
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Auxilium.MODID, "textures/entity/energy_matrix.png");

    private static final String MATRIX = "matrix";
    private final ModelPart matrix;

    public EnergyMatrixModel(ModelPart root) {
        this.matrix = root.getChild(MATRIX);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(MATRIX,
                CubeListBuilder.create().mirror().addBox(-24F, -32F, -24F, 48, 64, 48),
                PartPose.offset(0F, -24F, 0F));
        return LayerDefinition.create(mesh, 256, 128);
    }

    public void render(PoseStack poseStack, VertexConsumer buffer, int light, int overlay) {
        matrix.render(poseStack, buffer, light, overlay);
    }

    /** Renders tinted by an ARGB colour, e.g. for a translucent placement preview. */
    public void render(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
        matrix.render(poseStack, buffer, light, overlay, color);
    }
}
