package its.vlxd.graffiti.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SprayPaintStopPayload() implements CustomPacketPayload {
    public static final Type<SprayPaintStopPayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:spray_paint_stop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SprayPaintStopPayload> CODEC = StreamCodec.unit(new SprayPaintStopPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
