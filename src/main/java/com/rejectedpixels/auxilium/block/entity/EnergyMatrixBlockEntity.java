package com.rejectedpixels.auxilium.block.entity;

import com.rejectedpixels.auxilium.config.AuxiliumConfig;
import com.rejectedpixels.auxilium.block.EnergyMatrixBlock;
import com.rejectedpixels.auxilium.menu.EnergyMatrixMenu;
import com.rejectedpixels.auxilium.registry.BlockEntityTypeRegistry;
import com.rejectedpixels.auxilium.registry.BlockRegistry;
import com.rejectedpixels.auxilium.registry.ItemRegistry;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.capabilities.block_entity.IEmcStorage;
import moze_intel.projecte.api.capabilities.item.IItemEmcHolder;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class EnergyMatrixBlockEntity extends BlockEntity implements MenuProvider {
    // The structure is a 3x4x3 column with the core at the bottom center.
    private static final BlockPos STRUCTURE_MIN = new BlockPos(-1, 0, -1);
    private static final BlockPos STRUCTURE_MAX = new BlockPos(1, 3, 1);

    public static final int BLOCKS_REQUIRED = 36;

    private static final int BUILD_DELAY_MIN = 3;
    private static final int BUILD_DELAY_MAX = 7;

    public static final int OUTPUT_SLOTS = 18;

    private static final int MAX_CONDENSED_PER_TICK = 8;

    // Minecraft's light scale: 0 is complete darkness (no generation), 15 is full light (the full rate).
    public static final int MAX_LIGHT_LEVEL = 15;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final int LIGHT_LEVEL_UPDATE_INTERVAL = 20;

    // Menu data: the light level plus the stored EMC split into 15-bit chunks, because container data is synced as shorts.
    public static final int DATA_LIGHT_LEVEL = 0;
    public static final int DATA_EMC_CHUNKS = 4;
    public static final int DATA_EMC_START = 1;
    public static final int DATA_PROGRESS_START = DATA_EMC_START + DATA_EMC_CHUNKS;
    public static final int DATA_COUNT = DATA_PROGRESS_START + DATA_EMC_CHUNKS;
    public static final int EMC_CHUNK_BITS = 15;

    /** Holds a Klein Star (or anything else that stores EMC) to be charged from the matrix. */
    private final ItemStackHandler chargeSlot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getCapability(PECapabilities.EMC_HOLDER_ITEM_CAPABILITY) != null;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    //Ghost copy of the item to condense
    private final ItemStackHandler target = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ItemStackHandler output = new ItemStackHandler(OUTPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** What pipes and hoppers see: condensed items can be pulled out, nothing can be put in. */
    private final IItemHandler automationOutput = new IItemHandler() {
        @Override
        public int getSlots() {
            return output.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return output.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return output.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return output.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index == DATA_LIGHT_LEVEL) return lightLevel;
            if (index < DATA_PROGRESS_START) return chunk(storedEmc, index - DATA_EMC_START);
            return chunk(targetProgress, index - DATA_PROGRESS_START);
        }

        @Override
        public void set(int index, int value) {
            // Values are derived from the block entity's state; nothing to set server side.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private static int chunk(long value, int chunkIndex) {
        return (int) (value >>> (chunkIndex * EMC_CHUNK_BITS)) & ((1 << EMC_CHUNK_BITS) - 1);
    }

    private long storedEmc;
    // EMC put towards the next target item: new generation while condensing, topped up from storage.
    private long targetProgress;
    private double unprocessedEmc;
    private int lightLevel;
    private boolean lightLevelMeasured;
    private @Nullable List<BlockCapabilityCache<IEmcStorage, Direction>> neighbourEmcCaches;

    // A generator: other blocks can pull EMC out, but nothing can be pushed in.
    private final IEmcStorage emcStorage = new IEmcStorage() {
        @Override
        public long getStoredEmc() {
            return storedEmc;
        }

        @Override
        public long getMaximumEmc() {
            return AuxiliumConfig.maxEmc();
        }

        @Override
        public long extractEmc(long toExtract, EmcAction action) {
            long extracted = Math.min(Math.max(toExtract, 0), storedEmc);
            if (action.execute() && extracted > 0) {
                storedEmc -= extracted;
                setChanged();
            }
            return extracted;
        }

        @Override
        public long insertEmc(long toAccept, EmcAction action) {
            return 0;
        }
    };

    private final List<BlockPos> buildQueue = new ArrayList<>();
    private boolean built;
    private boolean demolishing;
    private int buildDelay = BUILD_DELAY_MAX;

    public EnergyMatrixBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypeRegistry.ENERGY_MATRIX_BLOCK_ENTITY.get(), pos, blockState);
    }

    public static List<BlockPos> getPartPositions(BlockPos corePos) {
        return BlockPos.betweenClosedStream(corePos.offset(STRUCTURE_MIN), corePos.offset(STRUCTURE_MAX))
                .map(BlockPos::immutable)
                .filter(pos -> !pos.equals(corePos))
                .toList();
    }

    public static AABB getStructureBounds(BlockPos corePos) {
        return AABB.encapsulatingFullBlocks(corePos.offset(STRUCTURE_MIN), corePos.offset(STRUCTURE_MAX));
    }

    public static Optional<BlockPos> findCorePos(BlockGetter level, BlockPos partPos) {
        return BlockPos.betweenClosedStream(partPos.subtract(STRUCTURE_MAX), partPos.subtract(STRUCTURE_MIN))
                .filter(pos -> !pos.equals(partPos))
                .filter(pos -> {
                    BlockState state = level.getBlockState(pos);
                    return state.is(BlockRegistry.ENERGY_MATRIX) && state.getValue(EnergyMatrixBlock.CORE);
                })
                .map(BlockPos::immutable)
                .findFirst();
    }

    private enum BuildPattern {
        SCATTER, SPIRAL, SNAKE, EDGES_AND_CORNERS
    }

    public void startBuilding() {
        RandomSource random = level != null ? level.random : RandomSource.create();
        BuildPattern pattern = BuildPattern.values()[random.nextInt(BuildPattern.values().length)];

        Map<Integer, List<BlockPos>> layers = new TreeMap<>();
        for (BlockPos pos : getPartPositions(worldPosition)) {
            layers.computeIfAbsent(pos.getY(), y -> new ArrayList<>()).add(pos);
        }

        buildQueue.clear();
        for (List<BlockPos> layer : layers.values()) {
            buildQueue.addAll(orderLayer(layer, pattern, random));
        }
        buildDelay = nextBuildDelay(random);
        built = false;
        setChanged();
    }

    private List<BlockPos> orderLayer(List<BlockPos> layer, BuildPattern pattern, RandomSource random) {
        List<BlockPos> ordered = new ArrayList<>(layer);
        switch (pattern) {
            case SCATTER -> shuffle(ordered, random);
            case SPIRAL -> {
                BlockPos centre = null;
                List<BlockPos> ring = new ArrayList<>();
                for (BlockPos pos : ordered) {
                    if (pos.getX() == worldPosition.getX() && pos.getZ() == worldPosition.getZ()) centre = pos;
                    else ring.add(pos);
                }
                ring.sort(Comparator.comparingDouble(pos -> Math.atan2(pos.getZ() - worldPosition.getZ(), pos.getX() - worldPosition.getX())));
                Collections.rotate(ring, random.nextInt(ring.size()));
                if (random.nextBoolean()) Collections.reverse(ring);
                ordered = ring;
                if (centre != null) {
                    if (random.nextBoolean()) ordered.addFirst(centre);
                    else ordered.addLast(centre);
                }
            }
            case SNAKE -> {
                boolean alongX = random.nextBoolean();
                int flip = random.nextBoolean() ? 1 : -1;
                ordered.sort((a, b) -> {
                    int rowA = alongX ? a.getZ() : a.getX();
                    int rowB = alongX ? b.getZ() : b.getX();
                    if (rowA != rowB) return flip * Integer.compare(rowA, rowB);
                    int colA = alongX ? a.getX() : a.getZ();
                    int colB = alongX ? b.getX() : b.getZ();
                    // Odd rows run the other way so the build zig-zags.
                    int direction = Math.floorMod(rowA, 2) == 0 ? 1 : -1;
                    return direction * Integer.compare(colA, colB);
                });
            }
            case EDGES_AND_CORNERS -> {
                List<BlockPos> centre = new ArrayList<>();
                List<BlockPos> edges = new ArrayList<>();
                List<BlockPos> corners = new ArrayList<>();
                for (BlockPos pos : ordered) {
                    int distance = Math.abs(pos.getX() - worldPosition.getX()) + Math.abs(pos.getZ() - worldPosition.getZ());
                    (distance == 0 ? centre : distance == 1 ? edges : corners).add(pos);
                }
                shuffle(edges, random);
                shuffle(corners, random);
                ordered = new ArrayList<>(centre);
                if (random.nextBoolean()) {
                    ordered.addAll(edges);
                    ordered.addAll(corners);
                } else {
                    ordered.addAll(corners);
                    ordered.addAll(edges);
                }
            }
        }
        return ordered;
    }

    private static <T> void shuffle(List<T> list, RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            Collections.swap(list, i, random.nextInt(i + 1));
        }
    }

    private static int nextBuildDelay(RandomSource random) {
        return BUILD_DELAY_MIN + random.nextInt(BUILD_DELAY_MAX - BUILD_DELAY_MIN + 1);
    }

    public void serverTick() {
        if (level == null) return;
        if (built) {
            generateEmc();
            chargeItem();

            if (isCondensing()) {
                condense();
            } else {
                sendEmcToNeighbours();
            }
            return;
        }

        if (buildQueue.isEmpty()) {
            built = true;
            setChanged();
            setFormed(worldPosition);
            for (BlockPos pos : getPartPositions(worldPosition)) {
                setFormed(pos);
            }
            return;
        }

        if (buildDelay-- > 0) return;
        buildDelay = nextBuildDelay(level.random);

        BlockPos pos = buildQueue.getFirst();
        if (!level.getBlockState(pos).canBeReplaced()) {
            demolish();
            return;
        }

        BlockState partState = BlockRegistry.ENERGY_MATRIX.get().defaultBlockState();
        level.setBlockAndUpdate(pos, partState);
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(partState));
        buildQueue.removeFirst();
        setChanged();
    }

    private void setFormed(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockRegistry.ENERGY_MATRIX)) {
            level.setBlock(pos, state.setValue(EnergyMatrixBlock.FORMED, true), Block.UPDATE_ALL);
            // Only formed blocks expose EMC storage, so let neighbours re-query the capability.
            level.invalidateCapabilities(pos);
        }
    }

    private void generateEmc() {
        if (!lightLevelMeasured || level.getGameTime() % LIGHT_LEVEL_UPDATE_INTERVAL == 0) {
            lightLevel = computeLightLevel();
            lightLevelMeasured = true;
        }
        long cost = getTargetEmcValue();
        long progressRoom = cost > 0 ? Math.max(0, cost - targetProgress) : 0;
        if (progressRoom == 0 && storedEmc >= AuxiliumConfig.maxEmc()) return;

        unprocessedEmc += AuxiliumConfig.emcPerSecond() * ((double) lightLevel / MAX_LIGHT_LEVEL) / TICKS_PER_SECOND;
        if (unprocessedEmc >= 1) {
            long whole = (long) unprocessedEmc;
            unprocessedEmc -= whole;
            long toProgress = Math.min(whole, progressRoom);
            targetProgress += toProgress;
            storedEmc = Math.min(AuxiliumConfig.maxEmc(), storedEmc + (whole - toProgress));
            setChanged();
        }
    }

    private int computeLightLevel() {
        if (level.dimensionType().ultraWarm()) return MAX_LIGHT_LEVEL;
        int total = 0;
        int count = 0;
        for (BlockPos pos : getSurroundingPositions(worldPosition, false)) {
            total += level.getMaxLocalRawBrightness(pos);
            count++;
        }
        return Math.clamp(Math.round((float) total / count), 0, MAX_LIGHT_LEVEL);
    }

    private void chargeItem() {
        ItemStack stack = chargeSlot.getStackInSlot(0);
        if (stack.isEmpty() || storedEmc <= 0) return;
        IItemEmcHolder holder = stack.getCapability(PECapabilities.EMC_HOLDER_ITEM_CAPABILITY);
        if (holder == null) return;
        long inserted = holder.insertEmc(stack, Math.min(storedEmc, AuxiliumConfig.transferPerTick()), IEmcStorage.EmcAction.EXECUTE);
        if (inserted > 0) {
            storedEmc -= inserted;
            setChanged();
        }
    }

    private void condense() {
        long cost = getTargetEmcValue();
        if (cost <= 0) return;
        ItemStack template = target.getStackInSlot(0);
        for (int made = 0; made < MAX_CONDENSED_PER_TICK; made++) {
            ItemStack single = template.copyWithCount(1);
            if (!ItemHandlerHelper.insertItemStacked(output, single, true).isEmpty()) return;

            if (targetProgress < cost) {
                long fromStorage = Math.min(storedEmc, cost - targetProgress);
                storedEmc -= fromStorage;
                targetProgress += fromStorage;
                if (fromStorage > 0) setChanged();
            }
            if (targetProgress < cost) return;

            ItemHandlerHelper.insertItemStacked(output, single, false);
            targetProgress -= cost;
            setChanged();
        }
    }

    private void refundTargetProgress() {
        if (targetProgress <= 0) return;
        storedEmc = Math.min(AuxiliumConfig.maxEmc(), storedEmc + targetProgress);
        targetProgress = 0;
        setChanged();
    }

    public boolean hasTarget() {
        return !target.getStackInSlot(0).isEmpty();
    }

    public boolean isCondensing() {
        return getTargetEmcValue() > 0;
    }

    public long getTargetEmcValue() {
        ItemStack stack = target.getStackInSlot(0);
        return stack.isEmpty() ? 0 : IEMCProxy.INSTANCE.getValue(stack);
    }

    public boolean setTarget(ItemStack stack) {
        ItemStack newTarget = ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            ItemInfo info = IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(stack));
            if (!IEMCProxy.INSTANCE.hasValue(info)) return false;
            newTarget = info.createStack();
        }

        if (!ItemStack.isSameItemSameComponents(target.getStackInSlot(0), newTarget)) {
            refundTargetProgress();
        }
        target.setStackInSlot(0, newTarget);
        return true;
    }

    private void sendEmcToNeighbours() {
        if (storedEmc <= 0 || !(level instanceof ServerLevel serverLevel)) return;

        if (neighbourEmcCaches == null) {
            neighbourEmcCaches = new ArrayList<>();
            for (BlockPos pos : getSurroundingPositions(worldPosition, true)) {
                neighbourEmcCaches.add(BlockCapabilityCache.create(PECapabilities.EMC_STORAGE_CAPABILITY, serverLevel, pos, facingStructure(pos)));
            }
        }

        List<IEmcStorage> acceptors = new ArrayList<>();
        for (BlockCapabilityCache<IEmcStorage, Direction> cache : neighbourEmcCaches) {
            IEmcStorage target = cache.getCapability();
            if (target != null && target.insertEmc(1, IEmcStorage.EmcAction.SIMULATE) > 0) {
                acceptors.add(target);
            }
        }
        if (acceptors.isEmpty()) return;

        long perTarget = Math.min(storedEmc, AuxiliumConfig.transferPerTick()) / acceptors.size();
        if (perTarget <= 0) return;
        for (IEmcStorage target : acceptors) {
            long accepted = target.insertEmc(Math.min(perTarget, storedEmc), IEmcStorage.EmcAction.EXECUTE);
            emcStorage.extractEmc(accepted, IEmcStorage.EmcAction.EXECUTE);
        }
    }

    private static List<BlockPos> getSurroundingPositions(BlockPos corePos, boolean includeBottom) {
        BlockPos min = corePos.offset(STRUCTURE_MIN);
        BlockPos max = corePos.offset(STRUCTURE_MAX);
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min.offset(-1, -1, -1), max.offset(1, 1, 1))) {
            int outsideAxes = (pos.getX() < min.getX() || pos.getX() > max.getX() ? 1 : 0)
                    + (pos.getY() < min.getY() || pos.getY() > max.getY() ? 1 : 0)
                    + (pos.getZ() < min.getZ() || pos.getZ() > max.getZ() ? 1 : 0);
            if (outsideAxes != 1) continue;
            if (!includeBottom && pos.getY() < min.getY()) continue;
            positions.add(pos.immutable());
        }
        return positions;
    }

    private Direction facingStructure(BlockPos neighbour) {
        BlockPos min = worldPosition.offset(STRUCTURE_MIN);
        BlockPos max = worldPosition.offset(STRUCTURE_MAX);
        if (neighbour.getY() > max.getY()) return Direction.DOWN;
        if (neighbour.getY() < min.getY()) return Direction.UP;
        if (neighbour.getX() > max.getX()) return Direction.WEST;
        if (neighbour.getX() < min.getX()) return Direction.EAST;
        if (neighbour.getZ() > max.getZ()) return Direction.NORTH;
        return Direction.SOUTH;
    }

    public @Nullable IEmcStorage getEmcStorage() {
        return built ? emcStorage : null;
    }

    public long getStoredEmc() {
        return storedEmc;
    }

    public int getLightLevel() {
        return lightLevel;
    }

    public void demolish() {
        if (level == null || level.isClientSide || demolishing) return;
        demolishing = true;

        int refund = buildQueue.size();
        for (BlockPos pos : getPartPositions(worldPosition)) {
            if (buildQueue.contains(pos)) continue;
            if (level.getBlockState(pos).is(BlockRegistry.ENERGY_MATRIX)) {
                level.removeBlock(pos, false);
                refund++;
            }
        }
        buildQueue.clear();

        if (level.getBlockState(worldPosition).is(BlockRegistry.ENERGY_MATRIX)) {
            level.removeBlock(worldPosition, false);
            refund++;
        }
        if (refund > 0) {
            Block.popResource(level, worldPosition, new ItemStack(ItemRegistry.ENERGY_MATRIX.get(), refund));
        }
        for (ItemStackHandler handler : List.of(chargeSlot, output)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                Block.popResource(level, worldPosition, handler.getStackInSlot(slot));
                handler.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    public boolean isBuilt() {
        return built;
    }

    public ItemStackHandler getChargeSlot() {
        return chargeSlot;
    }

    public ItemStackHandler getTarget() {
        return target;
    }

    public ItemStackHandler getOutput() {
        return output;
    }

    public @Nullable IItemHandler getAutomationItemHandler() {
        return built ? automationOutput : null;
    }

    public ContainerData getContainerData() {
        return data;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("built", built);
        tag.putLongArray("build_queue", buildQueue.stream().mapToLong(BlockPos::asLong).toArray());
        tag.put("charge", chargeSlot.serializeNBT(registries));
        tag.put("target", target.serializeNBT(registries));
        tag.put("output", output.serializeNBT(registries));
        tag.putLong("stored_emc", storedEmc);
        tag.putLong("target_progress", targetProgress);
        tag.putDouble("unprocessed_emc", unprocessedEmc);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        built = tag.getBoolean("built");
        buildQueue.clear();
        Arrays.stream(tag.getLongArray("build_queue")).mapToObj(BlockPos::of).forEach(buildQueue::add);
        chargeSlot.deserializeNBT(registries, tag.getCompound("charge"));
        target.deserializeNBT(registries, tag.getCompound("target"));
        output.deserializeNBT(registries, tag.getCompound("output"));
        storedEmc = Math.clamp(tag.getLong("stored_emc"), 0, AuxiliumConfig.maxEmc());
        targetProgress = Math.max(0, tag.getLong("target_progress"));
        unprocessedEmc = tag.getDouble("unprocessed_emc");
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new EnergyMatrixMenu(containerId, playerInventory, this);
    }
}
