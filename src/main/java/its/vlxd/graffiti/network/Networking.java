package its.vlxd.graffiti.network;

import its.vlxd.graffiti.GraffitiMod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class Networking {
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(GraffitiMod.MOD_ID);

        registrar.playToServer(
                PaintPayload.TYPE,
                PaintPayload.CODEC,
                (payload, context) -> handlePaintPacketServer(payload, context)
        );

        registrar.playToServer(
                ColorPayload.TYPE,
                ColorPayload.CODEC,
                (payload, context) -> {}
        );
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
