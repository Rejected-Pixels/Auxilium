package com.rejectedpixels.auxilium.item;

import com.rejectedpixels.auxilium.block.entity.EnergyMatrixBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.List;

public class EnergyMatrixItem extends BlockItem {
    public EnergyMatrixItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null || player instanceof FakePlayer || !context.canPlace()) {
            return InteractionResult.FAIL;
        }

        Level level = context.getLevel();
        BlockPos corePos = context.getClickedPos();
        int required = EnergyMatrixBlockEntity.BLOCKS_REQUIRED;

        int missing = getMissingBlocks(player);
        if (missing > 0) {
            return fail(level, player, Component.translatable("message.auxilium.energy_matrix.missing_blocks", missing));
        }
        if (!getObstructedPositions(level, corePos).isEmpty() || hasEntitiesInTheWay(level, corePos)) {
            return fail(level, player, Component.translatable("message.auxilium.energy_matrix.obstructed"));
        }

        InteractionResult result = super.place(context);
        if (result.consumesAction() && !level.isClientSide && !player.isCreative()) {
            ContainerHelper.clearOrCountMatchingItems(player.getInventory(), stack -> stack.is(this), required - 1, false);
        }
        return result;
    }

    public int getMissingBlocks(Player player) {
        if (player.isCreative()) return 0;
        return Math.max(0, EnergyMatrixBlockEntity.BLOCKS_REQUIRED - player.getInventory().countItem(this));
    }

    public static List<BlockPos> getObstructedPositions(Level level, BlockPos corePos) {
        return EnergyMatrixBlockEntity.getPartPositions(corePos).stream()
                .filter(pos -> !level.getBlockState(pos).canBeReplaced())
                .toList();
    }

    public static boolean hasEntitiesInTheWay(Level level, BlockPos corePos) {
        return !level.getEntitiesOfClass(LivingEntity.class, EnergyMatrixBlockEntity.getStructureBounds(corePos)).isEmpty();
    }

    private static InteractionResult fail(Level level, Player player, Component message) {
        if (!level.isClientSide) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
        return InteractionResult.FAIL;
    }
}
