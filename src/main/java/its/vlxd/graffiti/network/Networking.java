package its.vlxd.graffiti.network;

import its.vlxd.graffiti.GraffitiMod;
import its.vlxd.graffiti.item.GraffitiItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Networking {
    private static final Map<UUID, Integer> SOUND_COOLDOWN = new HashMap<>();
    private static final int SOUND_INTERVAL = 5;

    private static boolean tryPlaySound(Player player, ServerLevel level, double x, double y, double z, SoundEvent sound, float pitch) {
        int tick = player.getServer().getTickCount();
        Integer last = SOUND_COOLDOWN.get(player.getUUID());
        if (last != null && tick - last < SOUND_INTERVAL) return false;
        SOUND_COOLDOWN.put(player.getUUID(), tick);
        level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, 1.0f, pitch);
        return true;
    }
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

        registrar.playToServer(
                CleanPayload.TYPE,
                CleanPayload.CODEC,
                (payload, context) -> handleCleanServer(payload, context)
        );

        registrar.playToServer(
                SprayEquipPayload.TYPE,
                SprayEquipPayload.CODEC,
                (payload, context) -> handleSprayEquip(payload, context)
        );

        registrar.playToServer(
                SprayPaintPayload.TYPE,
                SprayPaintPayload.CODEC,
                (payload, context) -> handleSprayPaint(payload, context)
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

    private static void handleCleanServer(CleanPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || player.getServer() == null) return;

            ItemStack held = player.getMainHandItem();
            int alphaReduction;
            if (held.is(GraffitiMod.WET_BRUSH.get())) {
                alphaReduction = 51;
            } else if (held.is(GraffitiMod.BRUSH.get())) {
                alphaReduction = 16;
            } else {
                return;
            }

            BlockPos pos = payload.pos();
            Direction side = payload.side();
            long ck = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            long posL = pos.asLong();

            var chunk = GraffitiMod.SERVER_CACHE.get(ck);
            if (chunk == null) return;
            var faces = chunk.get(posL);
            if (faces == null) return;
            int[][] grid = faces.get(side);
            if (grid == null) return;

            GraffitiMod.saveSnapshot(ck, posL, side);

            boolean changed = false;
            int radius = payload.radius();
            for (int du = -radius; du <= radius; du++) {
                for (int dv = -radius; dv <= radius; dv++) {
                    if (du * du + dv * dv > radius * radius) continue;
                    int tu = payload.u() + du;
                    int tv = payload.v() + dv;
                    if (tu < 0 || tu >= 16 || tv < 0 || tv >= 16) continue;

                    int color = grid[tu][tv];
                    if (color == 0) continue;

                    int alpha = (color >> 24) & 0xFF;
                    alpha -= alphaReduction;
                    if (alpha <= 0) {
                        grid[tu][tv] = 0;
                    } else {
                        grid[tu][tv] = (alpha << 24) | (color & 0xFFFFFF);
                    }
                    changed = true;
                }
            }

            if (changed) {
                var level = player.getServer().getLevel(player.level().dimension());
                if (level != null) {
                    tryPlaySound(player, level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            SoundEvents.BRUSH_GENERIC, 1.0f);
                    GraffitiMod.broadcastFace(pos, side, grid, level);
                }
            }
        });
    }

    private static void handleSprayEquip(SprayEquipPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || player.getServer() == null) return;
            var level = player.getServer().getLevel(player.level().dimension());
            if (level != null) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        GraffitiMod.SPRAY_CAN_EQUIP.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        });
    }

    private static void handleSprayPaint(SprayPaintPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || player.getServer() == null) return;
            var level = player.getServer().getLevel(player.level().dimension());
            if (level != null) {
                BlockPos pos = payload.pos();
                level.playSound(player, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        GraffitiMod.SPRAY_CAN_PAINT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        });
    }
}
