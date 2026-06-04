package its.vlxd.graffiti.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SprayPaintPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<SprayPaintPayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:spray_paint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SprayPaintPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SprayPaintPayload::pos,
            SprayPaintPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
