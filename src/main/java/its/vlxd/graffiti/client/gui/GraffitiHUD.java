package its.vlxd.graffiti.client.gui;

import its.vlxd.graffiti.GraffitiMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.DeltaTracker;
import org.lwjgl.glfw.GLFW;

public class GraffitiHUD {
    private static final ResourceLocation BAR = ResourceLocation.parse("graffiti:textures/gui/bar.png");
    private static final ResourceLocation SELECTOR = ResourceLocation.parse("graffiti:textures/gui/selector.png");

    public static int selectedIndex = 0;
    private static float currentVisualX = 0;

    private static final float LERP_SPEED = 0.25f;
    private static float hudAlpha = 0.0f;
    private static final float FADE_SPEED = 0.1f;

    private static boolean lastA = false;
    private static boolean lastD = false;

    private static final Component[] TOOL_NAMES = {
            Component.translatable("hud.graffiti.tool.pencil"),
            Component.translatable("hud.graffiti.tool.eraser"),
            Component.translatable("hud.graffiti.tool.fill"),
            Component.translatable("hud.graffiti.tool.picker")
    };

    private static final String[] ICONS = {"✎", "✕", "█", "✀"};

    public static void init() {
    }

    public static void onClientTick() {
        var client = Minecraft.getInstance();
        if (client.player == null) return;

        boolean hasItem = client.player.getMainHandItem().is(GraffitiMod.GRAFFITI_TOOL.get());
        boolean isCtrlDown = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;

        if (hasItem && isCtrlDown) {
            var options = client.options;
            options.keyUp.setDown(false);
            options.keyDown.setDown(false);
            options.keyLeft.setDown(false);
            options.keyRight.setDown(false);
            options.keyJump.setDown(false);

            boolean isADown = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
            boolean isDDown = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;

            if (isADown && !lastA) {
                selectedIndex = (selectedIndex <= 0) ? 3 : selectedIndex - 1;
                playClickSound(client);
            }
            if (isDDown && !lastD) {
                selectedIndex = (selectedIndex >= 3) ? 0 : selectedIndex + 1;
                playClickSound(client);
            }

            lastA = isADown;
            lastD = isDDown;
        } else {
            lastA = false;
            lastD = false;
        }
    }

    public static void renderOverlay(GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;

        boolean hasItem = client.player.getMainHandItem().is(GraffitiMod.GRAFFITI_TOOL.get());
        boolean isCtrlDown = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;

        if (hasItem && isCtrlDown) hudAlpha = Math.min(1.0f, hudAlpha + FADE_SPEED);
        else hudAlpha = Math.max(0.0f, hudAlpha - FADE_SPEED);

        if (hudAlpha > 0) render(guiGraphics, client);
    }

    private static void playClickSound(Minecraft client) {
        if (client.player != null) {
            client.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(), 0.4f, 1.8f);
        }
    }

    private static void render(GuiGraphics context, Minecraft client) {
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();

        var pose = context.pose();
        pose.pushPose();
        float scale = 2.0f;
        pose.scale(scale, scale, 1.0f);

        int x = (int)((sw / 2f) / scale) - 32;
        int y = (int)(sh / scale) - 30;

        RenderSystem.enableBlend();
        context.setColor(1.0f, 1.0f, 1.0f, hudAlpha);

        context.blit(BAR, x, y, 0, 0, 64, 16, 64, 16);

        float targetX = x + (selectedIndex * 16);
        currentVisualX += (targetX - currentVisualX) * LERP_SPEED;
        context.blit(SELECTOR, (int)currentVisualX, y, 0, 0, 16, 16, 16, 16);

        for (int i = 0; i < 4; i++) {
            int tx = x + 8 + (i * 16);
            int alpha = (int)(hudAlpha * 255);
            int textColor = (alpha << 24) | 0xFFFFFF;
            context.drawCenteredString(client.font, ICONS[i], tx, y + 4, textColor);
        }

        context.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        pose.popPose();

        Component displayText = Component.literal("— ").append(TOOL_NAMES[selectedIndex]).append(" —");
        int alpha = (int)(hudAlpha * 255);
        int textColor = (alpha << 24) | 0xFFFFFF;
        context.drawCenteredString(client.font, displayText, sw / 2, sh - 75, textColor);
    }
}
