package its.vlxd.graffiti.client.renderer;

import its.vlxd.graffiti.GraffitiMod;
import its.vlxd.graffiti.config.GraffitiConfig;
import its.vlxd.graffiti.network.PaintPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GraffitiRenderer {
    private static final int GRID_SIZE = 16;
    private static final float Z_FIGHT_BIAS = 0.005f;

    public static final List<PaintPayload> PIXELS = Collections.synchronizedList(new ArrayList<>());
    public static final Map<Long, Map<Direction, int[][]>> GRAFFITI_CACHE = new ConcurrentHashMap<>();

    private static String lastWorldName = "";
    private static boolean needsSave = false;
    private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.parse("graffiti:textures/misc/white.png");

    private static Frustum frustum;

    public static void init() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (!GraffitiConfig.get().enabled) return;

        var world = Minecraft.getInstance().level;
        if (world == null) return;

        checkAndLoadWorldData();

        synchronized (PIXELS) {
            if (!PIXELS.isEmpty()) {
                for (PaintPayload p : PIXELS) {
                    GRAFFITI_CACHE.computeIfAbsent(p.pos().asLong(), k -> new EnumMap<>(Direction.class))
                            .computeIfAbsent(p.side(), k -> new int[GRID_SIZE][GRID_SIZE])[p.u()][p.v()] = p.color();
                }
                PIXELS.clear();
                needsSave = true;
            }
        }

        if (GRAFFITI_CACHE.isEmpty()) return;

        var poseStack = event.getPoseStack();
        var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        var buffer = bufferSource.getBuffer(RenderType.entityTranslucent(WHITE_TEXTURE));
        var camera = event.getCamera();
        var cameraPos = camera.getPosition();

        frustum = event.getFrustum();

        double maxDistSq = Math.pow(GraffitiConfig.get().renderDistance, 2);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Iterator<Map.Entry<Long, Map<Direction, int[][]>>> it = GRAFFITI_CACHE.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            BlockPos pos = BlockPos.of(entry.getKey());

            if (pos.distToCenterSqr(cameraPos) > maxDistSq) continue;

            if (GraffitiConfig.get().useCulling && frustum != null) {
                if (!frustum.isVisible(new AABB(pos))) continue;
            }

            if (world.getBlockState(pos).isAir()) {
                it.remove();
                needsSave = true;
                continue;
            }

            for (var sideEntry : entry.getValue().entrySet()) {
                renderOptimizedFace(poseStack, buffer, pos, sideEntry.getKey(), sideEntry.getValue(), world);
            }
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.entityTranslucent(WHITE_TEXTURE));

        if (needsSave) {
            save();
            needsSave = false;
        }
    }

    private static void renderOptimizedFace(com.mojang.blaze3d.vertex.PoseStack matrices, com.mojang.blaze3d.vertex.VertexConsumer buffer, BlockPos pos, Direction side, int[][] grid, net.minecraft.client.multiplayer.ClientLevel world) {
        boolean[][] visited = new boolean[GRID_SIZE][GRID_SIZE];
        float[][] depths = new float[GRID_SIZE][GRID_SIZE];

        for (int v = 0; v < GRID_SIZE; v++) {
            for (int u = 0; u < GRID_SIZE; u++) {
                if (grid[u][v] != 0) {
                    depths[u][v] = getPixelDepth(world, pos, side, u, v);
                }
            }
        }

        int light = LevelRenderer.getLightColor(world, pos.relative(side));
        matrices.pushPose();
        matrices.translate(pos.getX(), pos.getY(), pos.getZ());
        Matrix4f model = matrices.last().pose();

        for (int v = 0; v < GRID_SIZE; v++) {
            for (int u = 0; u < GRID_SIZE; u++) {
                int color = grid[u][v];
                if (color != 0 && !visited[u][v]) {
                    float currentDepth = depths[u][v];

                    int w = 1;
                    while (u + w < GRID_SIZE && grid[u + w][v] == color && !visited[u + w][v] && Math.abs(depths[u + w][v] - currentDepth) < 0.001f) w++;

                    int h = 1;
                    while (v + h < GRID_SIZE) {
                        boolean canExpand = true;
                        for (int k = 0; k < w; k++) {
                            if (grid[u + k][v + h] != color || visited[u + k][v + h] || Math.abs(depths[u + k][v + h] - currentDepth) > 0.001f) {
                                canExpand = false;
                                break;
                            }
                        }
                        if (!canExpand) break;
                        h++;
                    }

                    for (int dy = 0; dy < h; dy++) {
                        for (int dx = 0; dx < w; dx++) visited[u + dx][v + dy] = true;
                    }

                    drawQuad(model, buffer, side, u, v, w, h, color, light, currentDepth);
                }
            }
        }
        matrices.popPose();
    }

    private static void drawQuad(Matrix4f m, com.mojang.blaze3d.vertex.VertexConsumer b, Direction side, int u, int v, int w, int h, int color, int light, float offset) {
        float s = 1f / GRID_SIZE;
        float bias = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? Z_FIGHT_BIAS : -Z_FIGHT_BIAS;
        float z = offset + bias;

        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        float u1 = u * s, u2 = (u + w) * s;
        float v1 = v * s, v2 = (v + h) * s;

        switch (side) {
            case UP ->    quad(m, b, side, u1, z, v1, u1, z, v2, u2, z, v2, u2, z, v1, red, green, blue, alpha, light);
            case DOWN ->  quad(m, b, side, u1, z, v1, u2, z, v1, u2, z, v2, u1, z, v2, red, green, blue, alpha, light);
            case NORTH -> quad(m, b, side, u1, v1, z, u1, v2, z, u2, v2, z, u2, v1, z, red, green, blue, alpha, light);
            case SOUTH -> quad(m, b, side, u1, v1, z, u2, v1, z, u2, v2, z, u1, v2, z, red, green, blue, alpha, light);
            case WEST ->  quad(m, b, side, z, v1, u1, z, v1, u2, z, v2, u2, z, v2, u1, red, green, blue, alpha, light);
            case EAST ->  quad(m, b, side, z, v1, u1, z, v2, u1, z, v2, u2, z, v1, u2, red, green, blue, alpha, light);
        }
    }

    private static float getPixelDepth(net.minecraft.world.level.BlockGetter world, BlockPos pos, Direction side, int u, int v) {
        var state = world.getBlockState(pos);
        VoxelShape shape = state.getShape(world, pos);
        if (shape.isEmpty()) return side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0f : 0.0f;

        float cx = (u + 0.5f) / GRID_SIZE;
        float cy = (v + 0.5f) / GRID_SIZE;
        double rx = pos.getX(), ry = pos.getY(), rz = pos.getZ();

        switch (side) {
            case UP, DOWN -> { rx += cx; rz += cy; }
            case NORTH, SOUTH -> { rx += cx; ry += cy; }
            case WEST, EAST -> { rz += cx; ry += cy; }
        }

        double dx = side.getStepX(), dy = side.getStepY(), dz = side.getStepZ();
        Vec3 start = new Vec3(rx + dx, ry + dy, rz + dz);
        Vec3 end = new Vec3(rx - dx, ry - dy, rz - dz);

        var hit = shape.clip(start, end, pos);
        if (hit != null) {
            var hitPos = hit.getLocation();
            return (float) (switch (side.getAxis()) {
                case X -> hitPos.x - pos.getX();
                case Y -> hitPos.y - pos.getY();
                case Z -> hitPos.z - pos.getZ();
            });
        }
        return (float) (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? shape.max(side.getAxis()) : shape.min(side.getAxis()));
    }

    private static void quad(Matrix4f m, com.mojang.blaze3d.vertex.VertexConsumer b, Direction side, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int r, int g, int bl, int a, int light) {
        v(m, b, side, x1, y1, z1, r, g, bl, a, light);
        v(m, b, side, x2, y2, z2, r, g, bl, a, light);
        v(m, b, side, x3, y3, z3, r, g, bl, a, light);
        v(m, b, side, x4, y4, z4, r, g, bl, a, light);
    }

    private static void v(Matrix4f m, com.mojang.blaze3d.vertex.VertexConsumer b, Direction side, float x, float y, float z, int r, int g, int bl, int a, int light) {
        b.addVertex(m, x, y, z).setColor(r, g, bl, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(side.getStepX(), side.getStepY(), side.getStepZ());
    }

    public static void save() {
        if (lastWorldName.isEmpty()) return;
        File file = getSaveFile();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file))))) {
            List<long[]> data = new ArrayList<>();
            GRAFFITI_CACHE.forEach((pos, sides) -> sides.forEach((side, grid) -> {
                for (int u = 0; u < GRID_SIZE; u++) {
                    for (int v = 0; v < GRID_SIZE; v++) {
                        if (grid[u][v] != 0) data.add(new long[]{pos, side.get3DDataValue(), u, v, grid[u][v]});
                    }
                }
            }));
            out.writeInt(data.size());
            for (long[] p : data) {
                out.writeLong(p[0]);
                out.writeByte((int)p[1]);
                out.writeByte((int)p[2]);
                out.writeByte((int)p[3]);
                out.writeInt((int)p[4]);
            }
        } catch (IOException e) {
            GraffitiMod.LOGGER.error("Failed to save graffiti cache", e);
        }
    }

    public static void load() {
        File file = getSaveFile();
        GRAFFITI_CACHE.clear();
        if (!file.exists()) return;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                long pos = in.readLong();
                int sideId = in.readByte();
                int u = in.readUnsignedByte();
                int v = in.readUnsignedByte();
                int color = in.readInt();

                if (u < GRID_SIZE && v < GRID_SIZE && sideId >= 0 && sideId < 6) {
                    GRAFFITI_CACHE.computeIfAbsent(pos, k -> new EnumMap<>(Direction.class))
                            .computeIfAbsent(Direction.from3DDataValue(sideId), k -> new int[GRID_SIZE][GRID_SIZE])[u][v] = color;
                }
            }
        } catch (IOException e) {
            GraffitiMod.LOGGER.error("Failed to load graffiti cache", e);
        }
    }

    private static void checkAndLoadWorldData() {
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        String current = client.isLocalServer() && client.getSingleplayerServer() != null
                ? client.getSingleplayerServer().getWorldData().getLevelName()
                : "mp_server";
        if (!current.equals(lastWorldName)) {
            lastWorldName = current;
            load();
        }
    }

    private static File getSaveFile() {
        File dir = new File(Minecraft.getInstance().gameDirectory, "graffiti_data/" + lastWorldName);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "cache.bin");
    }

    public static void addPixelToCache(PaintPayload p) {
        GRAFFITI_CACHE.computeIfAbsent(p.pos().asLong(), k -> new EnumMap<>(Direction.class))
                .computeIfAbsent(p.side(), k -> new int[GRID_SIZE][GRID_SIZE])[p.u()][p.v()] = p.color();
        needsSave = true;
    }
}
