package its.vlxd.graffiti.client.renderer;

import its.vlxd.graffiti.GraffitiMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public class PixelOutlineRenderer {
    private static final float LINE_WIDTH = 3.0f;
    private static final float Z_OFFSET = 0.005f;

    public static void render(RenderLevelStageEvent context) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        boolean hasCan = client.player.getMainHandItem().is(GraffitiMod.GRAFFITI_TOOL) ||
                client.player.getOffhandItem().is(GraffitiMod.GRAFFITI_TOOL);

        if (!hasCan) return;

        var hit = client.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            drawSelection(context, blockHit);
        }
    }

    private static void drawSelection(RenderLevelStageEvent context, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Direction side = hit.getDirection();
        Vec3 localHit = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));

        float uRaw = 0, vRaw = 0, depth = 0;
        switch (side.getAxis()) {
            case Y -> { uRaw = (float) localHit.x; vRaw = (float) localHit.z; depth = (float) localHit.y; }
            case Z -> { uRaw = (float) localHit.x; vRaw = (float) localHit.y; depth = (float) localHit.z; }
            case X -> { uRaw = (float) localHit.z; vRaw = (float) localHit.y; depth = (float) localHit.x; }
        }

        int u = Math.max(0, Math.min(15, (int)(uRaw * 16)));
        int v = Math.max(0, Math.min(15, (int)(vRaw * 16)));

        renderPerfectLines(context, pos, side, u, v, depth);
    }

    private static void renderPerfectLines(RenderLevelStageEvent context, BlockPos pos, Direction side, int u, int v, float depth) {
        var poseStack = context.getPoseStack();
        var cameraPos = context.getCamera().getPosition();
        var client = Minecraft.getInstance();

        float time = (client.level.getGameTime() & 0xFFFF) + context.getPartialTick().getGameTimeDeltaTicks();

        poseStack.pushPose();
        poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);

        Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
        poseStack.translate(normal.x * Z_OFFSET, normal.y * Z_OFFSET, normal.z * Z_OFFSET);

        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.lineWidth(LINE_WIDTH);
        var bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        float s = 1/16f;
        float x1 = u * s, x2 = x1 + s;
        float y1 = v * s, y2 = y1 + s;

        drawLine(bufferBuilder, matrix, x1, y1, x2, y1, depth, side, time, 0);
        drawLine(bufferBuilder, matrix, x2, y1, x2, y2, depth, side, time, 1);
        drawLine(bufferBuilder, matrix, x2, y2, x1, y2, depth, side, time, 2);
        drawLine(bufferBuilder, matrix, x1, y2, x1, y1, depth, side, time, 3);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.lineWidth(1.0F);

        poseStack.popPose();
    }

    private static void drawLine(BufferBuilder b, Matrix4f m, float x1, float y1, float x2, float y2, float z, Direction side, float time, int offset) {
        float mix = (float) Math.sin(time + offset) * 0.5f + 0.5f;
        int r = (int) (255 * mix);
        int g = (int) (180 * (1 - mix));
        int bl = (int) (50 * mix + 255 * (1 - mix));

        addVertex(b, m, x1, y1, z, side, r, g, bl);
        addVertex(b, m, x2, y2, z, side, r, g, bl);
    }

    private static void addVertex(BufferBuilder b, Matrix4f m, float x, float y, float z, Direction side, int r, int g, int bl) {
        switch (side.getAxis()) {
            case Y -> b.addVertex(m, x, z, y).setColor(r, g, bl, 255);
            case Z -> b.addVertex(m, x, y, z).setColor(r, g, bl, 255);
            case X -> b.addVertex(m, z, y, x).setColor(r, g, bl, 255);
        }
    }
}
