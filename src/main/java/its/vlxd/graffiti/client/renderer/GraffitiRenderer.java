package its.vlxd.graffiti.client.renderer;

import its.vlxd.graffiti.GraffitiMod;
import its.vlxd.graffiti.config.GraffitiConfig;
import its.vlxd.graffiti.network.PaintPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GraffitiRenderer {
    private static final int GRID_SIZE = 16;
    private static final float Z_FIGHT_BIAS = 0.005f;
    private static final int SAVE_MAGIC = 0x47724166;
    private static final int SAVE_FORMAT_DIMENSIONS = -2;

    public static final List<PaintPayload> PIXELS = Collections.synchronizedList(new ArrayList<>());
    public static final Map<String, Map<Long, Map<Long, Map<Direction, int[][]>>>> GRAFFITI_CACHE = new ConcurrentHashMap<>();
    public static final Map<String, Map<Long, Map<Long, Map<Direction, List<BakedQuad>>>>> BAKED_CACHE = new ConcurrentHashMap<>();

    private static String lastWorldName = "";
    private static final AtomicBoolean saveInProgress = new AtomicBoolean(false);
    private static boolean hasReceivedServerSync = false;
    private static ResourceLocation WHITE_TEXTURE = ResourceLocation.parse("graffiti:textures/misc/white.png");
    private static Frustum frustum;
    private static boolean debugMode = false;

    public static boolean hasReceivedServerSync() {
        return hasReceivedServerSync;
    }

    public static void markServerSyncReceived() {
        hasReceivedServerSync = true;
    }

    public static void resetServerSyncFlag() {
        hasReceivedServerSync = false;
    }

    public static String currentDim() {
        var level = Minecraft.getInstance().level;
        if (level == null) return "minecraft:overworld";
        return level.dimension().location().toString();
    }

    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
        BAKED_CACHE.clear();
    }

    public static class BakedQuad {
        public final int u, v, w, h, color;
        public final float depth;
        public BakedQuad(int u, int v, int w, int h, int color, float depth) {
            this.u = u; this.v = v; this.w = w; this.h = h; this.color = color; this.depth = depth;
        }
    }

    public static void init() {}

    public static void render(RenderLevelStageEvent event) {
        if (!GraffitiConfig.get().enabled) return;

        var world = Minecraft.getInstance().level;
        if (world == null) return;

        checkAndLoadWorldData();

        synchronized (PIXELS) {
            if (!PIXELS.isEmpty()) {
                for (PaintPayload p : PIXELS) {
                    addPixelToCache(p);
                }
                PIXELS.clear();
            }
        }

        String dim = currentDim();
        var dimCache = GRAFFITI_CACHE.get(dim);
        if (dimCache == null || dimCache.isEmpty()) return;

        var poseStack = event.getPoseStack();
        var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        var buffer = bufferSource.getBuffer(RenderType.entityTranslucent(WHITE_TEXTURE));
        var cameraPos = event.getCamera().getPosition();

        frustum = event.getFrustum();
        double maxDistSq = Math.pow(GraffitiConfig.get().renderDistance, 2);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        int chunkRadius = (GraffitiConfig.get().renderDistance >> 4) + 2;
        ChunkPos cameraChunk = new ChunkPos(BlockPos.containing(cameraPos));

        for (int cx = cameraChunk.x - chunkRadius; cx <= cameraChunk.x + chunkRadius; cx++) {
            for (int cz = cameraChunk.z - chunkRadius; cz <= cameraChunk.z + chunkRadius; cz++) {
                long ck = ChunkPos.asLong(cx, cz);
                var chunk = dimCache.get(ck);
                if (chunk == null || chunk.isEmpty()) continue;

                var blockIter = chunk.entrySet().iterator();
                while (blockIter.hasNext()) {
                    var blockEntry = blockIter.next();
                    long posLong = blockEntry.getKey();
                    BlockPos pos = BlockPos.of(posLong);

                    if (pos.distToCenterSqr(cameraPos) > maxDistSq) continue;

                    if (GraffitiConfig.get().useCulling && frustum != null) {
                        if (!frustum.isVisible(new AABB(pos))) continue;
                    }

                    if (world.getBlockState(pos).isAir()) {
                        blockIter.remove();
                        var bDim = BAKED_CACHE.get(dim);
                        if (bDim != null) {
                            var bChunk = bDim.get(ck);
                            if (bChunk != null) bChunk.remove(posLong);
                        }
                        queueAsyncSave();
                        continue;
                    }

                    var bakedDim = BAKED_CACHE.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
                    var bakedBlockMap = bakedDim.computeIfAbsent(ck, k -> new HashMap<>())
                            .computeIfAbsent(posLong, k -> new EnumMap<>(Direction.class));

                    poseStack.pushPose();
                    poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                    Matrix4f model = poseStack.last().pose();

                    for (var sideEntry : blockEntry.getValue().entrySet()) {
                        Direction side = sideEntry.getKey();

                        int light = LevelRenderer.getLightColor(world, pos.relative(side));

                        List<BakedQuad> bakedQuads = bakedBlockMap.get(side);
                        if (bakedQuads == null) {
                            bakedQuads = bakeFace(world, pos, side, sideEntry.getValue());
                            bakedBlockMap.put(side, bakedQuads);
                        }

                        for (int i = 0; i < bakedQuads.size(); i++) {
                            BakedQuad q = bakedQuads.get(i);
                            drawQuad(model, buffer, side, q.u, q.v, q.w, q.h, q.color, light, q.depth);
                        }
                    }
                    poseStack.popPose();
                }

                if (chunk.isEmpty()) {
                    dimCache.remove(ck);
                    var bDim = BAKED_CACHE.get(dim);
                    if (bDim != null) bDim.remove(ck);
                }
            }
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.entityTranslucent(WHITE_TEXTURE));
    }

    public static void addPixelToCache(PaintPayload p) {
        String dim = currentDim();
        long ck = ChunkPos.asLong(p.pos().getX() >> 4, p.pos().getZ() >> 4);
        long posLong = p.pos().asLong();

        GRAFFITI_CACHE.computeIfAbsent(dim, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(ck, k -> new HashMap<>())
                .computeIfAbsent(posLong, k -> new EnumMap<>(Direction.class))
                .computeIfAbsent(p.side(), k -> new int[GRID_SIZE][GRID_SIZE])[p.u()][p.v()] = p.color();

        invalidateFace(dim, ck, posLong, p.side());
        queueAsyncSave();
    }

    public static void invalidateFace(String dim, long ck, long posLong, Direction side) {
        var dimMap = BAKED_CACHE.get(dim);
        if (dimMap != null) {
            var chunkMap = dimMap.get(ck);
            if (chunkMap != null) {
                var blockMap = chunkMap.get(posLong);
                if (blockMap != null) {
                    blockMap.remove(side);
                }
            }
        }
    }

    private static List<BakedQuad> bakeFace(net.minecraft.world.level.BlockGetter world, BlockPos pos, Direction side, int[][] grid) {
        List<BakedQuad> quads = new ArrayList<>();
        boolean[][] visited = new boolean[GRID_SIZE][GRID_SIZE];
        float[][] depths = new float[GRID_SIZE][GRID_SIZE];

        for (int v = 0; v < GRID_SIZE; v++) {
            for (int u = 0; u < GRID_SIZE; u++) {
                if (grid[u][v] != 0) {
                    depths[u][v] = getPixelDepth(world, pos, side, u, v);
                }
            }
        }

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

                    quads.add(new BakedQuad(u, v, w, h, debugMode ? ((u * 31 + v * 17) * 12345) | 0xFF000000 : color, currentDepth));
                }
            }
        }
        return quads;
    }

    public static void queueAsyncSave() {
        if (lastWorldName.isEmpty()) return;
        if (saveInProgress.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                save();
                saveInProgress.set(false);
            });
        }
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
            out.writeInt(SAVE_MAGIC);
            out.writeInt(SAVE_FORMAT_DIMENSIONS);
            out.writeInt(GRAFFITI_CACHE.size());
            for (var dimEntry : GRAFFITI_CACHE.entrySet()) {
                out.writeUTF(dimEntry.getKey());
                var chunks = dimEntry.getValue();
                out.writeInt(chunks.size());
                for (var chunkEntry : chunks.entrySet()) {
                    out.writeLong(chunkEntry.getKey());
                    var blocks = chunkEntry.getValue();
                    out.writeInt(blocks.size());
                    for (var blockEntry : blocks.entrySet()) {
                        out.writeLong(blockEntry.getKey());
                        var faces = blockEntry.getValue();
                        out.writeInt(faces.size());
                        for (var faceEntry : faces.entrySet()) {
                            out.writeByte(faceEntry.getKey().get3DDataValue());
                            int[][] grid = faceEntry.getValue();
                            int pixelCount = 0;
                            for (int u = 0; u < GRID_SIZE; u++) {
                                for (int v = 0; v < GRID_SIZE; v++) {
                                    if (grid[u][v] != 0) pixelCount++;
                                }
                            }
                            out.writeInt(pixelCount);
                            for (int u = 0; u < GRID_SIZE; u++) {
                                for (int v = 0; v < GRID_SIZE; v++) {
                                    if (grid[u][v] != 0) {
                                        out.writeByte(u);
                                        out.writeByte(v);
                                        out.writeInt(grid[u][v]);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            out.flush();
        } catch (IOException e) {
            GraffitiMod.LOGGER.error("Failed to save graffiti cache", e);
        }
    }

    public static void load() {
        File file = getSaveFile();
        GRAFFITI_CACHE.clear();
        BAKED_CACHE.clear();
        if (!file.exists()) return;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))) {
            int magic = in.readInt();
            if (magic == SAVE_MAGIC) {
                int val = in.readInt();
                if (val == SAVE_FORMAT_DIMENSIONS) {
                    int dimCount = in.readInt();
                    for (int d = 0; d < dimCount; d++) {
                        String dimName = in.readUTF();
                        int chunkCount = in.readInt();
                        var chunks = new HashMap<Long, Map<Long, Map<Direction, int[][]>>>();
                        for (int c = 0; c < chunkCount; c++) {
                            long ck = in.readLong();
                            int blockCount = in.readInt();
                            var blocks = new HashMap<Long, Map<Direction, int[][]>>();
                            for (int b = 0; b < blockCount; b++) {
                                long pos = in.readLong();
                                int faceCount = in.readInt();
                                var faces = new EnumMap<Direction, int[][]>(Direction.class);
                                for (int f = 0; f < faceCount; f++) {
                                    Direction side = Direction.from3DDataValue(in.readByte());
                                    int[][] grid = new int[GRID_SIZE][GRID_SIZE];
                                    int pixelCount = in.readInt();
                                    for (int p = 0; p < pixelCount; p++) {
                                        int u = in.readUnsignedByte();
                                        int v = in.readUnsignedByte();
                                        int color = in.readInt();
                                        if (u < GRID_SIZE && v < GRID_SIZE) grid[u][v] = color;
                                    }
                                    faces.put(side, grid);
                                }
                                blocks.put(pos, faces);
                            }
                            chunks.put(ck, blocks);
                        }
                        GRAFFITI_CACHE.put(dimName, chunks);
                    }
                } else {
                    int chunkCount = val;
                    var chunks = new HashMap<Long, Map<Long, Map<Direction, int[][]>>>();
                    for (int c = 0; c < chunkCount; c++) {
                        long ck = in.readLong();
                        int blockCount = in.readInt();
                        var blocks = new HashMap<Long, Map<Direction, int[][]>>();
                        for (int b = 0; b < blockCount; b++) {
                            long pos = in.readLong();
                            int faceCount = in.readInt();
                            var faces = new EnumMap<Direction, int[][]>(Direction.class);
                            for (int f = 0; f < faceCount; f++) {
                                Direction side = Direction.from3DDataValue(in.readByte());
                                int[][] grid = new int[GRID_SIZE][GRID_SIZE];
                                int pixelCount = in.readInt();
                                for (int p = 0; p < pixelCount; p++) {
                                    int u = in.readUnsignedByte();
                                    int v = in.readUnsignedByte();
                                    int color = in.readInt();
                                    if (u < GRID_SIZE && v < GRID_SIZE) grid[u][v] = color;
                                }
                                faces.put(side, grid);
                            }
                            blocks.put(pos, faces);
                        }
                        chunks.put(ck, blocks);
                    }
                    GRAFFITI_CACHE.put("minecraft:overworld", chunks);
                }
            }
        } catch (IOException e) {
            GraffitiMod.LOGGER.error("Failed to load graffiti cache", e);
        }
    }

    private static void checkAndLoadWorldData() {
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        String current;
        if (client.isLocalServer() && client.getSingleplayerServer() != null) {
            current = client.getSingleplayerServer().getWorldData().getLevelName();
        } else if (client.getConnection() != null && client.getConnection().getServerData() != null) {
            current = client.getConnection().getServerData().ip;
        } else {
            current = "mp_server";
        }
        if (!current.equals(lastWorldName)) {
            lastWorldName = current;
            if (!hasReceivedServerSync) {
                load();
            }
        }
    }

    private static File getSaveFile() {
        File dir = new File(Minecraft.getInstance().gameDirectory, "graffiti_data/" + lastWorldName);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "cache.bin");
    }

    public static Map<Direction, int[][]> getBlockFaces(BlockPos pos) {
        String dim = currentDim();
        var dimCache = GRAFFITI_CACHE.get(dim);
        if (dimCache == null) return null;
        var chunk = dimCache.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        return chunk != null ? chunk.get(pos.asLong()) : null;
    }

    public static void removeBlockFaces(BlockPos pos) {
        String dim = currentDim();
        long ck = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        var dimCache = GRAFFITI_CACHE.get(dim);
        if (dimCache != null) {
            var chunk = dimCache.get(ck);
            if (chunk != null) {
                chunk.remove(pos.asLong());
                if (chunk.isEmpty()) dimCache.remove(ck);

                var bDim = BAKED_CACHE.get(dim);
                if (bDim != null) {
                    var bChunk = bDim.get(ck);
                    if (bChunk != null) {
                        bChunk.remove(pos.asLong());
                        if (bChunk.isEmpty()) bDim.remove(ck);
                    }
                }
                queueAsyncSave();
            }
        }
    }
}
