package com.rejectedpixels.auxilium.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rejectedpixels.auxilium.block.EnergyMatrixBlock;
import com.rejectedpixels.auxilium.block.entity.EnergyMatrixBlockEntity;
import com.rejectedpixels.auxilium.client.model.EnergyMatrixModel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

public class EnergyMatrixRenderer implements BlockEntityRenderer<EnergyMatrixBlockEntity> {
    private final EnergyMatrixModel model;

    public EnergyMatrixRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new EnergyMatrixModel(context.bakeLayer(EnergyMatrixModel.LAYER));
    }

    @Override
    public void render(EnergyMatrixBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!isFormed(blockEntity) || blockEntity.getLevel() == null) return;

        int light = LevelRenderer.getLightColor(blockEntity.getLevel(), blockEntity.getBlockPos().above(4));

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.scale(1.0F, -1.0F, -1.0F);
        model.render(poseStack, bufferSource.getBuffer(RenderType.entityTranslucent(EnergyMatrixModel.TEXTURE)), light, packedOverlay);

        ResourceLocation glow = EnergyMatrixTraceGlow.prepare();
        if (glow != null) {
            model.render(poseStack, bufferSource.getBuffer(RenderType.eyes(glow)), LightTexture.FULL_BRIGHT, packedOverlay);
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(EnergyMatrixBlockEntity blockEntity) {
        return isFormed(blockEntity);
    }

    @Override
    public AABB getRenderBoundingBox(EnergyMatrixBlockEntity blockEntity) {
        return EnergyMatrixBlockEntity.getStructureBounds(blockEntity.getBlockPos());
    }

    private static boolean isFormed(EnergyMatrixBlockEntity blockEntity) {
        return blockEntity.getBlockState().getValue(EnergyMatrixBlock.FORMED);
    }
}
