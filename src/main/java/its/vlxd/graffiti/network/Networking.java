package its.vlxd.graffiti.network;

import its.vlxd.graffiti.GraffitiMod;
import its.vlxd.graffiti.item.GraffitiItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

import java.util.HashMap;

public class Networking {
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(GraffitiMod.MOD_ID);

        registrar.playBidirectional(
                PaintPayload.TYPE,
                PaintPayload.CODEC,
                new IPayloadHandler<>() {
                    @Override
                    public void handle(PaintPayload payload, IPayloadContext context) {
                        if (context.flow().isClientbound()) {
                            dispatchClientPaint(payload, context);
                        } else {
                            handlePaintPacketServer(payload, context);
                        }
                    }
                }
        );

        registrar.playToServer(
                ColorPayload.TYPE,
                ColorPayload.CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player == null) return;
                        ItemStack stack = player.getMainHandItem();
                        if (stack.is(GraffitiMod.GRAFFITI_TOOL.get())) {
                            GraffitiItem.setColor(stack, payload.color());
                        }
                    });
                }
        );

        registrar.playToServer(
                UndoPayload.TYPE,
                UndoPayload.CODEC,
                (payload, context) -> handleUndoServer(payload, context)
        );

        registrar.playToServer(
                SnapshotPayload.TYPE,
                SnapshotPayload.CODEC,
                (payload, context) -> handleSnapshotServer(payload, context)
        );

        registrar.playToClient(
                SyncGraffitiPayload.TYPE,
                SyncGraffitiPayload.CODEC,
                (payload, context) -> dispatchClientSync(payload, context)
        );

        registrar.playToClient(
                FaceSyncPayload.TYPE,
                FaceSyncPayload.CODEC,
                (payload, context) -> dispatchClientFaceSync(payload, context)
        );

        registrar.playToClient(
                RemoveGraffitiPayload.TYPE,
                RemoveGraffitiPayload.CODEC,
                (payload, context) -> dispatchClientRemove(payload, context)
        );
    }

    private static void dispatchClientRemove(RemoveGraffitiPayload payload, IPayloadContext context) {
        try {
            Class.forName("its.vlxd.graffiti.client.ClientHandler")
                    .getMethod("handleRemoveGraffiti", RemoveGraffitiPayload.class, IPayloadContext.class)
                    .invoke(null, payload, context);
        } catch (Exception ignored) {}
    }

    private static void dispatchClientFaceSync(FaceSyncPayload payload, IPayloadContext context) {
        try {
            Class.forName("its.vlxd.graffiti.client.ClientHandler")
                    .getMethod("handleFaceSync", FaceSyncPayload.class, IPayloadContext.class)
                    .invoke(null, payload, context);
        } catch (Exception ignored) {}
    }

    private static void handleUndoServer(UndoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || player.getServer() == null) return;
            var level = player.getServer().getLevel(player.level().dimension());
            if (level == null) return;

            BlockPos pos = payload.pos();
            long ck = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            long posL = pos.asLong();

            int[][] grid = payload.redo()
                    ? GraffitiMod.handleRedo(ck, posL, payload.side())
                    : GraffitiMod.handleUndo(ck, posL, payload.side());

            if (grid != null) {
                GraffitiMod.broadcastFace(pos, payload.side(), grid, level);
            }
        });
    }

    private static void handleSnapshotServer(SnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            BlockPos pos = payload.pos();
            long ck = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            long posL = pos.asLong();

            var tickMap = GraffitiMod.LAST_PAINT_TICK;
            var ckMap = tickMap.get(ck);
            if (ckMap == null) return;
            var faceMap = ckMap.get(posL);
            if (faceMap == null) return;
            Integer lastTick = faceMap.get(payload.side());
            if (lastTick == null) return;

            GraffitiMod.saveSnapshot(ck, posL, payload.side());
            faceMap.remove(payload.side());
            if (faceMap.isEmpty()) ckMap.remove(posL);
            if (ckMap.isEmpty()) tickMap.remove(ck);
        });
    }

    private static void dispatchClientPaint(PaintPayload payload, IPayloadContext context) {
        try {
            Class.forName("its.vlxd.graffiti.client.ClientHandler")
                    .getMethod("handlePaintPacket", PaintPayload.class, IPayloadContext.class)
                    .invoke(null, payload, context);
        } catch (Exception ignored) {}
    }

    private static void dispatchClientSync(SyncGraffitiPayload payload, IPayloadContext context) {
        try {
            Class.forName("its.vlxd.graffiti.client.ClientHandler")
                    .getMethod("handleSyncPacket", SyncGraffitiPayload.class, IPayloadContext.class)
                    .invoke(null, payload, context);
        } catch (Exception ignored) {}
    }

    private static void handlePaintPacketServer(PaintPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;

            BlockPos pos = payload.pos();
            long ck = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            long posL = pos.asLong();
            GraffitiMod.SERVER_CACHE.computeIfAbsent(ck, k -> new HashMap<>())
                    .computeIfAbsent(posL, k -> new java.util.EnumMap<>(net.minecraft.core.Direction.class))
                    .computeIfAbsent(payload.side(), k -> new int[16][16])[payload.u()][payload.v()] = payload.color();

            GraffitiMod.discardFutureHistory(ck, posL, payload.side());

            var server = player.getServer();
            if (server != null) {
                GraffitiMod.recordPaintTick(ck, posL, payload.side(), server.getTickCount());
            }

            for (var otherPlayer : player.getServer().getLevel(player.level().dimension()).players()) {
                if (otherPlayer != player) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(otherPlayer, payload);
                }
            }
        });
    }
}
