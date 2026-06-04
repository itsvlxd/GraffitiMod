package its.vlxd.graffiti.client.gui;

import its.vlxd.graffiti.item.GraffitiItem;
import its.vlxd.graffiti.network.ColorPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.awt.Color;

// FIX: add fallback to sqaure for any brush if the size is 1

public class GraffitiScreen extends Screen {
    private static final int PS = 100;
    private static final int W = 260, H = 185;

    private final ItemStack stack;
    private int px, py;
    private float hue, saturation, brightness, alpha;
    private int brushSize, brushShape, toolMode;
    private EditBox hexField;
    private Button sizeDown, sizeUp, shapeBtn, toolBtn;
    private boolean colorWasChanged = false;
    private boolean isLocked;

    private DynamicTexture paletteTexture;
    private ResourceLocation textureId;

    private static final int[] SIZES = {1, 2, 3, 4, 5, 6, 7, 8};
    private static final String[] SHAPES = {"Square", "Circle", "Rounded", "Cloud", "Leaky"};
    private static final String[] TOOLS = {"Pencil", "Fill", "Picker"};

    public GraffitiScreen(ItemStack stack) {
        super(Component.literal("Graffiti Editor"));
        this.stack = stack;
    }

    @Override 
    public boolean isPauseScreen() { 
        return false; 
    }
    
    @Override 
    public void renderBackground(GuiGraphics ctx, int mx, int my, float dt) {}

    @Override
    protected void init() {
        px = (width - W) / 2; 
        py = (height - H) / 2;

        var player = Minecraft.getInstance().player;
        isLocked = player != null && !player.isCreative() && GraffitiItem.isColorLocked(stack);

        int ic = GraffitiItem.getColor(stack);
        float[] hsb = Color.RGBtoHSB((ic >> 16) & 0xFF, (ic >> 8) & 0xFF, ic & 0xFF, null);
        hue = hsb[0]; 
        saturation = hsb[1]; 
        brightness = hsb[2]; 
        alpha = ((ic >> 24) & 0xFF) / 255f;
        brushSize = GraffitiItem.getBrushSize(stack);
        brushShape = GraffitiItem.getBrushShape(stack);
        toolMode = GraffitiItem.getToolMode(stack);

        hexField = new EditBox(font, px + 172, py + 77, 65, 16, Component.literal("Hex"));
        hexField.setValue(String.format("#%08X", ic));
        hexField.setEditable(!isLocked);
        addRenderableWidget(hexField);

        sizeDown = Button.builder(Component.literal("-"), b -> { 
            brushSize = SIZES[Math.max(0, idx(SIZES, brushSize) - 1)]; 
            saveSize(); 
        }).bounds(px + 165, py + 99, 16, 16).tooltip(Tooltip.create(Component.literal("Smaller"))).build();
        addRenderableWidget(sizeDown);

        sizeUp = Button.builder(Component.literal("+"), b -> { 
            brushSize = SIZES[Math.min(SIZES.length - 1, idx(SIZES, brushSize) + 1)]; 
            saveSize(); 
        }).bounds(px + 221, py + 99, 16, 16).tooltip(Tooltip.create(Component.literal("Bigger"))).build();
        addRenderableWidget(sizeUp);

        shapeBtn = Button.builder(Component.literal(SHAPES[brushShape]), b -> {
            brushShape = (brushShape + 1) % 5;
            shapeBtn.setMessage(Component.literal(SHAPES[brushShape]));
            GraffitiItem.setBrushShape(stack, brushShape);
        }).bounds(px + 165, py + 121, 72, 16).tooltip(Tooltip.create(Component.literal("Cycle brush shape"))).build();
        addRenderableWidget(shapeBtn);

        toolBtn = Button.builder(Component.literal(TOOLS[toolMode]), b -> {
            if (isLocked) {
                toolMode = (toolMode + 1) % 2;
            } else {
                toolMode = (toolMode + 1) % 3;
            }
            toolBtn.setMessage(Component.literal(TOOLS[toolMode]));
            GraffitiItem.setToolMode(stack, toolMode);
        }).bounds(px + 165, py + 143, 72, 16).tooltip(Tooltip.create(Component.literal("Cycle tool mode"))).build();
        addRenderableWidget(toolBtn);

        updatePaletteTexture();
    }

    private static int idx(int[] a, int v) { 
        for (int i = 0; i < a.length; i++) {
            if (a[i] == v) return i; 
        }
        return 0; 
    }
    
    private void saveSize() { 
        GraffitiItem.setBrushSize(stack, brushSize); 
    }

    private void updatePaletteTexture() {
        if (paletteTexture != null) paletteTexture.close();
        NativeImage img = new NativeImage(PS, PS, false);
        for (int i = 0; i < PS; i++) {
            for (int j = 0; j < PS; j++) {
                int c = Color.HSBtoRGB(hue, i / 100f, 1f - j / 100f);
                img.setPixelRGBA(i, j, 0xFF000000 | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF));
            }
        }
        paletteTexture = new DynamicTexture(img);
        textureId = ResourceLocation.parse("graffiti:palette_cache");
        minecraft.getTextureManager().register(textureId, paletteTexture);
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float dt) {
        ctx.fill(px, py, px + W, py + H, 0xCC0A0A0A);
        ctx.renderOutline(px, py, W, H, 0xFF555555);

        ctx.drawString(font, title, px + (W - font.width(title)) / 2, py + 6, 0xCCCCCC);
        ctx.fill(px + 4, py + 18, px + W - 4, py + 19, 0xFF444444);

        if (isLocked) {
            ctx.drawString(font, Component.literal("Color Locked"), px + 117 + 4, py + 26 + 12, 0xFF5555);
        }

        int ppX = px + 10, ppY = py + 26;
        ctx.fill(ppX - 1, ppY - 1, ppX + PS + 1, ppY + PS + 1, 0xFF444444);
        if (textureId != null) ctx.blit(textureId, ppX, ppY, 0, 0, PS, PS, PS, PS);
        if (isLocked) ctx.fill(ppX, ppY, ppX + PS, ppY + PS, 0x55000000);

        int sx = ppX + (int)(saturation * PS), sy = ppY + (int)((1f - brightness) * PS);
        ctx.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFFFFFFFF);

        int rX = px + 117, rY = ppY;
        ctx.fill(rX, rY, rX + 126, rY + 136, 0xFF151515);
        ctx.renderOutline(rX, rY, 126, 136, 0xFF444444);

        int live = (Math.round(alpha * 255) << 24) | (Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF);
        ctx.fill(rX + 4, rY + 4, rX + 122, rY + 26, live);
        ctx.fill(rX + 4, rY + 27, rX + 122, rY + 28, 0xFF444444);

        ctx.drawString(font, Component.literal("Current Color"), rX + 6, rY + 34, 0xAAAAAA);
        ctx.drawString(font, Component.literal("Hex Code:"), rX + 6, rY + 54, 0x888888);
        ctx.drawString(font, Component.literal("Size:"), rX + 6, rY + 76, 0x888888);
        ctx.drawString(font, Component.literal("Shape:"), rX + 6, rY + 98, 0x888888);
        ctx.drawString(font, Component.literal("Mode:"), rX + 6, rY + 120, 0x888888);

        String sizeStr = String.valueOf(brushSize);
        int sizeTextWidth = font.width(sizeStr);
        int centeredSizeX = (px + 181) + ((40 - sizeTextWidth) / 2); 
        ctx.drawString(font, Component.literal(sizeStr), centeredSizeX, py + 103, 0xFFFFFF);

        int hY = ppY + PS + 8;
        ctx.fill(ppX - 1, hY - 1, ppX + PS + 1, hY + 11, 0xFF555555);
        ctx.drawString(font, Component.literal("Hue"), px + 10, hY + 13, 0xAAAAAA);
        for (int i = 0; i < PS; i++) {
            ctx.fill(ppX + i, hY, ppX + i + 1, hY + 10, 0xFF000000 | Color.HSBtoRGB(i / 100f, 1f, 1f));
        }
        ctx.fill(ppX + (int)(hue * PS) - 1, hY - 1, ppX + (int)(hue * PS) + 2, hY + 11, 0xFFFFFFFF);
        if (isLocked) ctx.fill(ppX, hY, ppX + PS, hY + 10, 0x55000000);

        int aY = hY + 25;
        ctx.fill(ppX - 1, aY - 1, ppX + PS + 1, aY + 11, 0xFF555555);
        ctx.drawString(font, Component.literal("Alpha"), px + 10, aY + 13, 0xAAAAAA);
        for (int i = 0; i < PS; i++) {
            int g = (int)((i / 100f) * 255);
            ctx.fill(ppX + i, aY, ppX + i + 1, aY + 10, 0xFF000000 | (g << 16 | g << 8 | g));
        }
        ctx.fill(ppX + (int)(alpha * PS) - 1, aY - 1, ppX + (int)(alpha * PS) + 2, aY + 11, 0xFFFFFFFF);
        if (isLocked) ctx.fill(ppX, aY, ppX + PS, aY + 10, 0x55000000);

        super.render(ctx, mx, my, dt);
    }

    private void handleInputs(double mx, double my) {
        if (isLocked) return;

        int ppX = px + 10, ppY = py + 26;
        boolean changed = false;
        boolean hueChanged = false;

        if (mx >= ppX && mx < ppX + PS && my >= ppY && my < ppY + PS) {
            saturation = (float)((mx - ppX) / PS);
            brightness = 1f - (float)((my - ppY) / PS);
            changed = true;
        } else if (mx >= ppX && mx < ppX + PS && my >= ppY + PS + 8 && my < ppY + PS + 18) {
            hue = (float)((mx - ppX) / PS);
            hueChanged = true;
            changed = true;
        } else if (mx >= ppX && mx < ppX + PS && my >= ppY + PS + 33 && my < ppY + PS + 43) {
            alpha = (float)((mx - ppX) / PS);
            changed = true;
        }

        if (hueChanged) updatePaletteTexture();
        if (changed) {
            colorWasChanged = true;
            hexField.setValue(String.format("#%08X", (Math.round(alpha * 255) << 24) | (Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF)));
        }
    }

    @Override 
    public boolean mouseClicked(double mx, double my, int b) { 
        handleInputs(mx, my); 
        return super.mouseClicked(mx, my, b); 
    }
    
    @Override 
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) { 
        handleInputs(mx, my); 
        return super.mouseDragged(mx, my, b, dx, dy); 
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) { 
        return super.mouseReleased(mx, my, b); 
    }

    @Override
    public void onClose() { 
        flushSettings(); 
        if (paletteTexture != null) paletteTexture.close(); 
        super.onClose(); 
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) { 
        if (key == 256) { 
            onClose(); 
            return true; 
        } 
        return super.keyPressed(key, scanCode, modifiers); 
    }

    private void flushSettings() {
        int newColor = (Math.round(alpha * 255) << 24) | (Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF);
        GraffitiItem.setColor(stack, newColor);

        var player = Minecraft.getInstance().player;
        if (player != null && !player.isCreative() && colorWasChanged) {
            GraffitiItem.setColorLocked(stack, true);
        }

        PacketDistributor.sendToServer(new ColorPayload(newColor, brushSize, brushShape, toolMode));
    }
}
