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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
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

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final Item GRAFFITI_TOOL = new GraffitiItem(new Item.Properties().stacksTo(1));

    public static final Map<Long, Map<net.minecraft.core.Direction, int[][]>> SERVER_CACHE = new HashMap<>();

    public GraffitiMod(IEventBus modBus) {
        ITEMS.register("graffiti_tool", () -> GRAFFITI_TOOL);
        TABS.register("graffiti_group", () -> CreativeModeTab.builder()
                .icon(() -> new ItemStack(GRAFFITI_TOOL))
                .title(Component.translatable("itemGroup.graffiti.group"))
                .displayItems((params, output) -> output.accept(GRAFFITI_TOOL))
                .build());

        ITEMS.register(modBus);
        TABS.register(modBus);

        modBus.addListener(Networking::registerPayloadHandlers);

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(this::onServerStarted);
        bus.addListener(this::onServerStopping);
        bus.addListener(this::onPlayerLogin);
    }

    private void onServerStarted(ServerStartedEvent event) {
        SERVER_CACHE.clear();
        File file = new File(event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "graffiti.bin");
        if (!file.exists()) return;

        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                long pos = in.readLong();
                int sideId = in.readByte();
                int u = in.readUnsignedByte();
                int v = in.readUnsignedByte();
                int color = in.readInt();

                if (u >= 0 && u < 16 && v >= 0 && v < 16 && sideId >= 0 && sideId < 6) {
                    SERVER_CACHE.computeIfAbsent(pos, k -> new EnumMap<>(net.minecraft.core.Direction.class))
                            .computeIfAbsent(net.minecraft.core.Direction.from3DDataValue(sideId), k -> new int[16][16])[u][v] = color;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load graffiti data", e);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        File file = new File(event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "graffiti.bin");
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file))))) {
            List<long[]> data = new ArrayList<>();
            SERVER_CACHE.forEach((pos, sides) -> sides.forEach((side, grid) -> {
                for (int u = 0; u < 16; u++) {
                    for (int v = 0; v < 16; v++) {
                        if (grid[u][v] != 0) {
                            data.add(new long[]{pos, side.get3DDataValue(), u, v, grid[u][v]});
                        }
                    }
                }
            }));

            out.writeInt(data.size());
            for (long[] p : data) {
                out.writeLong(p[0]);
                out.writeByte((int) p[1]);
                out.writeByte((int) p[2]);
                out.writeByte((int) p[3]);
                out.writeInt((int) p[4]);
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
            SERVER_CACHE.forEach((posL, sides) -> sides.forEach((side, grid) -> {
                for (int u = 0; u < 16; u++) {
                    for (int v = 0; v < 16; v++) {
                        if (grid[u][v] != 0) {
                            syncData.add(new PaintPayload(net.minecraft.core.BlockPos.of(posL), side, u, v, grid[u][v], 1));
                        }
                    }
                }
            }));

            if (!syncData.isEmpty()) {
                PacketDistributor.sendToPlayer((ServerPlayer) player, new SyncGraffitiPayload(syncData));
            }
        });
    }
}
