package its.vlxd.graffiti.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;

import java.util.List;

public class GraffitiItem extends Item {
    public static int brushSize = 1;

    public GraffitiItem(Properties properties) { super(properties); }

    public static int getColor(ItemStack stack) {
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            var nbt = customData.copyTag();
            if (nbt.contains("GraffitiColor")) {
                return nbt.getInt("GraffitiColor");
            }
        }
        return 0xFFFFFFFF;
    }

    public static void setColor(ItemStack stack, int color) {
        var nbt = new net.minecraft.nbt.CompoundTag();
        var existing = stack.get(DataComponents.CUSTOM_DATA);
        if (existing != null) {
            nbt = existing.copyTag();
        }
        nbt.putInt("GraffitiColor", color);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        int color = getColor(stack);
        String hexString = String.format("#%06X", color & 0xFFFFFF);
        tooltip.add(Component.translatable("tooltip.graffiti.color_hex")
                .append(": ")
                .append(Component.literal(hexString).withStyle(net.minecraft.ChatFormatting.GRAY)));
        super.appendHoverText(stack, context, tooltip, type);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        return InteractionResultHolder.success(user.getItemInHand(hand));
    }
}
