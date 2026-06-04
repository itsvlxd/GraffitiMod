package its.vlxd.graffiti.client;

import its.vlxd.graffiti.client.gui.GraffitiHUD;
import its.vlxd.graffiti.client.gui.GraffitiScreen;
import its.vlxd.graffiti.client.renderer.GraffitiRenderer;
import its.vlxd.graffiti.item.GraffitiItem;
import its.vlxd.graffiti.network.ColorPayload;
import its.vlxd.graffiti.network.PaintPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

public class ClientItemHandler {
    public static void handleAttack(BlockHitResult hit) {
        var client = Minecraft.getInstance();
        if (client.player != null) {
            BlockPos pos = hit.getBlockPos();
            double reach = client.player.blockInteractionRange();
            if (client.player.distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach) return;

            ItemStack stack = client.player.getMainHandItem();
            int itemColor = GraffitiItem.getColor(stack);

            Direction side = hit.getDirection();
            Vec3 r = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));

            int u = Math.min(15, Math.max(0, getCoord(r, side, true)));
            int v = Math.min(15, Math.max(0, getCoord(r, side, false)));

            int toolIndex = GraffitiHUD.selectedIndex;

            switch (toolIndex) {
                case 0 -> {
                    draw(pos, side, u, v, itemColor);
                    client.level.playSound(client.player, pos, net.minecraft.sounds.SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, 0.5f, 1.5f);
                }
                case 1 -> {
                    draw(pos, side, u, v, 0);
                    client.level.playSound(client.player, pos, net.minecraft.sounds.SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, 0.4f, 0.8f);
                }
                case 2 -> {
                    floodFill(pos, side, u, v, itemColor);
                    client.level.playSound(client.player, pos, net.minecraft.sounds.SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.PLAYERS, 0.7f, 1.2f);
                }
                case 3 -> {
                    pickColor(pos, side, u, v, stack);
                    client.level.playSound(client.player, pos, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8f, 1.0f);
                }
            }
        }
    }

    public static void openScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new GraffitiScreen(stack));
    }

    private static void pickColor(BlockPos pos, Direction side, int u, int v, ItemStack stack) {
        int newColor = getPixelAt(pos, side, u, v);

        if (newColor == 0) {
            var client = Minecraft.getInstance();
            int blockColor = client.getBlockColors().getColor(client.level.getBlockState(pos), client.level, pos, 0);
            if (blockColor == -1) {
                blockColor = client.level.getBlockState(pos).getMapColor(client.level, pos).col;
            }
            newColor = 0xFF000000 | blockColor;
        }

        if (newColor != 0) {
            GraffitiItem.setColor(stack, newColor);
            PacketDistributor.sendToServer(new ColorPayload(newColor));
        }
    }

    private static void draw(BlockPos pos, Direction side, int uC, int vC, int color) {
        var client = Minecraft.getInstance();
        if (client.player == null) return;
        ItemStack held = client.player.getMainHandItem();
        int rad = GraffitiItem.getBrushSize(held) - 1;
        int shape = GraffitiItem.getBrushShape(held);
        Random rng = new Random();
        int extraY = (shape == GraffitiItem.SHAPE_LEAKY) ? 3 : 0;

        for (int x = -rad; x <= rad; x++) {
            for (int y = -rad; y <= rad + extraY; y++) {
                int u = uC + x, v = vC + y;
                if (u < 0 || u >= 16 || v < 0 || v >= 16) continue;

                if (shouldPaint(x, y, rad, shape, rng)) {
                    setPixel(pos, side, u, v, color);
                }
            }
        }
    }

    private static boolean shouldPaint(int x, int y, int rad, int shape, Random rng) {
        return switch (shape) {
            case GraffitiItem.SHAPE_SQUARE -> true;
            case GraffitiItem.SHAPE_CIRCLE -> x * x + y * y <= rad * rad;
            case GraffitiItem.SHAPE_ROUNDED -> isRounded(x, y, rad);
            case GraffitiItem.SHAPE_CLOUD -> {
                float dist = (float)(x * x + y * y) / (float)(rad * rad);
                yield rng.nextFloat() > dist * 0.7f;
            }
            case GraffitiItem.SHAPE_LEAKY -> {
                if (isRounded(x, y, rad)) yield true;
                if (y > rad) {
                    Random dripRng = new Random(x * 31 + rad * 7);
                    if (dripRng.nextFloat() < 0.3f && Math.abs(x) <= rad * 0.65f) {
                        int dripLen = 1 + dripRng.nextInt(3);
                        yield y <= rad + dripLen;
                    }
                }
                yield false;
            }
            default -> true;
        };
    }

    private static boolean isRounded(int x, int y, int rad) {
        int cr = Math.max(1, rad / 2);
        if (Math.abs(x) <= rad - cr || Math.abs(y) <= rad - cr) return true;
        int cx = Math.abs(x) - (rad - cr);
        int cy = Math.abs(y) - (rad - cr);
        return cx * cx + cy * cy <= cr * cr;
    }

    private static void floodFill(BlockPos pos, Direction side, int startU, int startV, int targetColor) {
        int startColor = getPixelAt(pos, side, startU, startV);
        if (startColor == targetColor) return;

        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{startU, startV});
        boolean[][] visited = new boolean[16][16];

        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            int u = curr[0], v = curr[1];
            if (u < 0 || u >= 16 || v < 0 || v >= 16 || visited[u][v]) continue;
            if (getPixelAt(pos, side, u, v) != startColor) continue;

            visited[u][v] = true;
            setPixel(pos, side, u, v, targetColor);

            queue.add(new int[]{u + 1, v});
            queue.add(new int[]{u - 1, v});
            queue.add(new int[]{u, v + 1});
            queue.add(new int[]{u, v - 1});
        }
    }

    private static int getPixelAt(BlockPos pos, Direction side, int u, int v) {
        var sides = GraffitiRenderer.getBlockFaces(pos);
        if (sides != null) {
            int[][] grid = sides.get(side);
            if (grid != null) return grid[u][v];
        }
        synchronized (GraffitiRenderer.PIXELS) {
            for (PaintPayload p : GraffitiRenderer.PIXELS) {
                if (p.pos().equals(pos) && p.side() == side && p.u() == u && p.v() == v) return p.color();
            }
        }
        return 0;
    }

    private static void setPixel(BlockPos pos, Direction side, int u, int v, int color) {
        PaintPayload p = new PaintPayload(pos, side, u, v, color, 1);
        GraffitiRenderer.addPixelToCache(p);
        PacketDistributor.sendToServer(p);
    }

    public static int getCoord(Vec3 r, Direction s, boolean isU) {
        if (isU) {
            return (int) (switch (s) {
                case UP, DOWN, NORTH, SOUTH -> r.x;
                case WEST, EAST -> r.z;
            } * 16);
        } else {
            return (int) (switch (s) {
                case UP, DOWN -> r.z;
                default -> r.y;
            } * 16);
        }
    }
}
