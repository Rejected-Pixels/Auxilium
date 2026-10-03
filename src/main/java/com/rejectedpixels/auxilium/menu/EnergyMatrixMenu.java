package com.rejectedpixels.auxilium.menu;

import com.rejectedpixels.auxilium.config.AuxiliumConfig;
import com.rejectedpixels.auxilium.block.entity.EnergyMatrixBlockEntity;
import com.rejectedpixels.auxilium.registry.BlockRegistry;
import com.rejectedpixels.auxilium.registry.MenuTypeRegistry;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.event.PlayerAttemptLearnEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

public class EnergyMatrixMenu extends AbstractContainerMenu {
    public static final int CHARGE_SLOT = 0;
    public static final int TARGET_SLOT = 1;
    private static final int OUTPUT_START = 2;
    private static final int OUTPUT_END = OUTPUT_START + EnergyMatrixBlockEntity.OUTPUT_SLOTS;
    private static final int INVENTORY_START = OUTPUT_END;
    private static final int HOTBAR_END = INVENTORY_START + 36;

    // Layout, shared with EnergyMatrixScreen.
    public static final int CHARGE_X = 152;
    public static final int CHARGE_Y = 24;
    public static final int TARGET_X = 26;
    public static final int TARGET_Y = 46;
    public static final int OUTPUT_X = 8;
    public static final int OUTPUT_Y = 72;
    public static final int INVENTORY_Y = 126;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final @Nullable EnergyMatrixBlockEntity blockEntity;

    public EnergyMatrixMenu(int containerId, Inventory inventory, FriendlyByteBuf extraData) {
        this(containerId, inventory, null, new ItemStackHandler(1), new ItemStackHandler(1),
                new ItemStackHandler(EnergyMatrixBlockEntity.OUTPUT_SLOTS),
                new SimpleContainerData(EnergyMatrixBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL);
    }

    public EnergyMatrixMenu(int containerId, Inventory inventory, EnergyMatrixBlockEntity energyMatrix) {
        this(containerId, inventory, energyMatrix, energyMatrix.getChargeSlot(), energyMatrix.getTarget(), energyMatrix.getOutput(),
                energyMatrix.getContainerData(), ContainerLevelAccess.create(energyMatrix.getLevel(), energyMatrix.getBlockPos()));
    }

    private EnergyMatrixMenu(int containerId, Inventory inventory, @Nullable EnergyMatrixBlockEntity blockEntity,
                                 IItemHandler charge, IItemHandler target, IItemHandler output, ContainerData data, ContainerLevelAccess access) {
        super(MenuTypeRegistry.ENERGY_MATRIX.get(), containerId);
        this.blockEntity = blockEntity;
        this.access = access;
        this.data = data;

        addSlot(new SlotItemHandler(charge, 0, CHARGE_X, CHARGE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isEmcHolder(stack);
            }
        });
        // Ghost slot: shows the target but never holds a real item. Clicks are handled in clicked().
        addSlot(new SlotItemHandler(target, 0, TARGET_X, TARGET_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new SlotItemHandler(output, column + row * 9, OUTPUT_X + column * 18, OUTPUT_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }

        addDataSlots(data);
    }

    /**
     * Whether a player may target an item they aren't holding (dragged in from JEI): only items they've learned
     * in a Transmutation Table, so JEI can't be used to skip ProjectE progression. Creative players can target anything.
     * ProjectE syncs knowledge to the client, so this works on both sides.
     */
    public static boolean canTargetUnheld(Player player, ItemStack stack) {
        if (player.isCreative()) return true;
        IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        return knowledge != null && knowledge.hasKnowledge(stack);
    }

    private static boolean isEmcHolder(ItemStack stack) {
        return stack.getCapability(PECapabilities.EMC_HOLDER_ITEM_CAPABILITY) != null;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == TARGET_SLOT) {
            if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) {
                ItemStack carried = clickType == ClickType.QUICK_MOVE ? ItemStack.EMPTY : getCarried();
                // Targeting an item you're actually holding also teaches it, like putting it in a Transmutation Table.
                if (setTarget(carried) && !carried.isEmpty() && player instanceof ServerPlayer serverPlayer) {
                    learn(serverPlayer, carried);
                }
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * Server side: sets (or clears, with an empty stack) the ghost target. Used by slot clicks and by
     * SetEnergyMatrixTargetPayload for items dragged in from JEI. Does nothing on the client; the slot syncs.
     * Returns whether the target was set (false for items without an EMC value).
     */
    public boolean setTarget(ItemStack stack) {
        return blockEntity != null && blockEntity.setTarget(stack);
    }

    /**
     * Adds the item to the player's transmutation knowledge if they don't know it yet.
     */
    private static void learn(ServerPlayer player, ItemStack stack) {
        IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        if (knowledge == null) return;
        ItemInfo info = ItemInfo.fromStack(stack);
        ItemInfo cleaned = IEMCProxy.INSTANCE.getPersistentInfo(info);
        if (knowledge.hasKnowledge(cleaned)) return;
        if (NeoForge.EVENT_BUS.post(new PlayerAttemptLearnEvent(player, info, cleaned)).isCanceled()) return;
        if (knowledge.addKnowledge(cleaned)) {
            knowledge.syncKnowledgeChange(player, cleaned, true);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index == TARGET_SLOT || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < INVENTORY_START) {
            if (!moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, true)) return ItemStack.EMPTY;
        } else if (isEmcHolder(stack)) {
            if (!moveItemStackTo(stack, CHARGE_SLOT, CHARGE_SLOT + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;

        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, BlockRegistry.ENERGY_MATRIX.get());
    }

    public int getLightLevel() {
        return data.get(EnergyMatrixBlockEntity.DATA_LIGHT_LEVEL);
    }

    public long getEmcPerSecond() {
        return AuxiliumConfig.emcPerSecond() * getLightLevel() / EnergyMatrixBlockEntity.MAX_LIGHT_LEVEL;
    }

    public long getStoredEmc() {
        return readLong(EnergyMatrixBlockEntity.DATA_EMC_START);
    }

    public long getTargetProgress() {
        return readLong(EnergyMatrixBlockEntity.DATA_PROGRESS_START);
    }

    private long readLong(int start) {
        long value = 0;
        int mask = (1 << EnergyMatrixBlockEntity.EMC_CHUNK_BITS) - 1;
        for (int i = 0; i < EnergyMatrixBlockEntity.DATA_EMC_CHUNKS; i++) {
            long chunk = data.get(start + i) & mask;
            value |= chunk << (i * EnergyMatrixBlockEntity.EMC_CHUNK_BITS);
        }
        return value;
    }

    public ItemStack getTargetStack() {
        return slots.get(TARGET_SLOT).getItem();
    }

    public long getTargetEmcValue() {
        ItemStack target = getTargetStack();
        return target.isEmpty() ? 0 : IEMCProxy.INSTANCE.getValue(target);
    }
}
