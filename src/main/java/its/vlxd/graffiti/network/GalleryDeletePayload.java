package its.vlxd.graffiti.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record GalleryDeletePayload(UUID designId) implements CustomPacketPayload {
    public static final Type<GalleryDeletePayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:gallery_delete"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GalleryDeletePayload> CODEC = StreamCodec.of(
            (buf, val) -> {
                buf.writeLong(val.designId().getMostSignificantBits());
                buf.writeLong(val.designId().getLeastSignificantBits());
            },
            buf -> new GalleryDeletePayload(new UUID(buf.readLong(), buf.readLong()))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
