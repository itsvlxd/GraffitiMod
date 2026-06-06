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
import its.vlxd.graffiti.network.GalleryListPayload;
import its.vlxd.graffiti.network.GallerySavePayload;
import its.vlxd.graffiti.network.GalleryPastePayload;
import its.vlxd.graffiti.gallery.SavedDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
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
    public static final String PREFIX = "§6§lGraffitiMod §8§l┃ §7";
    private static final int SAVE_MAGIC = 0x47724166;
    private static final int SAVE_FORMAT_DIMENSIONS = -2;

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

    public static final Map<String, Map<Long, Map<Long, Map<Direction, int[][]>>>> SERVER_CACHE = new HashMap<>();

    public static final Map<String, Map<Long, Map<Long, Map<Direction, List<int[][]>>>>> UNDO_HISTORY = new HashMap<>();
    public static final Map<String, Map<Long, Map<Long, Map<Direction, Integer>>>> LAST_PAINT_TICK = new HashMap<>();
    private static final int IDLE_TICKS = 20;
    private static final int GALLERY_MAGIC = 0x47616C6C;
    public static final Map<UUID, List<SavedDesign>> GALLERY = new HashMap<>();
    private record ClipboardEntry(ResourceLocation blockId, Map<Direction, int[][]> faces) {}
    public static final Map<java.util.UUID, Deque<ClipboardEntry>> PLAYER_CLIPBOARD = new HashMap<>();
    private static final long DESATURATION_INTERVAL = 120_000L;
    private long lastDesatTick = 0;
    public static final Map<UUID, Integer> SUBMERGED_BRUSHES = new HashMap<>();

    public static String dimKey(ResourceKey<Level> dim) {
        return dim.location().toString();
    }

    public static String dimKey(Level level) {
        return level.dimension().location().toString();
    }

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
        if (file.exists()) {
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
                                        var side = Direction.from3DDataValue(in.readByte());
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
                                chunks.put(ck, blocks);
                            }
                            SERVER_CACHE.put(dimName, chunks);
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
                                    var side = Direction.from3DDataValue(in.readByte());
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
                            chunks.put(ck, blocks);
                        }
                        SERVER_CACHE.put("minecraft:overworld", chunks);
                    }
                } else {
                    int count = magic;
                    var chunks = new HashMap<Long, Map<Long, Map<Direction, int[][]>>>();
                    for (int i = 0; i < count; i++) {
                        long pos = in.readLong();
                        int sideId = in.readByte();
                        int u = in.readUnsignedByte();
                        int v = in.readUnsignedByte();
                        int color = in.readInt();
                        if (u < 16 && v < 16 && sideId >= 0 && sideId < 6) {
                            long ck = ChunkPos.asLong(BlockPos.of(pos).getX() >> 4, BlockPos.of(pos).getZ() >> 4);
                            chunks.computeIfAbsent(ck, k -> new HashMap<>())
                                    .computeIfAbsent(pos, k -> new EnumMap<>(Direction.class))
                                    .computeIfAbsent(Direction.from3DDataValue(sideId), k -> new int[16][16])[u][v] = color;
                        }
                    }
                    if (!chunks.isEmpty()) SERVER_CACHE.put("minecraft:overworld", chunks);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load graffiti data", e);
            }
        }

        var level = event.getServer().overworld();
        lastDesatTick = level.getDayTime() - (level.getDayTime() % DESATURATION_INTERVAL);
        LOGGER.info("Desaturation epoch aligned to dayTime={}, next desat at dayTime={}",
                level.getDayTime(), lastDesatTick + DESATURATION_INTERVAL);

        GALLERY.clear();
        File galFile = new File(event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "gallery.bin");
        if (galFile.exists()) {
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(galFile))))) {
                int magic = in.readInt();
                if (magic == GALLERY_MAGIC) {
                    int designCount = in.readInt();
                    for (int i = 0; i < designCount; i++) {
                        SavedDesign design = SavedDesign.read(in);
                        GALLERY.computeIfAbsent(design.authorId(), k -> new ArrayList<>()).add(design);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load gallery data", e);
            }
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        File file = new File(event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "graffiti.bin");
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file))))) {
            out.writeInt(SAVE_MAGIC);
            out.writeInt(SAVE_FORMAT_DIMENSIONS);
            out.writeInt(SERVER_CACHE.size());
            for (var dimEntry : SERVER_CACHE.entrySet()) {
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
            }
            out.flush();
        } catch (Exception e) {
            LOGGER.error("Failed to save graffiti data", e);
        }

        File galFile = new File(event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "gallery.bin");
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(galFile))))) {
            out.writeInt(GALLERY_MAGIC);
            int totalDesigns = 0;
            for (var list : GALLERY.values()) totalDesigns += list.size();
            out.writeInt(totalDesigns);
            for (var list : GALLERY.values()) {
                for (var design : list) {
                    design.write(out);
                }
            }
            out.flush();
        } catch (Exception e) {
            LOGGER.error("Failed to save gallery data", e);
        }
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        var player = event.getEntity();
        var server = player.getServer();
        if (server == null || !(player instanceof ServerPlayer sp)) return;

        server.execute(() -> {
            List<PaintPayload> syncData = new ArrayList<>();

            for (var dimEntry : SERVER_CACHE.entrySet()) {
                String dim = dimEntry.getKey();
                for (var ckEntry : dimEntry.getValue().entrySet()) {
                    long ck = ckEntry.getKey();
                    for (var posLEntry : ckEntry.getValue().entrySet()) {
                        long posL = posLEntry.getKey();
                        for (var faceEntry : posLEntry.getValue().entrySet()) {
                            Direction side = faceEntry.getKey();
                            int[][] grid = faceEntry.getValue();
                            for (int u = 0; u < 16; u++) {
                                for (int v = 0; v < 16; v++) {
                                    if (grid[u][v] != 0) {
                                        syncData.add(new PaintPayload(BlockPos.of(posL), side, u, v, grid[u][v], 1));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!syncData.isEmpty()) {
                PacketDistributor.sendToPlayer(sp, new SyncGraffitiPayload(syncData));
            }

            syncGalleryToPlayer(sp);
        });
    }

    public void onServerTick(ServerTickEvent.Post event) {
        int currentTick = event.getServer().getTickCount();

        var dimIter = LAST_PAINT_TICK.entrySet().iterator();
        while (dimIter.hasNext()) {
            var dimEntry = dimIter.next();
            var ckIter = dimEntry.getValue().entrySet().iterator();
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
                            saveSnapshot(dimEntry.getKey(), ck, posL, faceEntry.getKey());
                            faceIter.remove();
                        }
                    }
                    if (blockEntry.getValue().isEmpty()) blockIter.remove();
                }
                if (ckEntry.getValue().isEmpty()) ckIter.remove();
            }
            if (dimEntry.getValue().isEmpty()) dimIter.remove();
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

    public static void saveSnapshot(String dim, long ck, long posL, Direction side) {
        var dimCache = SERVER_CACHE.get(dim);
        if (dimCache == null) return;
        var chunk = dimCache.get(ck);
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
                .computeIfAbsent(dim, k -> new HashMap<>())
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

    public static int[][] handleUndo(String dim, long ck, long posL, Direction side) {
        return handleUndoRedo(dim, ck, posL, side, false);
    }

    public static int[][] handleRedo(String dim, long ck, long posL, Direction side) {
        return handleUndoRedo(dim, ck, posL, side, true);
    }

    private static int[][] handleUndoRedo(String dim, long ck, long posL, Direction side, boolean redo) {
        var dimHist = UNDO_HISTORY.get(dim);
        if (dimHist == null) return null;
        var chunk = dimHist.get(ck);
        if (chunk == null) return null;
        var faces = chunk.get(posL);
        if (faces == null) return null;
        var history = faces.get(side);
        if (history == null || history.isEmpty()) return null;

        var dimCache = SERVER_CACHE.computeIfAbsent(dim, k -> new HashMap<>());
        var currentChunk = dimCache.computeIfAbsent(ck, k -> new HashMap<>());
        var currentFaces = currentChunk.computeIfAbsent(posL, k -> new EnumMap<>(Direction.class));
        int[][] currentGrid = currentFaces.get(side);
        if (currentGrid == null) {
            currentGrid = new int[16][16];
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

        return currentGrid;
    }

    public static void broadcastFace(BlockPos pos, Direction side, int[][] grid, ServerLevel level) {
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

    public static void recordPaintTick(String dim, long ck, long posL, Direction side, int tick) {
        LAST_PAINT_TICK.computeIfAbsent(dim, k -> new HashMap<>())
                .computeIfAbsent(ck, k -> new HashMap<>())
                .computeIfAbsent(posL, k -> new EnumMap<>(Direction.class))
                .put(side, tick);
    }

    public static void syncGalleryToPlayer(ServerPlayer sp) {
        List<SavedDesign> playerDesigns = GALLERY.getOrDefault(sp.getUUID(), List.of());
        PacketDistributor.sendToPlayer(sp, new GalleryListPayload(playerDesigns));
    }

    public static void handleGallerySave(ServerPlayer player, GallerySavePayload payload) {
        var server = player.getServer();
        if (server == null) return;

        String dim = dimKey(player.level());
        ItemStack held = player.getMainHandItem();
        if (!held.is(GRAFFITI_TOOL.get())) {
            player.sendSystemMessage(Component.literal(PREFIX)
                .append(Component.literal("Must hold a graffiti can to save.").withStyle(ChatFormatting.GRAY)));
            return;
        }

        BlockPos min = BlockPos.min(payload.pos1(), payload.pos2());
        BlockPos max = BlockPos.max(payload.pos1(), payload.pos2());
        Map<BlockPos, Map<Direction, int[][]>> blocks = new LinkedHashMap<>();
        int pixelCount = 0;

        var dimCache = SERVER_CACHE.get(dim);
        if (dimCache == null) {
            player.sendSystemMessage(Component.literal(PREFIX)
                .append(Component.literal("Nothing to save here.").withStyle(ChatFormatting.GRAY)));
            return;
        }

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    long ck = ChunkPos.asLong(bp.getX() >> 4, bp.getZ() >> 4);
                    long posL = bp.asLong();
                    var chunk = dimCache.get(ck);
                    if (chunk == null) continue;
                    var faces = chunk.get(posL);
                    if (faces == null || faces.isEmpty()) continue;

                    Map<Direction, int[][]> copiedFaces = new EnumMap<>(Direction.class);
                    for (var faceEntry : faces.entrySet()) {
                        Direction side = faceEntry.getKey();
                        int[][] grid = faceEntry.getValue();
                        int[][] copy = new int[16][16];
                        for (int u = 0; u < 16; u++) {
                            System.arraycopy(grid[u], 0, copy[u], 0, 16);
                        }
                        copiedFaces.put(side, copy);
                        for (int u = 0; u < 16; u++) {
                            for (int v = 0; v < 16; v++) {
                                if (copy[u][v] != 0) pixelCount++;
                            }
                        }
                    }
                    blocks.put(bp.subtract(min), copiedFaces);
                }
            }
        }

        if (blocks.isEmpty()) {
            player.sendSystemMessage(Component.literal(PREFIX)
                .append(Component.literal("Nothing to save here.").withStyle(ChatFormatting.GRAY)));
            return;
        }

        int remaining = held.getMaxDamage() - held.getDamageValue();
        if (pixelCount > remaining) {
            player.sendSystemMessage(Component.literal(PREFIX)
                .append(Component.literal("Not enough paint! Need ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(pixelCount)).withStyle(ChatFormatting.RED))
                .append(Component.literal(" durability, have ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(remaining)).withStyle(ChatFormatting.RED))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
            return;
        }

        held.setDamageValue(held.getDamageValue() + pixelCount);

        SavedDesign design = new SavedDesign(
                UUID.randomUUID(),
                payload.name(),
                player.getUUID(),
                System.currentTimeMillis(),
                0,
                player.getDirection(),
                blocks
        );

        GALLERY.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(design);

        player.sendSystemMessage(Component.literal(PREFIX)
            .append(Component.literal("Saved ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(payload.name()).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(" (" + pixelCount + " pixels)").withStyle(ChatFormatting.GRAY)));
        syncGalleryToPlayer(player);
        server.execute(() -> saveGallery(server));
    }

    public static void handleGalleryPaste(ServerPlayer player, GalleryPastePayload payload) {
        var server = player.getServer();
        if (server == null) return;

        List<SavedDesign> playerDesigns = GALLERY.get(player.getUUID());
        if (playerDesigns == null) {
            player.sendSystemMessage(Component.literal(PREFIX)
                .append(Component.literal("No designs found.").withStyle(ChatFormatting.GRAY)));
            return;
        }

        SavedDesign design = null;
        for (SavedDesign d : playerDesigns) {
            if (d.id().equals(payload.designId())) {
                design = d;
                break;
            }
        }
        if (design == null) {
            player.sendSystemMessage(Component.literal(PREFIX)
                .append(Component.literal("Design not found.").withStyle(ChatFormatting.GRAY)));
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (!held.is(GRAFFITI_TOOL.get())) {
            player.sendSystemMessage(Component.literal(PREFIX)
                .append(Component.literal("Must hold a graffiti can to paste.").withStyle(ChatFormatting.GRAY)));
            return;
        }

        int pixelCount = design.pixelCount();
        int remaining = held.getMaxDamage() - held.getDamageValue();
        if (pixelCount > remaining) {
            player.sendSystemMessage(Component.literal(PREFIX)
                .append(Component.literal("Not enough paint! Need ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(pixelCount)).withStyle(ChatFormatting.RED))
                .append(Component.literal(" durability, have ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(remaining)).withStyle(ChatFormatting.RED))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
            return;
        }

        held.setDamageValue(held.getDamageValue() + pixelCount);

        String dim = dimKey(player.level());
        var dimCache = SERVER_CACHE.computeIfAbsent(dim, k -> new HashMap<>());
        var level = server.getLevel(player.level().dimension());
        if (level == null) return;

        BlockPos target = payload.targetPos();

        int rotations = getRotations(design.facing(), player.getDirection());

        int pasted = 0;

        for (var blockEntry : design.blocks().entrySet()) {
            BlockPos relative = blockEntry.getKey();
            BlockPos rotatedRel = rotatePos(relative, rotations);
            BlockPos worldPos = target.offset(rotatedRel);
            if (!level.getBlockState(worldPos).isAir()) {
                long ck = ChunkPos.asLong(worldPos.getX() >> 4, worldPos.getZ() >> 4);
                long posL = worldPos.asLong();
                var targetFaces = dimCache.computeIfAbsent(ck, k -> new HashMap<>())
                        .computeIfAbsent(posL, k -> new EnumMap<>(Direction.class));

                for (var faceEntry : blockEntry.getValue().entrySet()) {
                    Direction side = faceEntry.getKey();
                    Direction rotatedSide = mapHorizontalFace(side, rotations);
                    int[][] grid = faceEntry.getValue();
                    boolean axisChanged = side.getAxis() != rotatedSide.getAxis();
                    boolean oppositeFaces = rotations % 4 == 2;
                    if ((axisChanged || oppositeFaces) && side != Direction.UP && side != Direction.DOWN)
                        grid = flipHorizontal(grid);
                    targetFaces.put(rotatedSide, grid);
                    broadcastFace(worldPos, rotatedSide, grid, level);
                    pasted++;
                }
            }
        }

        player.sendSystemMessage(Component.literal(PREFIX)
            .append(Component.literal("Pasted ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(design.name()).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(" (" + pasted + " faces)").withStyle(ChatFormatting.GRAY)));
    }

    public static BlockPos rotatePos(BlockPos pos, int rotations) {
        if (rotations == 0) return pos;
        return switch ((rotations % 4 + 4) % 4) {
            case 1 -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case 2 -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case 3 -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
            default -> pos;
        };
    }

    public static void handleGalleryDelete(ServerPlayer player, UUID designId) {
        List<SavedDesign> playerDesigns = GALLERY.get(player.getUUID());
        if (playerDesigns == null) return;
        playerDesigns.removeIf(d -> d.id().equals(designId));
        if (playerDesigns.isEmpty()) GALLERY.remove(player.getUUID());
        player.sendSystemMessage(Component.literal(PREFIX)
            .append(Component.literal("Design deleted.").withStyle(ChatFormatting.GRAY)));
        syncGalleryToPlayer(player);
        var server = player.getServer();
        if (server != null) server.execute(() -> saveGallery(server));
    }

    private static void saveGallery(net.minecraft.server.MinecraftServer server) {
        File galFile = new File(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "gallery.bin");
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(galFile))))) {
            out.writeInt(GALLERY_MAGIC);
            int totalDesigns = 0;
            for (var list : GALLERY.values()) totalDesigns += list.size();
            out.writeInt(totalDesigns);
            for (var list : GALLERY.values()) {
                for (var design : list) {
                    design.write(out);
                }
            }
            out.flush();
        } catch (Exception e) {
            LOGGER.error("Failed to save gallery data", e);
        }
    }

    private void desaturateAll(ServerLevel level) {
        String dim = dimKey(level);
        var dimCache = SERVER_CACHE.get(dim);
        if (dimCache == null || dimCache.isEmpty()) return;

        List<PaintPayload> syncData = new ArrayList<>();
        var rng = new Random();

        dimCache.forEach((ck, blocks) -> blocks.forEach((posL, faces) -> faces.forEach((side, grid) -> {
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
        LOGGER.info("Desaturated {} graffiti pixels in {}", syncData.size(), dim);
    }

    public static int getRotations(Direction from, Direction to) {
        for (int i = 0; i < HORIZONTAL_CYCLE.length; i++) {
            if (HORIZONTAL_CYCLE[i] == to) {
                for (int j = 0; j < HORIZONTAL_CYCLE.length; j++) {
                    if (HORIZONTAL_CYCLE[j] == from) {
                        return (i - j + 4) % 4;
                    }
                }
            }
        }
        return 0;
    }

    public static final Direction[] HORIZONTAL_CYCLE = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    public static Direction mapHorizontalFace(Direction dir, int rotations) {
        if (dir == Direction.UP || dir == Direction.DOWN) return dir;
        for (int i = 0; i < 4; i++) {
            if (HORIZONTAL_CYCLE[i] == dir) {
                return HORIZONTAL_CYCLE[(i + rotations % 4 + 4) % 4];
            }
        }
        return dir;
    }

    private static Direction getHitFace(BlockPos pos, Player player) {
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

    public static int[][] flipHorizontal(int[][] grid) {
        int[][] result = new int[16][16];
        for (int u = 0; u < 16; u++) {
            for (int v = 0; v < 16; v++) {
                result[15 - u][v] = grid[u][v];
            }
        }
        return result;
    }

    private void onBlockBreak(BlockEvent.BreakEvent event) {
        var player = event.getPlayer();
        if (player == null) return;
        BlockPos pos = event.getPos();

        String dim = dimKey(player.level());
        long ck = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        long posL = pos.asLong();

        var dimCache = SERVER_CACHE.get(dim);
        if (dimCache == null) return;
        var chunk = dimCache.get(ck);
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
        if (chunk.isEmpty()) dimCache.remove(ck);

        var dimUndo = UNDO_HISTORY.get(dim);
        if (dimUndo != null) {
            dimUndo.remove(ck);
            if (dimUndo.isEmpty()) UNDO_HISTORY.remove(dim);
        }

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
        if (!(entity instanceof Player player)) return;

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

        String dim = dimKey(player.level());
        long ck = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        long posL = pos.asLong();

        var dimCache = SERVER_CACHE.computeIfAbsent(dim, k -> new HashMap<>());
        var targetFaces = dimCache.computeIfAbsent(ck, k -> new HashMap<>())
                .computeIfAbsent(posL, k -> new EnumMap<>(Direction.class));

        var level = player.getServer() != null
                ? player.getServer().getLevel(player.level().dimension()) : null;

        for (var clipEntry : clipboard.entrySet()) {
            Direction oldFace = clipEntry.getKey();
            Direction newFace = mapHorizontalFace(oldFace, rotations);
            int[][] grid = clipEntry.getValue();
            boolean axisChanged = oldFace.getAxis() != newFace.getAxis();
            boolean oppositeFaces = rotations % 4 == 2;
            if ((axisChanged || oppositeFaces) && oldFace != Direction.UP && oldFace != Direction.DOWN)
                grid = flipHorizontal(grid);
            targetFaces.put(newFace, grid);

            if (level != null) {
                broadcastFace(pos, newFace, grid, level);
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

        for (int i = 0; i < event.getInventory().getContainerSize(); i++) {
            ItemStack input = event.getInventory().getItem(i);
            if (input.is(GRAFFITI_TOOL.get())) {
                int inputColor = GraffitiItem.getColor(input);
                int inputSize = GraffitiItem.getBrushSize(input);
                int inputShape = GraffitiItem.getBrushShape(input);
                boolean inputLocked = GraffitiItem.isColorLocked(input);
                ItemStack finalResult = event.getCrafting();

                GraffitiItem.setColor(finalResult, inputColor);
                GraffitiItem.setBrushSize(finalResult, inputSize);
                GraffitiItem.setBrushShape(finalResult, inputShape);
                if (inputLocked) GraffitiItem.setColorLocked(finalResult, true);
                finalResult.setDamageValue(0);

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

    public static void discardFutureHistory(String dim, long ck, long posL, Direction side) {
        var dimHist = UNDO_HISTORY.get(dim);
        if (dimHist == null) return;
        var chunk = dimHist.get(ck);
        if (chunk == null) return;
        var faces = chunk.get(posL);
        if (faces == null) return;
        var history = faces.get(side);
        if (history == null) return;

        if (history.size() <= 1) return;

        int[][] current = null;
        var dimCache = SERVER_CACHE.get(dim);
        if (dimCache != null) {
            var sc = dimCache.get(ck);
            if (sc != null) {
                var fc = sc.get(posL);
                if (fc != null) current = fc.get(side);
            }
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
