package com.rejectedpixels.auxilium.client.screen;

import com.rejectedpixels.auxilium.config.AuxiliumConfig;
import com.rejectedpixels.auxilium.Auxilium;
import com.rejectedpixels.auxilium.block.entity.EnergyMatrixBlockEntity;
import com.rejectedpixels.auxilium.menu.EnergyMatrixMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;
import java.util.List;

public class EnergyMatrixScreen extends AbstractContainerScreen<EnergyMatrixMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Auxilium.MODID, "textures/gui/energy_matrix.png");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();

    // Inner areas of the bars drawn into the texture.
    private static final int LIGHT_X = 9, LIGHT_Y = 19, LIGHT_W = 10, LIGHT_H = 48;
    private static final int EMC_X = 27, EMC_Y = 30, EMC_W = 120, EMC_H = 8;
    private static final int PROGRESS_X = 49, PROGRESS_Y = 51, PROGRESS_W = 98, PROGRESS_H = 8;

    private static final int[] EMC_GRADIENT = {0xFF003471, 0xFF0054A6, 0xFF0072BC, 0xFF00AEEF, 0xFF8FE3FF};
    private static final int[] PROGRESS_GRADIENT = {0xFF004D24, 0xFF007235, 0xFF1FAF5C, 0xFF3DDC84, 0xFFB5F7D0};

    public EnergyMatrixScreen(EnergyMatrixMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 208;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        guiGraphics.blit(TEXTURE, left, top, 0, 0, imageWidth, imageHeight);

        // Light meter (0-15) fills from the bottom
        int lightHeight = Math.round(LIGHT_H * (float) menu.getLightLevel() / EnergyMatrixBlockEntity.MAX_LIGHT_LEVEL);
        if (lightHeight > 0) {
            int y0 = top + LIGHT_Y + LIGHT_H - lightHeight;
            guiGraphics.fillGradient(left + LIGHT_X, y0, left + LIGHT_X + LIGHT_W, top + LIGHT_Y + LIGHT_H, 0xFFFFE27A, 0xFFE08A1A);
        }

        long stored = menu.getStoredEmc();
        int emcWidth = (int) (EMC_W * Math.min(1.0, (double) stored / AuxiliumConfig.maxEmc()));
        drawGradientBar(guiGraphics, left + EMC_X, top + EMC_Y, emcWidth, EMC_W, EMC_H, EMC_GRADIENT);

        // Progress towards the next condensed item
        long cost = menu.getTargetEmcValue();
        if (cost > 0) {
            int progressWidth = (int) (PROGRESS_W * Math.min(1.0, (double) menu.getTargetProgress() / cost));
            drawGradientBar(guiGraphics, left + PROGRESS_X, top + PROGRESS_Y, progressWidth, PROGRESS_W, PROGRESS_H, PROGRESS_GRADIENT);
        }
    }

    private static void drawGradientBar(GuiGraphics guiGraphics, int x, int y, int filledWidth, int fullWidth, int height, int[] stops) {
        for (int column = 0; column < filledWidth; column++) {
            int base = sampleGradient(stops, fullWidth <= 1 ? 1.0F : (float) column / (fullWidth - 1));
            for (int row = 0; row < height; row++) {
                int shaded;
                if (row == 0) shaded = mix(base, 0xFFFFFFFF, 0.45F);              // bright rim
                else if (row < height / 2) shaded = mix(base, 0xFFFFFFFF, 0.15F); // upper glow
                else if (row == height - 1) shaded = mix(base, 0xFF000000, 0.35F); // dark lower edge
                else shaded = base;
                guiGraphics.fill(x + column, y + row, x + column + 1, y + row + 1, shaded);
            }
        }
    }

    private static int sampleGradient(int[] stops, float t) {
        float scaled = Math.clamp(t, 0.0F, 1.0F) * (stops.length - 1);
        int index = Math.min((int) scaled, stops.length - 2);
        return mix(stops[index], stops[index + 1], scaled - index);
    }

    private static int mix(int from, int to, float amount) {
        int a = (int) Mth.lerp(amount, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF);
        int r = (int) Mth.lerp(amount, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(amount, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(amount, from & 0xFF, to & 0xFF);
        return a << 24 | r << 16 | g << 8 | b;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);
        guiGraphics.drawString(font, Component.translatable("gui.auxilium.energy_matrix.stored",
                compact(menu.getStoredEmc()), compact(AuxiliumConfig.maxEmc())), 27, 19, 0x404040, false);

        long cost = menu.getTargetEmcValue();
        Component status = cost > 0
                ? Component.translatable("gui.auxilium.energy_matrix.cost", compact(cost))
                : Component.translatable("gui.auxilium.energy_matrix.rate", compact(menu.getEmcPerSecond()));
        guiGraphics.drawString(font, status, 49, 62, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        long stored = menu.getStoredEmc();
        long cost = menu.getTargetEmcValue();
        if (isHovering(LIGHT_X, LIGHT_Y, LIGHT_W, LIGHT_H, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.auxilium.energy_matrix.light", menu.getLightLevel(), EnergyMatrixBlockEntity.MAX_LIGHT_LEVEL),
                    Component.translatable("gui.auxilium.energy_matrix.rate", NUMBER_FORMAT.format(menu.getEmcPerSecond())).withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        } else if (isHovering(EMC_X, EMC_Y, EMC_W, EMC_H, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.auxilium.energy_matrix.stored",
                            NUMBER_FORMAT.format(stored), NUMBER_FORMAT.format(AuxiliumConfig.maxEmc())),
                    Component.translatable(cost > 0 ? "gui.auxilium.energy_matrix.mode.condensing" : "gui.auxilium.energy_matrix.mode.relaying")
                            .withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && cost > 0) {
            guiGraphics.renderTooltip(font, Component.translatable("gui.auxilium.energy_matrix.progress",
                    NUMBER_FORMAT.format(Math.min(menu.getTargetProgress(), cost)), NUMBER_FORMAT.format(cost)), mouseX, mouseY);
        } else if (hoveredSlot != null && hoveredSlot.index == EnergyMatrixMenu.TARGET_SLOT && !hoveredSlot.hasItem()) {
            guiGraphics.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.auxilium.energy_matrix.target"),
                    Component.translatable("gui.auxilium.energy_matrix.target.hint").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        } else if (hoveredSlot != null && hoveredSlot.index == EnergyMatrixMenu.CHARGE_SLOT && !hoveredSlot.hasItem()) {
            guiGraphics.renderTooltip(font, Component.translatable("gui.auxilium.energy_matrix.charge"), mouseX, mouseY);
        }
    }

    public Rect2i getTargetSlotArea() {
        return new Rect2i(leftPos + EnergyMatrixMenu.TARGET_X, topPos + EnergyMatrixMenu.TARGET_Y, 16, 16);
    }

    private static String compact(long value) {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 10_000) return String.format("%.1fk", value / 1_000.0);
        return NUMBER_FORMAT.format(value);
    }
}
