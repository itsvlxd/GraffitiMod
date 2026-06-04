package its.vlxd.graffiti.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CleanPayload(BlockPos pos, Direction side, int u, int v, int radius) implements CustomPacketPayload {
    public static final Type<CleanPayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:clean"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CleanPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CleanPayload::pos,
            Direction.STREAM_CODEC, CleanPayload::side,
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, CleanPayload::u,
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, CleanPayload::v,
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, CleanPayload::radius,
            CleanPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
