package its.vlxd.graffiti.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PaintPayload(BlockPos pos, Direction side, int u, int v, int color, int size) implements CustomPacketPayload {
    public static final Type<PaintPayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:paint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PaintPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PaintPayload::pos,
            Direction.STREAM_CODEC, PaintPayload::side,
            ByteBufCodecs.VAR_INT, PaintPayload::u,
            ByteBufCodecs.VAR_INT, PaintPayload::v,
            ByteBufCodecs.INT, PaintPayload::color,
            ByteBufCodecs.VAR_INT, PaintPayload::size,
            PaintPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
