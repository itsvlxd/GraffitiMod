package its.vlxd.graffiti.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UndoPayload(BlockPos pos, Direction side, boolean redo) implements CustomPacketPayload {
    public static final Type<UndoPayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:undo"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UndoPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UndoPayload::pos,
            Direction.STREAM_CODEC, UndoPayload::side,
            ByteBufCodecs.BOOL, UndoPayload::redo,
            UndoPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
