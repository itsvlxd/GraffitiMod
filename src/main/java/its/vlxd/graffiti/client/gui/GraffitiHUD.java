package its.vlxd.graffiti.client.gui;

import its.vlxd.graffiti.item.GraffitiItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class GraffitiHUD {
    private static final Component[] TOOL_NAMES = {
            Component.translatable("hud.graffiti.tool.pencil"),
            Component.translatable("hud.graffiti.tool.fill"),
            Component.translatable("hud.graffiti.tool.picker")
    };

    private static final int[] MODE_COLORS = {0xFFFFFF, 0x55FF55, 0xFFDD44};

    public static final int[] SHAPE_COLORS = {0xFF5555, 0xFF7700, 0x77AA77, 0xAAAAFF, 0x77FFAA};

    public static Component getColoredShapeName(String name, int shape) {
        int color = shape >= 0 && shape < SHAPE_COLORS.length ? SHAPE_COLORS[shape] : SHAPE_COLORS[0];
        return Component.literal(name).withStyle(s -> s.withColor(color));
    }

    public static void init() {}

    public static int getSelectedIndex() {
        var client = Minecraft.getInstance();
        if (client.player != null) {
            return GraffitiItem.getToolMode(client.player.getMainHandItem());
        }
        return 0;
    }

    public static Component getColoredToolName() {
        int idx = getSelectedIndex();
        return Component.literal(TOOL_NAMES[idx].getString())
                .withStyle(s -> s.withColor(MODE_COLORS[idx]));
    }
}
