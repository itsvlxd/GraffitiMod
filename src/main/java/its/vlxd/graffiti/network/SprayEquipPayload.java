package its.vlxd.graffiti.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SprayEquipPayload() implements CustomPacketPayload {
    public static final Type<SprayEquipPayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:spray_equip"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SprayEquipPayload> CODEC = StreamCodec.unit(new SprayEquipPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
