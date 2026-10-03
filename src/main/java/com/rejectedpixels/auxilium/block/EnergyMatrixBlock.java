package com.rejectedpixels.auxilium.block;

import com.rejectedpixels.auxilium.block.entity.EnergyMatrixBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;


public class EnergyMatrixBlock extends Block implements EntityBlock {
    public static final BooleanProperty CORE = BooleanProperty.create("core");
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public EnergyMatrixBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CORE, false).setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CORE, FORMED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(CORE, true);
    }

    // Once formed, the whole structure is drawn by EnergyMatrixRenderer
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(CORE) ? new EnergyMatrixBlockEntity(pos, state) : null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null : (world, pos, blockstate, entity) -> {
            if(entity instanceof EnergyMatrixBlockEntity matrixCore) {
                matrixCore.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EnergyMatrixBlockEntity matrixCore) {
            matrixCore.startBuilding();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockPos corePos = state.getValue(CORE) ? pos : EnergyMatrixBlockEntity.findCorePos(level, pos).orElse(null);
            if (corePos != null && level.getBlockEntity(corePos) instanceof EnergyMatrixBlockEntity matrixCore) {
                matrixCore.demolish();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!state.getValue(CORE)) {
            return EnergyMatrixBlockEntity.findCorePos(level, pos)
                    .map(corePos -> level.getBlockState(corePos).useWithoutItem(level, player, hitResult.withPosition(corePos)))
                    .orElse(InteractionResult.PASS);
        }
        if (!state.getValue(FORMED)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            openContainer(level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof EnergyMatrixBlockEntity matrixCore) {
            player.openMenu(matrixCore, pos);
        }
    }
}
