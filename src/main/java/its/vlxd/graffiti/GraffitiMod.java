package its.vlxd.graffiti;

import its.vlxd.graffiti.item.GraffitiItem;
import its.vlxd.graffiti.item.BrushItem;
import its.vlxd.graffiti.network.CleanPayload;
import its.vlxd.graffiti.network.ColorPayload;
import its.vlxd.graffiti.network.FaceSyncPayload;
import its.vlxd.graffiti.network.Networking;
import its.vlxd.graffiti.network.PaintPayload;
import its.vlxd.graffiti.network.RemoveGraffitiPayload;
import its.vlxd.graffiti.network.SyncGraffitiPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Mod(GraffitiMod.MOD_ID)
public class GraffitiMod {
    public static final String MOD_ID = "graffiti";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int SAVE_MAGIC = 0x47724166;

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);

    public static final DeferredHolder<Item, GraffitiItem> GRAFFITI_TOOL =
            ITEMS.register("graffiti_tool", () -> new GraffitiItem(new Item.Properties().stacksTo(1).durability(12800)));

    public static final DeferredHolder<Item, BrushItem> BRUSH =
            ITEMS.register("brush", () -> new BrushItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, BrushItem> WET_BRUSH =
            ITEMS.register("wet_brush", () -> new BrushItem(new Item.Properties().stacksTo(1).durability(200)));

    public static final DeferredHolder<SoundEvent, SoundEvent> SPRAY_CAN_EQUIP =
            SOUND_EVENTS.register("item.spray_can_equip", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "item.spray_can_equip")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SPRAY_CAN_PAINT =
            SOUND_EVENTS.register("item.spray_can_paint", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "item.spray_can_paint")));

    public static final Map<Long, Map<Long, Map<net.minecraft.core.Direction, int[][]>>> SERVER_CACHE = new HashMap<>();

    public static final Map<Long, Map<Long, Map<net.minecraft.core.Direction, List<int[][]>>>> UNDO_HISTORY = new HashMap<>();
    public static final Map<Long, Map<Long, Map<net.minecraft.core.Direction, Integer>>> LAST_PAINT_TICK = new HashMap<>();
    private static final int IDLE_TICKS = 20;
    private record ClipboardEntry(ResourceLocation blockId, Map<Direction, int[][]> faces) {}
    public static final Map<java.util.UUID, Deque<ClipboardEntry>> PLAYER_CLIPBOARD = new HashMap<>();
    private static final long DESATURATION_INTERVAL = 120_000L; // 5 in-game days
    private long lastDesatTick = 0;
    public static final Map<UUID, Integer> SUBMERGED_BRUSHES = new HashMap<>();

    public GraffitiMod(IEventBus modBus) {
        TABS.register("graffiti_group", () -> CreativeModeTab.builder()
                .icon(() -> new ItemStack(GRAFFITI_TOOL.get()))
                .title(Component.translatable("itemGroup.graffiti.group"))
                .displayItems((params, output) -> {
                    output.accept(GRAFFITI_TOOL.get());
                    output.accept(BRUSH.get());
                    output.accept(WET_BRUSH.get());
                })
                .build());

        ITEMS.register(modBus);
        TABS.register(modBus);
        SOUND_EVENTS.register(modBus);

        modBus.addListener(Networking::registerPayloadHandlers);
        modBus.addListener(this::addToVanillaTabs);

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(this::onServerStarted);
        bus.addListener(this::onServerStopping);
        bus.addListener(this::onPlayerLogin);
        bus.addListener(this::onServerTick);
        bus.addListener(this::onBlockBreak);
        bus.addListener(this::onBlockPlace);
        bus.addListener(this::onEntityJoinLevel);
        bus.addListener(this::onItemCrafted);
        bus.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        its.vlxd.graffiti.command.GraffitiCommands.register(event.getDispatcher());
    }

    private void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(GRAFFITI_TOOL.get());
            event.accept(BRUSH.get());
            event.accept(WET_BRUSH.get());
        }
    }

    private void onServerStarted(ServerStartedEvent event) {
        SERVER_CACHE.clear();
        UNDO_HISTORY.clear();
        LAST_PAINT_TICK.clear();
        File file = new File(event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "graffiti.bin");
        if (!file.exists()) return;

        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))) {
            int magic = in.readInt();
            if (magic == SAVE_MAGIC) {
                int chunkCount = in.readInt();
                for (int c = 0; c < chunkCount; c++) {
                    long ck = in.readLong();
                    int blockCount = in.readInt();
                    var blocks = new HashMap<Long, Map<net.minecraft.core.Direction, int[][]>>();
                    for (int b = 0; b < blockCount; b++) {
                        long pos = in.readLong();
                        int faceCount = in.readInt();
                        var faces = new EnumMap<net.minecraft.core.Direction, int[][]>(net.minecraft.core.Direction.class);
                        for (int f = 0; f < faceCount; f++) {
                            var side = net.minecraft.core.Direction.from3DDataValue(in.readByte());
                            int[][] grid = new int[16][16];
                            int pixelCount = in.readInt();
                            for (int p = 0; p < pixelCount; p++) {
                                int u = in.readUnsignedByte();
                                int v = in.readUnsignedByte();
                                int color = in.readInt();
                                if (u < 16 && v < 16) grid[u][v] = color;
                            }
                            faces.put(side, grid);
                        }
                        blocks.put(pos, faces);
                    }
                    SERVER_CACHE.put(ck, blocks);
                }
            } else {
                int count = magic;
                for (int i = 0; i < count; i++) {
                    long pos = in.readLong();
                    int sideId = in.readByte();
                    int u = in.readUnsignedByte();
                    int v = in.readUnsignedByte();
                    int color = in.readInt();
                    if (u < 16 && v < 16 && sideId >= 0 && sideId < 6) {
                        long ck = ChunkPos.asLong(net.minecraft.core.BlockPos.of(pos).getX() >> 4, net.minecraft.core.BlockPos.of(pos).getZ() >> 4);
                        SERVER_CACHE.computeIfAbsent(ck, k -> new HashMap<>())
                                .computeIfAbsent(pos, k -> new EnumMap<>(net.minecraft.core.Direction.class))
                                .computeIfAbsent(net.minecraft.core.Direction.from3DDataValue(sideId), k -> new int[16][16])[u][v] = color;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load graffiti data", e);
        }

        var level = event.getServer().overworld();
        lastDesatTick = level.getDayTime() - (level.getDayTime() % DESATURATION_INTERVAL);
        LOGGER.info("Desaturation epoch aligned to dayTime={}, next desat at dayTime={}",
                level.getDayTime(), lastDesatTick + DESATURATION_INTERVAL);
    }

    private void onServerStopping(ServerStoppingEvent event) {
        File file = new File(event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "graffiti.bin");
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file))))) {
            out.writeInt(SAVE_MAGIC);
            out.writeInt(SERVER_CACHE.size());
            for (var chunkEntry : SERVER_CACHE.entrySet()) {
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
                        for (int u = 0; u < 16; u++) {
                            for (int v = 0; v < 16; v++) {
                                if (grid[u][v] != 0) pixelCount++;
                            }
                        }
                        out.writeInt(pixelCount);
                        for (int u = 0; u < 16; u++) {
                            for (int v = 0; v < 16; v++) {
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
            out.flush();
        } catch (Exception e) {
            LOGGER.error("Failed to save graffiti data", e);
        }
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        var player = event.getEntity();
        var server = player.getServer();
        if (server == null || !(player instanceof ServerPlayer sp)) return;

        server.execute(() -> {
            List<PaintPayload> syncData = new ArrayList<>();
            
            SERVER_CACHE.forEach((ck, blocks) -> blocks.forEach((posL, faces) -> faces.forEach((side, grid) -> {
                for (int u = 0; u < 16; u++) {
                    for (int v = 0; v < 16; v++) {
                        if (grid[u][v] != 0) {
                            syncData.add(new PaintPayload(net.minecraft.core.BlockPos.of(posL), side, u, v, grid[u][v], 1));
                        }
                    }
                }
            })));

            if (!syncData.isEmpty()) {
                int batchSize = 1000; 
                for (int i = 0; i < syncData.size(); i += batchSize) {
                    List<PaintPayload> subList = syncData.subList(i, Math.min(i + batchSize, syncData.size()));
                    PacketDistributor.sendToPlayer(sp, new SyncGraffitiPayload(new ArrayList<>(subList)));
                }
            }
        });
    }

    public void onServerTick(ServerTickEvent.Post event) {
        int currentTick = event.getServer().getTickCount();

        var ckIter = LAST_PAINT_TICK.entrySet().iterator();
        while (ckIter.hasNext()) {
            var ckEntry = ckIter.next();
            long ck = ckEntry.getKey();
            var blockIter = ckEntry.getValue().entrySet().iterator();
            while (blockIter.hasNext()) {
                var blockEntry = blockIter.next();
                long posL = blockEntry.getKey();
                var faceIter = blockEntry.getValue().entrySet().iterator();
                while (faceIter.hasNext()) {
                    var faceEntry = faceIter.next();
                    if (currentTick - faceEntry.getValue() > IDLE_TICKS) {
                        saveSnapshot(ck, posL, faceEntry.getKey());
                        faceIter.remove();
                    }
                }
                if (blockEntry.getValue().isEmpty()) blockIter.remove();
            }
            if (ckEntry.getValue().isEmpty()) ckIter.remove();
        }

        long dayTime = event.getServer().overworld().getDayTime();
        if (dayTime - lastDesatTick >= DESATURATION_INTERVAL) {
            desaturateAll(event.getServer().overworld());
            lastDesatTick += DESATURATION_INTERVAL;
        }

        if (!SUBMERGED_BRUSHES.isEmpty()) {
            var it = SUBMERGED_BRUSHES.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                Entity entity = null;
                for (ServerLevel sl : event.getServer().getAllLevels()) {
                    entity = sl.getEntity(entry.getKey());
                    if (entity != null) break;
                }
                if (!(entity instanceof ItemEntity ie) || !ie.isAlive()) {
                    it.remove();
                    continue;
                }
                if (!ie.isInWater()) {
                    if (entry.getValue() != -1) it.remove();
                    continue;
                }
                if (entry.getValue() == -1) {
                    entry.setValue(currentTick);
                } else if (currentTick - entry.getValue() >= 40) {
                    ItemStack wet = new ItemStack(WET_BRUSH.get());
                    var srcData = ie.getItem().get(DataComponents.CUSTOM_DATA);
                    if (srcData != null) {
                        wet.set(DataComponents.CUSTOM_DATA, srcData);
                    }
                    ie.setItem(wet);
                    ((ServerLevel) ie.level()).sendParticles(
                            ParticleTypes.BUBBLE, ie.getX(), ie.getY() + 0.5, ie.getZ(),
                            12, 0.3, 0.3, 0.3, 0.05
                    );
                    ((ServerLevel) ie.level()).playSound(
                            null, ie.getX(), ie.getY() + 0.5, ie.getZ(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.5f
                    );
                    it.remove();
                }
            }
        }
    }

    public static void saveSnapshot(long ck, long posL, net.minecraft.core.Direction side) {
        var chunk = SERVER_CACHE.get(ck);
        if (chunk == null) return;
        var faces = chunk.get(posL);
        if (faces == null) return;
        int[][] grid = faces.get(side);
        if (grid == null) return;

        int[][] copy = new int[16][16];
        for (int u = 0; u < 16; u++) {
            System.arraycopy(grid[u], 0, copy[u], 0, 16);
        }

        var history = UNDO_HISTORY
                .computeIfAbsent(ck, k -> new HashMap<>())
                .computeIfAbsent(posL, k -> new HashMap<>())
                .computeIfAbsent(side, k -> new ArrayList<>());

        if (!history.isEmpty()) {
            int[][] last = history.get(history.size() - 1);
            boolean same = true;
            outer: for (int u = 0; u < 16; u++) {
                for (int v = 0; v < 16; v++) {
                    if (last[u][v] != copy[u][v]) { same = false; break outer; }
                }
            }
            if (same) return;
        }

        history.add(copy);
        if (history.size() > 20) history.remove(0);
    }

    public static int[][] handleUndo(long ck, long posL, net.minecraft.core.Direction side) {
        return handleUndoRedo(ck, posL, side, false);
    }

    public static int[][] handleRedo(long ck, long posL, net.minecraft.core.Direction side) {
        return handleUndoRedo(ck, posL, side, true);
    }

    private static int[][] handleUndoRedo(long ck, long posL, net.minecraft.core.Direction side, boolean redo) {
        var chunk = UNDO_HISTORY.get(ck);
        if (chunk == null) return null;
        var faces = chunk.get(posL);
        if (faces == null) return null;
        var history = faces.get(side);
        if (history == null || history.isEmpty()) return null;

        var currentChunk = SERVER_CACHE.get(ck);
        var currentFaces = currentChunk != null ? currentChunk.get(posL) : null;
        int[][] currentGrid = currentFaces != null ? currentFaces.get(side) : null;
        if (currentGrid == null) {
            currentGrid = new int[16][16];
            if (currentFaces == null) {
                currentFaces = new EnumMap<>(net.minecraft.core.Direction.class);
                currentChunk.put(posL, currentFaces);
            }
            currentFaces.put(side, currentGrid);
        }

        int currentIdx = -1;
        for (int i = 0; i < history.size(); i++) {
            boolean match = true;
            int[][] hGrid = history.get(i);
            for (int u = 0; u < 16 && match; u++) {
                for (int v = 0; v < 16 && match; v++) {
                    if (hGrid[u][v] != currentGrid[u][v]) match = false;
                }
            }
            if (match) {
                currentIdx = i;
                break;
            }
        }

        int targetIdx;
        if (redo) {
            if (currentIdx >= history.size() - 1) return null;
            targetIdx = currentIdx + 1;
        } else {
            if (currentIdx <= 0) return null;
            targetIdx = currentIdx - 1;
        }

        int[][] target = history.get(targetIdx);
        for (int u = 0; u < 16; u++) {
            System.arraycopy(target[u], 0, currentGrid[u], 0, 16);
        }

        // Discard future history if user paints later (handled in paint handler)
        return currentGrid;
    }

    public static void broadcastFace(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side, int[][] grid, ServerLevel level) {
        int[] flat = new int[256];
        for (int u = 0; u < 16; u++) {
            for (int v = 0; v < 16; v++) {
                flat[u * 16 + v] = grid[u][v];
            }
        }
        var payload = new FaceSyncPayload(pos, side, flat);
        for (var player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void recordPaintTick(long ck, long posL, net.minecraft.core.Direction side, int tick) {
        LAST_PAINT_TICK.computeIfAbsent(ck, k -> new HashMap<>())
                .computeIfAbsent(posL, k -> new EnumMap<>(net.minecraft.core.Direction.class))
                .put(side, tick);
    }

    private void desaturateAll(ServerLevel level) {
        List<PaintPayload> syncData = new ArrayList<>();
        var rng = new Random();

        SERVER_CACHE.forEach((ck, blocks) -> blocks.forEach((posL, faces) -> faces.forEach((side, grid) -> {
            for (int u = 0; u < 16; u++) {
                for (int v = 0; v < 16; v++) {
                    int color = grid[u][v];
                    if (color == 0) continue;

                    int a = (color >> 24) & 0xFF;
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = color & 0xFF;

                    float[] hsb = new float[3];
                    Color.RGBtoHSB(r, g, b, hsb);
                    float reduction = 0.02f + rng.nextFloat() * 0.13f;
                    float floor = Math.min(hsb[1], 0.85f);
                    hsb[1] *= (1.0f - reduction);
                    if (hsb[1] < floor) hsb[1] = floor;
                    int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);

                    int newColor = (a << 24) | (rgb & 0xFFFFFF);
                    grid[u][v] = newColor;
                    syncData.add(new PaintPayload(BlockPos.of(posL), side, u, v, newColor, 1));
                }
            }
        })));

        if (syncData.isEmpty()) return;

        var payload = new SyncGraffitiPayload(syncData);
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        LOGGER.info("Desaturated {} graffiti pixels (30 in-game days)", syncData.size());
    }

    private static final Direction[] HORIZONTAL_CYCLE = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private static Direction mapHorizontalFace(Direction dir, int rotations) {
        if (dir == Direction.UP || dir == Direction.DOWN) return dir;
        for (int i = 0; i < 4; i++) {
            if (HORIZONTAL_CYCLE[i] == dir) {
                return HORIZONTAL_CYCLE[(i + rotations % 4 + 4) % 4];
            }
        }
        return dir;
    }

    private static Direction getHitFace(BlockPos pos, net.minecraft.world.entity.player.Player player) {
        Vec3 delta = player.position().subtract(Vec3.atCenterOf(pos));
        double absX = Math.abs(delta.x);
        double absY = Math.abs(delta.y);
        double absZ = Math.abs(delta.z);

        if (absX >= absY && absX >= absZ) {
            return delta.x > 0 ? Direction.WEST : Direction.EAST;
        }
        if (absY >= absZ) {
            return delta.y > 0 ? Direction.DOWN : Direction.UP;
        }
        return delta.z > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    public static int[][] rotateGrid(int[][] grid, int rotations) {
        if (rotations == 0 || grid == null) return grid;
        int[][] result = new int[16][16];
        for (int u = 0; u < 16; u++) {
            for (int v = 0; v < 16; v++) {
                int val = grid[u][v];
                if (val == 0) continue;
                int nu, nv;
                switch ((rotations % 4 + 4) % 4) {
                    case 1 -> { nu = 15 - v; nv = u; }
                    case 2 -> { nu = 15 - u; nv = 15 - v; }
                    case 3 -> { nu = v; nv = 15 - u; }
                    default -> { nu = u; nv = v; }
                }
                result[nu][nv] = val;
            }
        }
        return result;
    }

    private void onBlockBreak(BlockEvent.BreakEvent event) {
        var player = event.getPlayer();
        if (player == null) return;
        BlockPos pos = event.getPos();

        long ck = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        long posL = pos.asLong();

        var chunk = SERVER_CACHE.get(ck);
        if (chunk == null) return;
        var faces = chunk.get(posL);
        if (faces == null || faces.isEmpty()) return;

        Direction hitFace = getHitFace(pos, player);
        int normRot = 0;
        if (hitFace != Direction.UP && hitFace != Direction.DOWN) {
            for (int i = 0; i < 4; i++) {
                if (HORIZONTAL_CYCLE[i] == hitFace) {
                    normRot = (4 - i) % 4;
                    break;
                }
            }
        }

        Map<Direction, int[][]> clipboard = new EnumMap<>(Direction.class);
        for (var entry : faces.entrySet()) {
            Direction normalizedFace = mapHorizontalFace(entry.getKey(), normRot);
            int[][] copy = new int[16][16];
            for (int u = 0; u < 16; u++) {
                System.arraycopy(entry.getValue()[u], 0, copy[u], 0, 16);
            }
            clipboard.put(normalizedFace, copy);
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
        PLAYER_CLIPBOARD.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>()).addLast(new ClipboardEntry(blockId, clipboard));

        chunk.remove(posL);
        if (chunk.isEmpty()) SERVER_CACHE.remove(ck);

        UNDO_HISTORY.remove(ck);

        ServerLevel level = player.getServer() != null
                ? player.getServer().getLevel(player.level().dimension()) : null;
        if (level != null) {
            var removal = new RemoveGraffitiPayload(pos);
            for (var p : level.players()) {
                PacketDistributor.sendToPlayer(p, removal);
            }
        }
    }

    private void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        var entity = event.getEntity();
        if (!(entity instanceof net.minecraft.world.entity.player.Player player)) return;

        var deque = PLAYER_CLIPBOARD.get(player.getUUID());
        if (deque == null || deque.isEmpty()) return;

        BlockPos pos = event.getPos();
        ResourceLocation placedId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());

        ClipboardEntry match = null;
        var it = deque.iterator();
        while (it.hasNext()) {
            ClipboardEntry e = it.next();
            if (e.blockId().equals(placedId)) {
                match = e;
                it.remove();
                break;
            }
        }
        if (match == null) return;
        if (deque.isEmpty()) PLAYER_CLIPBOARD.remove(player.getUUID());

        var clipboard = match.faces();
        if (clipboard == null || clipboard.isEmpty()) return;
        Direction facing = player.getDirection();

        int rotations = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };

        long ck = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        long posL = pos.asLong();

        var targetFaces = SERVER_CACHE.computeIfAbsent(ck, k -> new HashMap<>())
                .computeIfAbsent(posL, k -> new EnumMap<>(Direction.class));

        var level = player.getServer() != null
                ? player.getServer().getLevel(player.level().dimension()) : null;

        for (var entry : clipboard.entrySet()) {
            Direction newFace = mapHorizontalFace(entry.getKey(), rotations);
            targetFaces.put(newFace, entry.getValue());

            if (level != null) {
                broadcastFace(pos, newFace, entry.getValue(), level);
            }
        }
    }

    private void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity ie && ie.getItem().is(BRUSH.get())) {
            SUBMERGED_BRUSHES.put(ie.getUUID(), -1);
        }
    }

    private void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting();
        if (!result.is(GRAFFITI_TOOL.get())) return;

        // Copy settings from input can
        for (int i = 0; i < event.getInventory().getContainerSize(); i++) {
            ItemStack input = event.getInventory().getItem(i);
            if (input.is(GRAFFITI_TOOL.get())) {
                int inputColor = GraffitiItem.getColor(input);
                int inputSize = GraffitiItem.getBrushSize(input);
                int inputShape = GraffitiItem.getBrushShape(input);
                boolean inputLocked = GraffitiItem.isColorLocked(input);
                ItemStack finalResult = event.getCrafting();

                // Apply to result immediately
                GraffitiItem.setColor(finalResult, inputColor);
                GraffitiItem.setBrushSize(finalResult, inputSize);
                GraffitiItem.setBrushShape(finalResult, inputShape);
                if (inputLocked) GraffitiItem.setColorLocked(finalResult, true);
                finalResult.setDamageValue(0);

                // Also schedule an inventory update to ensure it sticks
                var player = event.getEntity();
                if (player instanceof ServerPlayer sp) {
                    sp.getServer().execute(() -> {
                        for (int s = 0; s < sp.getInventory().getContainerSize(); s++) {
                            ItemStack inv = sp.getInventory().getItem(s);
                            if (inv.is(GRAFFITI_TOOL.get()) && inv.getDamageValue() == 0 && inv.getCount() == 1) {
                                GraffitiItem.setColor(inv, inputColor);
                                GraffitiItem.setBrushSize(inv, inputSize);
                                GraffitiItem.setBrushShape(inv, inputShape);
                                if (inputLocked) GraffitiItem.setColorLocked(inv, true);
                                inv.setDamageValue(0);
                                break;
                            }
                        }
                    });
                }
                break;
            }
        }
    }

    public static void discardFutureHistory(long ck, long posL, net.minecraft.core.Direction side) {
        var chunk = UNDO_HISTORY.get(ck);
        if (chunk == null) return;
        var faces = chunk.get(posL);
        if (faces == null) return;
        var history = faces.get(side);
        if (history == null) return;

        if (history.size() <= 1) return;

        int[][] current = null;
        var sc = SERVER_CACHE.get(ck);
        if (sc != null) {
            var fc = sc.get(posL);
            if (fc != null) current = fc.get(side);
        }
        if (current == null) return;

        for (int i = 0; i < history.size(); i++) {
            boolean match = true;
            int[][] h = history.get(i);
            for (int u = 0; u < 16 && match; u++) {
                for (int v = 0; v < 16 && match; v++) {
                    if (h[u][v] != current[u][v]) match = false;
                }
            }
            if (match) {
                history.subList(i + 1, history.size()).clear();
                return;
            }
        }
    }
}
