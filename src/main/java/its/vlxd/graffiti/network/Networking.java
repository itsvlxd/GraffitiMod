package its.vlxd.graffiti.network;

import its.vlxd.graffiti.GraffitiMod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

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
                (payload, context) -> {}
        );

        registrar.playToClient(
                SyncGraffitiPayload.TYPE,
                SyncGraffitiPayload.CODEC,
                (payload, context) -> dispatchClientSync(payload, context)
        );
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

            GraffitiMod.SERVER_CACHE.computeIfAbsent(payload.pos().asLong(), k -> new java.util.EnumMap<>(net.minecraft.core.Direction.class))
                    .computeIfAbsent(payload.side(), k -> new int[16][16])[payload.u()][payload.v()] = payload.color();

            for (var otherPlayer : player.getServer().getLevel(player.level().dimension()).players()) {
                if (otherPlayer != player) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(otherPlayer, payload);
                }
            }
        });
    }
}
