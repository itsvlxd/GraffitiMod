package its.vlxd.graffiti;

import its.vlxd.graffiti.item.GraffitiItem;
import its.vlxd.graffiti.network.ColorPayload;
import its.vlxd.graffiti.network.Networking;
import its.vlxd.graffiti.network.PaintPayload;
import its.vlxd.graffiti.network.SyncGraffitiPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public static final DeferredHolder<Item, GraffitiItem> GRAFFITI_TOOL =
            ITEMS.register("graffiti_tool", () -> new GraffitiItem(new Item.Properties().stacksTo(1)));

    public static final Map<Long, Map<Long, Map<net.minecraft.core.Direction, int[][]>>> SERVER_CACHE = new HashMap<>();

    public GraffitiMod(IEventBus modBus) {
        TABS.register("graffiti_group", () -> CreativeModeTab.builder()
                .icon(() -> new ItemStack(GRAFFITI_TOOL.get()))
                .title(Component.translatable("itemGroup.graffiti.group"))
                .displayItems((params, output) -> output.accept(GRAFFITI_TOOL.get()))
                .build());

        ITEMS.register(modBus);
        TABS.register(modBus);

        modBus.addListener(Networking::registerPayloadHandlers);
        modBus.addListener(this::addToVanillaTabs);

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(this::onServerStarted);
        bus.addListener(this::onServerStopping);
        bus.addListener(this::onPlayerLogin);
    }

    private void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(GRAFFITI_TOOL.get());
        }
    }

    private void onServerStarted(ServerStartedEvent event) {
        SERVER_CACHE.clear();
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
        if (server == null) return;

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
                PacketDistributor.sendToPlayer((ServerPlayer) player, new SyncGraffitiPayload(syncData));
            }
        });
    }
}
