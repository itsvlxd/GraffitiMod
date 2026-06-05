package its.vlxd.graffiti.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DebugPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<DebugPayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:debug_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DebugPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DebugPayload::enabled,
            DebugPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
