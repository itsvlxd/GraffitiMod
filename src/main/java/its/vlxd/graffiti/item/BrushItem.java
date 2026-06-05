package its.vlxd.graffiti.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public class BrushItem extends Item {
    public static final int SHAPE_SQUARE = 0;
    public static final int SHAPE_CIRCLE = 1;
    public static final int SHAPE_ROUNDED = 2;

    public BrushItem(Properties properties) { super(properties); }

    private static CompoundTag getTag(ItemStack stack) {
        var existing = stack.get(DataComponents.CUSTOM_DATA);
        return existing != null ? existing.copyTag() : new CompoundTag();
    }

    private static void saveTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getSize(ItemStack stack) {
        var tag = getTag(stack);
        return tag.contains("BrushSize") ? tag.getInt("BrushSize") : 3;
    }

    public static void setSize(ItemStack stack, int size) {
        var tag = getTag(stack);
        tag.putInt("BrushSize", Math.max(1, Math.min(16, size)));
        saveTag(stack, tag);
    }

    public static int getShape(ItemStack stack) {
        var tag = getTag(stack);
        int shape = tag.contains("BrushShape") ? tag.getInt("BrushShape") : SHAPE_CIRCLE;
        if (shape < 0 || shape > 2) return SHAPE_SQUARE;
        return shape;
    }

    public static void setShape(ItemStack stack, int shape) {
        var tag = getTag(stack);
        tag.putInt("BrushShape", Math.max(0, Math.min(2, shape)));
        saveTag(stack, tag);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        if (stack.getMaxDamage() > 0) {
            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            tooltip.add(Component.literal("Durability: " + remaining + "/" + stack.getMaxDamage())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("Size: " + getSize(stack) + " Shape: " + getShapeName(getShape(stack)))
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, type);
    }

    public static String getShapeName(int shape) {
        return switch (shape) {
            case SHAPE_CIRCLE -> "Circle";
            case SHAPE_ROUNDED -> "Rounded";
            default -> "Square";
        };
    }
}
