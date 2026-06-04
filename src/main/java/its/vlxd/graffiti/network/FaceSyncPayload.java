package its.vlxd.graffiti.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record FaceSyncPayload(BlockPos pos, Direction side, int[] flatGrid) implements CustomPacketPayload {
    public static final Type<FaceSyncPayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:face_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FaceSyncPayload> CODEC = StreamCodec.of(
            (buf, val) -> {
                BlockPos.STREAM_CODEC.encode(buf, val.pos());
                Direction.STREAM_CODEC.encode(buf, val.side());
                for (int i = 0; i < 256; i++) {
                    buf.writeInt(val.flatGrid()[i]);
                }
            },
            buf -> {
                var pos = BlockPos.STREAM_CODEC.decode(buf);
                var side = Direction.STREAM_CODEC.decode(buf);
                int[] grid = new int[256];
                for (int i = 0; i < 256; i++) {
                    grid[i] = buf.readInt();
                }
                return new FaceSyncPayload(pos, side, grid);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
