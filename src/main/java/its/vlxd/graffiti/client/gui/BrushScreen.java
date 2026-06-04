package its.vlxd.graffiti.client.gui;

import its.vlxd.graffiti.item.BrushItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// FIX: fix the menu placement
// FIX: brush doesnt save its settings when thron
// into water to become wet

public class BrushScreen extends Screen {
    private static final int W = 200, H = 95;

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

        int lx = px + 10;
        int cx = px + 50;

        sizeDown = Button.builder(Component.literal("-"), b -> {
            brushSize = SIZES[Math.max(0, idx(SIZES, brushSize) - 1)];
            BrushItem.setSize(stack, brushSize);
        }).bounds(cx, py + 30, 18, 16).build();
        addRenderableWidget(sizeDown);

        sizeUp = Button.builder(Component.literal("+"), b -> {
            brushSize = SIZES[Math.min(SIZES.length - 1, idx(SIZES, brushSize) + 1)];
            BrushItem.setSize(stack, brushSize);
        }).bounds(cx + 56, py + 30, 18, 16).build();
        addRenderableWidget(sizeUp);

        shapeBtn = Button.builder(Component.literal(SHAPES[brushShape]), b -> {
            brushShape = (brushShape + 1) % 3;
            shapeBtn.setMessage(Component.literal(SHAPES[brushShape]));
            BrushItem.setShape(stack, brushShape);
        }).bounds(cx, py + 54, 74, 16).build();
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

        int lx = px + 10;
        int cx = px + 50;

        ctx.drawString(font, Component.literal("Size:"), lx, py + 31, 0xAAAAAA);
        String sz = String.valueOf(brushSize);
        ctx.drawString(font, Component.literal(sz), cx + 24, py + 31, 0xFFFFFF);

        ctx.drawString(font, Component.literal("Shape:"), lx, py + 55, 0xAAAAAA);

        super.render(ctx, mx, my, dt);
    }

    @Override public void onClose() { super.onClose(); }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256) { onClose(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
