package com.rejectedpixels.auxilium.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rejectedpixels.auxilium.Auxilium;
import com.rejectedpixels.auxilium.block.entity.EnergyMatrixBlockEntity;
import com.rejectedpixels.auxilium.client.model.EnergyMatrixModel;
import com.rejectedpixels.auxilium.item.EnergyMatrixItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shows a translucent ghost of the formed Energy Matrix where it would be built while the player holds the item,
 * tinted green if it can be placed and red if not, with any blocks in the way outlined.
 */
@EventBusSubscriber(modid = Auxilium.MODID, value = Dist.CLIENT)
public class EnergyMatrixPlacementPreview {
    private static final int VALID_TINT = 0x7090FFA0;
    private static final int INVALID_TINT = 0x70FF6060;

    private static @Nullable EnergyMatrixModel model;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        InteractionHand hand = getHandHoldingMatrix(player);
        if (hand == null) return;
        ItemStack stack = player.getItemInHand(hand);
        EnergyMatrixItem item = (EnergyMatrixItem) stack.getItem();

        BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, hit);
        if (!context.canPlace()) return;
        BlockPos corePos = context.getClickedPos();

        List<BlockPos> obstructions = EnergyMatrixItem.getObstructedPositions(level, corePos);
        boolean valid = obstructions.isEmpty()
                && !EnergyMatrixItem.hasEntitiesInTheWay(level, corePos)
                && item.getMissingBlocks(player) == 0;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType ghostType = RenderType.entityTranslucent(EnergyMatrixModel.TEXTURE);

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        AABB bounds = EnergyMatrixBlockEntity.getStructureBounds(corePos);
        if (valid) {
            LevelRenderer.renderLineBox(poseStack, lines, bounds, 0.55F, 1.0F, 0.65F, 1.0F);
        } else {
            LevelRenderer.renderLineBox(poseStack, lines, bounds, 1.0F, 0.35F, 0.35F, 1.0F);
        }
        for (BlockPos pos : obstructions) {
            LevelRenderer.renderLineBox(poseStack, lines, new AABB(pos).inflate(0.002), 1.0F, 0.1F, 0.1F, 1.0F);
        }
        buffers.endBatch(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5);
        poseStack.scale(1.0F, -1.0F, -1.0F);
        getModel(minecraft).render(poseStack, buffers.getBuffer(ghostType), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                valid ? VALID_TINT : INVALID_TINT);
        poseStack.popPose();
        buffers.endBatch(ghostType);

        poseStack.popPose();
    }

    private static @Nullable InteractionHand getHandHoldingMatrix(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof EnergyMatrixItem) {
                return hand;
            }
        }
        return null;
    }

    private static EnergyMatrixModel getModel(Minecraft minecraft) {
        if (model == null) {
            model = new EnergyMatrixModel(minecraft.getEntityModels().bakeLayer(EnergyMatrixModel.LAYER));
        }
        return model;
    }
}
