package its.vlxd.graffiti.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GallerySavePayload(String name, BlockPos pos1, BlockPos pos2) implements CustomPacketPayload {
    public static final Type<GallerySavePayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:gallery_save"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GallerySavePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GallerySavePayload::name,
            BlockPos.STREAM_CODEC, GallerySavePayload::pos1,
            BlockPos.STREAM_CODEC, GallerySavePayload::pos2,
            GallerySavePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
