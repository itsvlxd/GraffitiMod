package its.vlxd.graffiti.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record GalleryPastePayload(UUID designId, BlockPos targetPos, Direction targetSide) implements CustomPacketPayload {
    public static final Type<GalleryPastePayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:gallery_paste"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GalleryPastePayload> CODEC = StreamCodec.of(
            (buf, val) -> {
                buf.writeLong(val.designId().getMostSignificantBits());
                buf.writeLong(val.designId().getLeastSignificantBits());
                BlockPos.STREAM_CODEC.encode(buf, val.targetPos());
                Direction.STREAM_CODEC.encode(buf, val.targetSide());
            },
            buf -> {
                UUID id = new UUID(buf.readLong(), buf.readLong());
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                Direction side = Direction.STREAM_CODEC.decode(buf);
                return new GalleryPastePayload(id, pos, side);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
