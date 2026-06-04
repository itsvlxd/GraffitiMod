package its.vlxd.graffiti.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SnapshotPayload(BlockPos pos, Direction side) implements CustomPacketPayload {
    public static final Type<SnapshotPayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SnapshotPayload::pos,
            Direction.STREAM_CODEC, SnapshotPayload::side,
            SnapshotPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
