package its.vlxd.graffiti.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record SyncGraffitiPayload(List<PaintPayload> allPixels) implements CustomPacketPayload {
    public static final Type<SyncGraffitiPayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:sync_all"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncGraffitiPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.allPixels().size());
                for (PaintPayload p : value.allPixels()) {
                    PaintPayload.CODEC.encode(buf, p);
                }
            },
            buf -> {
                int size = buf.readVarInt();
                List<PaintPayload> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(PaintPayload.CODEC.decode(buf));
                }
                return new SyncGraffitiPayload(list);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
