package its.vlxd.graffiti.client.gui;

import its.vlxd.graffiti.GraffitiMod;
import its.vlxd.graffiti.gallery.SavedDesign;
import its.vlxd.graffiti.item.GraffitiItem;
import its.vlxd.graffiti.network.GalleryDeletePayload;
import its.vlxd.graffiti.network.GalleryPastePayload;
import its.vlxd.graffiti.network.GallerySavePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class GalleryScreen extends Screen {
    private static final int W = 320, H = 230;
    private static final int THUMB_SIZE = 48;

    public static BlockPos selPos1;
    public static BlockPos selPos2;

    public static SavedDesign previewDesign;
    public static boolean previewActive;
    public static int previewYOffset;

    public static List<SavedDesign> cachedDesigns = List.of();

    private int px, py;
    private int page = 0;
    private int maxPage = 0;
    private SavedDesign selectedDesign;
    private EditBox nameField;
    private Button saveBtn, pasteBtn, deleteBtn, selectBtn;

    public GalleryScreen() {
        super(Component.literal("Gallery"));
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

        int totalDesigns = cachedDesigns.size();
        maxPage = Math.max(0, (totalDesigns - 1) / 8);

        nameField = new EditBox(font, px + 55, py + H - 66, 100, 16, Component.literal("Name"));
        nameField.setMaxLength(32);
        nameField.setValue("");
        addRenderableWidget(nameField);

        int btnY = py + H - 26;
        saveBtn = Button.builder(Component.literal("Save"), b -> onSave())
                .bounds(px + 10, btnY, 46, 16).build();
        addRenderableWidget(saveBtn);

        pasteBtn = Button.builder(Component.literal("Paste"), b -> onPaste())
                .bounds(px + 62, btnY, 46, 16).build();
        addRenderableWidget(pasteBtn);

        deleteBtn = Button.builder(Component.literal("Del"), b -> onDelete())
                .bounds(px + 114, btnY, 36, 16).build();
        addRenderableWidget(deleteBtn);

        selectBtn = Button.builder(Component.literal("Select"), b -> onSelect())
                .bounds(px + 156, btnY, 50, 16).build();
        addRenderableWidget(selectBtn);
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float dt) {
        ctx.fill(px, py, px + W, py + H, 0xCC0A0A0A);
        ctx.renderOutline(px, py, W, H, 0xFF555555);

        ctx.drawString(font, title, px + (W - font.width(title)) / 2, py + 6, 0xCCCCCC);
        ctx.fill(px + 4, py + 18, px + W - 4, py + 19, 0xFF444444);

        int startIdx = page * 8;
        int endIdx = Math.min(startIdx + 8, cachedDesigns.size());
        int cols = 4;

        for (int i = startIdx; i < endIdx; i++) {
            SavedDesign d = cachedDesigns.get(i);
            int idx = i - startIdx;
            int col = idx % cols;
            int row = idx / cols;
            int tx = px + 10 + col * 78;
            int ty = py + 26 + row * 56;

            boolean selected = d == selectedDesign;
            ctx.fill(tx - 2, ty - 2, tx + THUMB_SIZE + 2, ty + THUMB_SIZE + 2, selected ? 0xFF00FF00 : 0xFF444444);

            renderThumbnail(ctx, d, tx, ty);

            String name = d.name();
            if (font.width(name) > 68) name = font.plainSubstrByWidth(name, 66) + "\u2026";
            ctx.drawString(font, Component.literal(name), tx + THUMB_SIZE / 2 - font.width(name) / 2, ty + THUMB_SIZE + 2, 0xAAAAAA);
        }

        if (maxPage > 0) {
            String pageStr = (page + 1) + "/" + (maxPage + 1);
            int pgX = px + W / 2 - font.width(pageStr) / 2;
            ctx.drawString(font, Component.literal(pageStr), pgX, py + H - 72, 0xAAAAAA);
            if (page > 0) ctx.drawString(font, Component.literal("<"), px + 10, py + H - 72, 0xCCCCCC);
            if (page < maxPage) ctx.drawString(font, Component.literal(">"), px + W - 20, py + H - 72, 0xCCCCCC);
        }

        ctx.fill(px + 4, py + H - 46, px + W - 4, py + H - 45, 0xFF444444);


        ctx.drawString(font, Component.literal("Name:"), px + 12, py + H - 64, 0x888888);
        if (selPos1 != null && selPos2 != null) {
            String selStr = "Selection: " + selPos1.toShortString() + " \u2192 " + selPos2.toShortString();
            ctx.drawString(font, Component.literal(selStr).withStyle(net.minecraft.ChatFormatting.GREEN), px + 165, py + H - 64, 0x55FF55);
            ctx.drawString(font, Component.literal("\u2713 Ready to save"), px + 165, py + H - 44, 0x55FF55);
        }

        super.render(ctx, mx, my, dt);
    }

    private void renderThumbnail(GuiGraphics ctx, SavedDesign d, int tx, int ty) {
        var blocks = d.blocks();
        if (blocks.isEmpty()) return;
        var faces = blocks.values().iterator().next();
        if (faces == null || faces.isEmpty()) return;
        int[][] grid = faces.values().iterator().next();
        if (grid == null) return;

        float scale = THUMB_SIZE / 16f;
        for (int u = 0; u < 16; u++) {
            for (int v = 0; v < 16; v++) {
                int color = grid[u][v];
                if (color == 0) continue;
                int sx = tx + (int)(u * scale);
                int sy = ty + (int)(v * scale);
                ctx.fill(sx, sy, sx + Math.max(1, (int)Math.ceil((u + 1) * scale) - (int)(u * scale)),
                        sy + Math.max(1, (int)Math.ceil((v + 1) * scale) - (int)(v * scale)), color);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int startIdx = page * 8;
            int endIdx = Math.min(startIdx + 8, cachedDesigns.size());

            for (int i = startIdx; i < endIdx; i++) {
                SavedDesign d = cachedDesigns.get(i);
                int idx = i - startIdx;
                int col = idx % 4;
                int row = idx / 4;
                int tx = px + 10 + col * 78;
                int ty = py + 26 + row * 56;

                if (mx >= tx - 2 && mx <= tx + THUMB_SIZE + 2 && my >= ty - 2 && my <= ty + THUMB_SIZE + 2) {
                    selectedDesign = d;
                    return true;
                }
            }

            if (page > 0 && mx >= px + 10 && mx <= px + 22 && my >= py + H - 72 && my <= py + H - 56) {
                page--;
                return true;
            }
            if (page < maxPage && mx >= px + W - 22 && mx <= px + W - 10 && my >= py + H - 72 && my <= py + H - 56) {
                page++;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (scrollY < 0 && page < maxPage) page++;
        else if (scrollY > 0 && page > 0) page--;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void onSave() {
        String name = nameField.getValue().trim();
        if (name.isEmpty() || selPos1 == null || selPos2 == null) return;
        PacketDistributor.sendToServer(new GallerySavePayload(name, selPos1, selPos2));
        selPos1 = null;
        selPos2 = null;
        onClose();
    }

    private void onPaste() {
        if (selectedDesign == null) return;
        previewDesign = selectedDesign;
        previewActive = true;
        previewYOffset = 0;
        onClose();
    }

    private void onDelete() {
        if (selectedDesign == null) return;
        PacketDistributor.sendToServer(new GalleryDeletePayload(selectedDesign.id()));
        cachedDesigns.remove(selectedDesign);
        selectedDesign = null;
        int totalDesigns = cachedDesigns.size();
        maxPage = Math.max(0, (totalDesigns - 1) / 8);
        if (page > maxPage) page = maxPage;
    }

    private void onSelect() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (held.is(GraffitiMod.GRAFFITI_TOOL.get())) {
            GraffitiItem.setToolMode(held, GraffitiItem.TOOL_SELECT);
        }
        onClose();
    }

    public static void receiveDesigns(List<SavedDesign> designs) {
        cachedDesigns = designs;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof GalleryScreen gs) {
            gs.maxPage = Math.max(0, (designs.size() - 1) / 8);
            if (gs.page > gs.maxPage) gs.page = gs.maxPage;
            gs.selectedDesign = null;
        }
    }
}
