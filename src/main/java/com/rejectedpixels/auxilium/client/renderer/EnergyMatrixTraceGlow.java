package com.rejectedpixels.auxilium.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import com.rejectedpixels.auxilium.Auxilium;
import com.rejectedpixels.auxilium.client.model.EnergyMatrixModel;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Animated glow for the circuit traces on the formed Energy Matrix, generated at runtime from the entity texture
 * (the texture itself is never changed). On load, the green/blue trace pixels are found and each one is given its
 * distance along its circuit from the middle of its face. Every frame, light pulses are painted travelling outward
 * along those paths into a dynamic texture, which EnergyMatrixRenderer draws over the model as an emissive layer.
 */
public final class EnergyMatrixTraceGlow {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Auxilium.MODID, "dynamic/energy_matrix_trace_glow");

    // Pulse shape, in texture pixels: how fast a pulse travels, the gap between pulses and the length of its fading tail.
    private static final double PULSE_SPEED = 14.0;
    private static final double PULSE_SPACING = 36.0;
    private static final double PULSE_TAIL = 7.0;
    // How much a glowing pixel is pushed towards white, so pulses read as light rather than just a brighter trace.
    private static final double WHITEN = 0.35;
    // Each circuit also fades in and out slowly on its own cycle, so traces switch "on and off" out of step.
    private static final double GATE_MIN_SPEED = 0.35;
    private static final double GATE_MAX_SPEED = 0.9;
    // Re-painting ~30 times a second is plenty for a smooth pulse.
    private static final long FRAME_MILLIS = 33;
    // A pixel counts as a trace when it's strongly saturated and green or blue; stone, frame, orb and port are all near-grey.
    private static final int MIN_SATURATION = 48;
    private static final int MIN_HUE_LEAD = 24;
    // Box UV layout of EnergyMatrixModel at 256x128 (top, bottom, then the four sides).
    private static final int[][] FACES = {
            {48, 0, 48, 48}, {96, 0, 48, 48},
            {0, 48, 48, 64}, {48, 48, 48, 64}, {96, 48, 48, 64}, {144, 48, 48, 64}
    };

    private static @Nullable DynamicTexture texture;
    private static boolean loadFailed;
    private static boolean painted;
    private static long lastPaint;

    // One entry per trace pixel.
    private static int[] traceX = new int[0];
    private static int[] traceY = new int[0];
    private static int[] traceColor = new int[0];
    private static int[] traceDistance = new int[0];
    private static int[] traceCircuit = new int[0];
    // One entry per connected circuit.
    private static double[] circuitPulseOffset = new double[0];
    private static double[] circuitGateSpeed = new double[0];
    private static double[] circuitGatePhase = new double[0];

    private EnergyMatrixTraceGlow() {
    }

    /** Repaints the glow if it's due and returns its texture, or null if the entity texture couldn't be read. */
    public static @Nullable ResourceLocation prepare() {
        if (texture == null && !loadFailed) load();
        if (texture == null) return null;
        // Compare elapsed time with a flag for the first paint; a sentinel like Long.MIN_VALUE would overflow the subtraction.
        long now = Util.getMillis();
        if (!painted || now - lastPaint >= FRAME_MILLIS) {
            painted = true;
            lastPaint = now;
            paint(now / 1000.0);
        }
        return TEXTURE;
    }

    /** Called on resource reload (e.g. F3+T) so an edited entity texture is picked up. */
    public static void reset() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            texture = null;
        }
        loadFailed = false;
        painted = false;
    }

    private static void load() {
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(EnergyMatrixModel.TEXTURE);
             NativeImage source = NativeImage.read(in)) {
            analyse(source);
            texture = new DynamicTexture(new NativeImage(source.getWidth(), source.getHeight(), true));
            Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
            Auxilium.LOGGER.info("Energy Matrix trace glow: found {} trace pixels in {} circuits", traceX.length, circuitPulseOffset.length);
        } catch (IOException e) {
            loadFailed = true;
            Auxilium.LOGGER.warn("Couldn't read {} for the Energy Matrix trace glow", EnergyMatrixModel.TEXTURE, e);
        }
    }

    /** Finds the trace pixels, splits them into connected circuits and measures each pixel's distance along its circuit. */
    private static void analyse(NativeImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        // Scale the 256x128 face layout in case the texture is drawn at a higher resolution.
        double scale = width / 256.0;

        boolean[] isTrace = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                isTrace[y * width + x] = looksLikeTrace(source.getPixelRGBA(x, y));
            }
        }

        List<int[]> pixels = new ArrayList<>(); // {x, y, color, distance, circuit}
        boolean[] visited = new boolean[width * height];
        int circuits = 0;

        for (int[] face : FACES) {
            int fx = (int) (face[0] * scale), fy = (int) (face[1] * scale);
            int fw = (int) (face[2] * scale), fh = (int) (face[3] * scale);
            double centreX = fx + fw / 2.0, centreY = fy + fh / 2.0;

            for (int y = fy; y < fy + fh; y++) {
                for (int x = fx; x < fx + fw; x++) {
                    int start = y * width + x;
                    if (!isTrace[start] || visited[start]) continue;

                    List<Integer> circuit = new ArrayList<>();
                    ArrayDeque<Integer> queue = new ArrayDeque<>();
                    queue.add(start);
                    visited[start] = true;
                    while (!queue.isEmpty()) {
                        int index = queue.poll();
                        circuit.add(index);
                        int px = index % width, py = index / width;
                        for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            int nx = px + step[0], ny = py + step[1];
                            if (nx < fx || ny < fy || nx >= fx + fw || ny >= fy + fh) continue;
                            int next = ny * width + nx;
                            if (isTrace[next] && !visited[next]) {
                                visited[next] = true;
                                queue.add(next);
                            }
                        }
                    }

                    // Pulses start from the pixel closest to the middle of the face (the orb, or the hub on the sides)
                    // and run outward, so the glow looks like energy being spread through the circuit.
                    int seed = circuit.getFirst();
                    double best = Double.MAX_VALUE;
                    for (int index : circuit) {
                        double dx = index % width - centreX, dy = index / width - centreY;
                        double d = dx * dx + dy * dy;
                        if (d < best) {
                            best = d;
                            seed = index;
                        }
                    }
                    int[] distance = distancesFrom(seed, circuit, width);
                    for (int i = 0; i < circuit.size(); i++) {
                        int index = circuit.get(i);
                        pixels.add(new int[]{index % width, index / width, source.getPixelRGBA(index % width, index / width), distance[i], circuits});
                    }
                    circuits++;
                }
            }
        }

        int count = pixels.size();
        traceX = new int[count];
        traceY = new int[count];
        traceColor = new int[count];
        traceDistance = new int[count];
        traceCircuit = new int[count];
        for (int i = 0; i < count; i++) {
            int[] p = pixels.get(i);
            traceX[i] = p[0];
            traceY[i] = p[1];
            traceColor[i] = p[2];
            traceDistance[i] = p[3];
            traceCircuit[i] = p[4];
        }

        Random random = new Random(0x5EED);
        circuitPulseOffset = new double[circuits];
        circuitGateSpeed = new double[circuits];
        circuitGatePhase = new double[circuits];
        for (int c = 0; c < circuits; c++) {
            circuitPulseOffset[c] = random.nextDouble() * PULSE_SPACING;
            circuitGateSpeed[c] = GATE_MIN_SPEED + random.nextDouble() * (GATE_MAX_SPEED - GATE_MIN_SPEED);
            circuitGatePhase[c] = random.nextDouble() * Math.PI * 2;
        }
    }

    private static int[] distancesFrom(int seed, List<Integer> circuit, int width) {
        Map<Integer, Integer> order = new HashMap<>();
        for (int i = 0; i < circuit.size(); i++) order.put(circuit.get(i), i);
        int[] distance = new int[circuit.size()];
        Arrays.fill(distance, -1);

        ArrayDeque<Integer> queue = new ArrayDeque<>();
        distance[order.get(seed)] = 0;
        queue.add(seed);
        while (!queue.isEmpty()) {
            int index = queue.poll();
            int d = distance[order.get(index)];
            int px = index % width, py = index / width;
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = px + step[0], ny = py + step[1];
                if (nx < 0 || ny < 0 || nx >= width) continue;
                Integer next = order.get(ny * width + nx);
                if (next != null && distance[next] < 0) {
                    distance[next] = d + 1;
                    queue.add(circuit.get(next));
                }
            }
        }
        return distance;
    }

    private static boolean looksLikeTrace(int abgr) {
        int a = abgr >>> 24, r = abgr & 0xFF, g = (abgr >> 8) & 0xFF, b = (abgr >> 16) & 0xFF;
        if (a < 128) return false;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return max - min >= MIN_SATURATION && (g >= r + MIN_HUE_LEAD || b >= r + MIN_HUE_LEAD);
    }

    private static void paint(double seconds) {
        NativeImage image = texture.getPixels();
        if (image == null) return;
        for (int i = 0; i < traceX.length; i++) {
            int circuit = traceCircuit[i];
            double behind = seconds * PULSE_SPEED + circuitPulseOffset[circuit] - traceDistance[i];
            behind = ((behind % PULSE_SPACING) + PULSE_SPACING) % PULSE_SPACING;
            double pulse = behind < PULSE_TAIL ? 1.0 - behind / PULSE_TAIL : 0.0;
            double gate = 0.5 + 0.5 * Math.sin(seconds * circuitGateSpeed[circuit] + circuitGatePhase[circuit]);
            double intensity = pulse * gate * gate;

            int color = traceColor[i];
            int r = glowChannel(color & 0xFF, intensity);
            int g = glowChannel((color >> 8) & 0xFF, intensity);
            int b = glowChannel((color >> 16) & 0xFF, intensity);
            image.setPixelRGBA(traceX[i], traceY[i], 0xFF000000 | b << 16 | g << 8 | r);
        }
        texture.upload();
    }

    private static int glowChannel(int channel, double intensity) {
        double whitened = channel + (255 - channel) * WHITEN;
        return (int) Math.round(whitened * intensity);
    }
}
