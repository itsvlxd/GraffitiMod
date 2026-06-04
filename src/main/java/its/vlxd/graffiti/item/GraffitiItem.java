package its.vlxd.graffiti.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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
    public static final int SHAPE_SQUARE = 0;
    public static final int SHAPE_CIRCLE = 1;
    public static final int SHAPE_ROUNDED = 2;
    public static final int SHAPE_CLOUD = 3;
    public static final int SHAPE_LEAKY = 4;

    public GraffitiItem(Properties properties) { super(properties); }

    private static CompoundTag getTag(ItemStack stack) {
        var existing = stack.get(DataComponents.CUSTOM_DATA);
        return existing != null ? existing.copyTag() : new CompoundTag();
    }

    private static void saveTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getColor(ItemStack stack) {
        var tag = getTag(stack);
        return tag.contains("GraffitiColor") ? tag.getInt("GraffitiColor") : 0xFFFFFFFF;
    }

    public static void setColor(ItemStack stack, int color) {
        var tag = getTag(stack);
        tag.putInt("GraffitiColor", color);
        saveTag(stack, tag);
    }

    public static int getBrushSize(ItemStack stack) {
        var tag = getTag(stack);
        return tag.contains("BrushSize") ? tag.getInt("BrushSize") : 1;
    }

    public static void setBrushSize(ItemStack stack, int size) {
        var tag = getTag(stack);
        tag.putInt("BrushSize", Math.max(1, Math.min(8, size)));
        saveTag(stack, tag);
    }

    public static int getBrushShape(ItemStack stack) {
        var tag = getTag(stack);
        return tag.contains("BrushShape") ? tag.getInt("BrushShape") : SHAPE_SQUARE;
    }

    public static void setBrushShape(ItemStack stack, int shape) {
        var tag = getTag(stack);
        tag.putInt("BrushShape", Math.max(0, Math.min(4, shape)));
        saveTag(stack, tag);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        int color = getColor(stack);
        String hexString = String.format("#%06X", color & 0xFFFFFF);
        tooltip.add(Component.translatable("tooltip.graffiti.color_hex")
                .append(": ")
                .append(Component.literal(hexString).withStyle(net.minecraft.ChatFormatting.GRAY)));
        tooltip.add(Component.literal("Size: " + getBrushSize(stack) + " Shape: " + getShapeName(getBrushShape(stack)))
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, type);
    }

    private static String getShapeName(int shape) {
        return switch (shape) {
            case SHAPE_CIRCLE -> "Circle";
            case SHAPE_ROUNDED -> "Rounded";
            case SHAPE_CLOUD -> "Cloud";
            case SHAPE_LEAKY -> "Leaky";
            default -> "Square";
        };
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        return InteractionResultHolder.success(user.getItemInHand(hand));
    }
}
