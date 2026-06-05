package its.vlxd.graffiti.client.gui;

import its.vlxd.graffiti.item.BrushItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class BrushScreen extends Screen {
    private static final int W = 150, H = 80;

    private final ItemStack stack;
    private int px, py;
    private int brushSize, brushShape;
    private Button sizeDown, sizeUp, shapeBtn;

    private static final int[] SIZES = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    private static final String[] SHAPES = {"Square", "Circle", "Rounded"};

    public BrushScreen(ItemStack stack) {
        super(Component.literal("Brush Settings"));
        this.stack = stack;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics ctx, int mx, int my, float dt) {}

    @Override
    protected void init() {
        px = (width - W) / 2; py = (height - H) / 2;
        brushSize = BrushItem.getSize(stack);
        brushShape = BrushItem.getShape(stack);

        sizeDown = Button.builder(Component.literal("-"), b -> {
            brushSize = SIZES[Math.max(0, idx(SIZES, brushSize) - 1)];
            BrushItem.setSize(stack, brushSize);
        }).bounds(px + 76, py + 30, 16, 16).build();
        addRenderableWidget(sizeDown);

        sizeUp = Button.builder(Component.literal("+"), b -> {
            brushSize = SIZES[Math.min(SIZES.length - 1, idx(SIZES, brushSize) + 1)];
            BrushItem.setSize(stack, brushSize);
        }).bounds(px + 122, py + 30, 16, 16).build();
        addRenderableWidget(sizeUp);

        shapeBtn = Button.builder(Component.literal(SHAPES[brushShape]), b -> {
            brushShape = (brushShape + 1) % 3;
            shapeBtn.setMessage(Component.literal(SHAPES[brushShape]));
            BrushItem.setShape(stack, brushShape);
        }).bounds(px + 76, py + 52, 62, 16).build();
        addRenderableWidget(shapeBtn);
    }

    private static int idx(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return 0;
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float dt) {
        ctx.fill(px, py, px + W, py + H, 0xCC0A0A0A);
        ctx.renderOutline(px, py, W, H, 0xFF555555);
        ctx.drawString(font, title, px + (W - font.width(title)) / 2, py + 6, 0xCCCCCC);
        ctx.fill(px + 4, py + 17, px + W - 4, py + 18, 0xFF444444);

        int lx = px + 12;

        ctx.drawString(font, Component.literal("Size:"), lx, py + 34, 0xAAAAAA);
        ctx.drawString(font, Component.literal("Shape:"), lx, py + 56, 0xAAAAAA);

        String sz = String.valueOf(brushSize);
        int textWidth = font.width(sz);
        int centeredNumberX = (px + 92) + ((30 - textWidth) / 2);
        ctx.drawString(font, Component.literal(sz), centeredNumberX, py + 34, 0xFFFFFF);

        super.render(ctx, mx, my, dt);
    }

    @Override public void onClose() { super.onClose(); }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256) { onClose(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
