package its.vlxd.graffiti.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ColorPayload(int color, int brushSize, int brushShape) implements CustomPacketPayload {
    public static final Type<ColorPayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:sync_color"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColorPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ColorPayload::color,
            ByteBufCodecs.VAR_INT, ColorPayload::brushSize,
            ByteBufCodecs.VAR_INT, ColorPayload::brushShape,
            ColorPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
