package its.vlxd.graffiti.client.gui;

import its.vlxd.graffiti.item.GraffitiItem;
import its.vlxd.graffiti.client.renderer.GraffitiRenderer;
import its.vlxd.graffiti.network.ColorPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.awt.Color;

public class GraffitiScreen extends Screen {
    private static final int PALETTE_SIZE = 100;

    private final ItemStack stack;
    private int px, py;
    private float hue, saturation, brightness, alpha;
    private EditBox hexField, sizeField;

    private DynamicTexture paletteTexture;
    private ResourceLocation textureId;

    public GraffitiScreen(ItemStack stack) {
        super(Component.translatable("screen.graffiti.title"));
        this.stack = stack;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {}

    @Override
    protected void init() {
        this.px = width / 2 - 120;
        this.py = height / 2 - 70;

        int itemColor = GraffitiItem.getColor(stack);

        float[] hsb = Color.RGBtoHSB((itemColor >> 16) & 0xFF, (itemColor >> 8) & 0xFF, itemColor & 0xFF, null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alpha = ((itemColor >> 24) & 0xFF) / 255f;

        hexField = new EditBox(font, px + 110, py + 15, 85, 16, Component.translatable("screen.graffiti.hex_label"));
        sizeField = new EditBox(font, px + 110, py + 45, 40, 16, Component.translatable("screen.graffiti.size_label"));

        hexField.setValue(String.format("#%08X", itemColor));
        sizeField.setValue(String.valueOf(GraffitiItem.brushSize));

        this.addRenderableWidget(hexField);
        this.addRenderableWidget(sizeField);

        updatePaletteTexture();
    }

    private void updatePaletteTexture() {
        if (paletteTexture != null) paletteTexture.close();
        NativeImage img = new NativeImage(PALETTE_SIZE, PALETTE_SIZE, false);
        for (int i = 0; i < PALETTE_SIZE; i++) {
            for (int j = 0; j < PALETTE_SIZE; j++) {
                int c = Color.HSBtoRGB(hue, i / 100f, 1.0f - (j / 100f));
                int abgr = 0xFF000000 | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF);
                img.setPixelRGBA(i, j, abgr);
            }
        }
        paletteTexture = new DynamicTexture(img);
        textureId = ResourceLocation.parse("graffiti:palette_cache");
        minecraft.getTextureManager().register(textureId, paletteTexture);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(px - 10, py - 10, px + PALETTE_SIZE + 105, py + PALETTE_SIZE + 40, 0x88000000);
        if (textureId != null) context.blit(textureId, px, py, 0, 0, PALETTE_SIZE, PALETTE_SIZE, PALETTE_SIZE, PALETTE_SIZE);

        for (int i = 0; i < PALETTE_SIZE; i++) {
            context.fill(px + i, py + PALETTE_SIZE + 5, px + i + 1, py + PALETTE_SIZE + 15, 0xFF000000 | Color.HSBtoRGB(i/100f, 1f, 1f));
            int gray = (int)((i/100f)*255);
            context.fill(px + i, py + PALETTE_SIZE + 20, px + i + 1, py + PALETTE_SIZE + 30, 0xFF000000 | (gray << 16 | gray << 8 | gray));
        }

        context.fill(px + (int)(saturation * PALETTE_SIZE) - 2, py + (int)((1f - brightness) * PALETTE_SIZE) - 2, px + (int)(saturation * PALETTE_SIZE) + 3, py + (int)((1f - brightness) * PALETTE_SIZE) + 3, 0xFFFFFFFF);
        context.fill(px + (int)(hue * PALETTE_SIZE), py + PALETTE_SIZE + 4, px + (int)(hue * PALETTE_SIZE) + 2, py + PALETTE_SIZE + 16, 0xFFFFFFFF);
        context.fill(px + (int)(alpha * PALETTE_SIZE), py + PALETTE_SIZE + 19, px + (int)(alpha * PALETTE_SIZE) + 2, py + PALETTE_SIZE + 31, 0xFFFFFFFF);

        context.drawString(font, Component.translatable("screen.graffiti.hex_alpha"), px + 110, py + 5, 0xFFFFFF);
        context.drawString(font, Component.translatable("screen.graffiti.brush_size"), px + 110, py + 35, 0xFFFFFF);

        context.fill(px + 110, py + 70, px + 185, py + 95, 0xFFFFFFFF);
        context.fill(px + 111, py + 71, px + 184, py + 94, GraffitiItem.getColor(stack));

        super.render(context, mouseX, mouseY, delta);
    }

    private void handleInputs(double mx, double my) {
        boolean hueChanged = false;
        if (mx >= px && mx < px + PALETTE_SIZE && my >= py && my < py + PALETTE_SIZE) {
            saturation = (float)((mx - px) / PALETTE_SIZE);
            brightness = 1.0f - (float)((my - py) / PALETTE_SIZE);
        } else if (mx >= px && mx < px + PALETTE_SIZE && my >= py + PALETTE_SIZE + 5 && my < py + PALETTE_SIZE + 15) {
            hue = (float)((mx - px) / PALETTE_SIZE);
            hueChanged = true;
        } else if (mx >= px && mx < px + PALETTE_SIZE && my >= py + PALETTE_SIZE + 20 && my < py + PALETTE_SIZE + 30) {
            alpha = (float)((mx - px) / PALETTE_SIZE);
        }
        if (hueChanged) updatePaletteTexture();
        updateFinalColor();
    }

    private void updateFinalColor() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        int newColor = (Math.round(alpha * 255) << 24) | rgb;

        GraffitiItem.setColor(stack, newColor);
        PacketDistributor.sendToServer(new ColorPayload(newColor));

        hexField.setValue(String.format("#%08X", newColor));
        if (minecraft != null && minecraft.levelRenderer != null) minecraft.levelRenderer.allChanged();
    }

    @Override public boolean mouseClicked(double mx, double my, int b) { handleInputs(mx, my); return super.mouseClicked(mx, my, b); }
    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) { handleInputs(mx, my); return super.mouseDragged(mx, my, b, dx, dy); }

    @Override
    public void onClose() {
        if (paletteTexture != null) paletteTexture.close();
        try { GraffitiItem.brushSize = Integer.parseInt(sizeField.getValue()); } catch (Exception ignored) {}
        super.onClose();
    }

}
