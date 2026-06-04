package its.vlxd.graffiti.client;

import its.vlxd.graffiti.GraffitiMod;
import its.vlxd.graffiti.client.gui.GraffitiHUD;
import its.vlxd.graffiti.client.renderer.GraffitiRenderer;
import its.vlxd.graffiti.config.GraffitiConfig;
import its.vlxd.graffiti.item.GraffitiItem;
import its.vlxd.graffiti.network.CleanPayload;
import its.vlxd.graffiti.network.FaceSyncPayload;
import its.vlxd.graffiti.network.PaintPayload;
import its.vlxd.graffiti.network.RemoveGraffitiPayload;
import its.vlxd.graffiti.network.SnapshotPayload;
import its.vlxd.graffiti.network.SyncGraffitiPayload;
import its.vlxd.graffiti.network.SprayEquipPayload;
import its.vlxd.graffiti.network.SprayPaintPayload;
import its.vlxd.graffiti.network.UndoPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Objects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = GraffitiMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientHandler {
    private static boolean lastC = false;
    private static boolean lastZ = false;
    private static boolean lastY = false;

    private static BlockPos lastPaintPos = null;
    private static Direction lastPaintSide = null;
    private static int lastPaintU = -1;
    private static int lastPaintV = -1;

    private static BlockPos lastLookPos = null;
    private static Direction lastLookSide = null;

    private static ItemStack lastHeldItem = ItemStack.EMPTY;
    private static boolean lastRightDown = false;
    private static SoundInstance paintLoop = null;



    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        GraffitiConfig.load();
        GraffitiHUD.init();
        GraffitiRenderer.init();

        NeoForge.EVENT_BUS.addListener(ClientHandler::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ClientHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientHandler::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(ClientHandler::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(ClientHandler::onRenderGui);

        ClientPlayConnection.JOIN.register(() -> clearClientCache());
        ClientPlayConnection.DISCONNECT.register(() -> clearClientCache());
    }

    @SubscribeEvent
    public static void onRegisterColorHandlers(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                int itemColor = GraffitiItem.getColor(stack);
                return 0xFF000000 | (itemColor & 0xFFFFFF);
            }
            return 0xFFFFFFFF;
        }, GraffitiMod.GRAFFITI_TOOL.get());
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            GraffitiRenderer.render(event);
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        GraffitiHUD.onClientTick();

        var client = Minecraft.getInstance();
        if (client.player == null) return;

        ItemStack held = client.player.getMainHandItem();
        if (!ItemStack.matches(held, lastHeldItem)) {
            if (held.is(GraffitiMod.GRAFFITI_TOOL.get())) {
                PacketDistributor.sendToServer(new SprayEquipPayload());
            }
            lastHeldItem = held.copy();
        }

        boolean hasItem = held.is(GraffitiMod.GRAFFITI_TOOL.get());
        boolean isCKey = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
        boolean isZKey = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
        boolean isYKey = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_Y) == GLFW.GLFW_PRESS;

        if (hasItem && isCKey && !lastC) {
            ClientItemHandler.openScreen(client.player.getMainHandItem());
        }
        lastC = isCKey;

        if (hasItem && isZKey && !lastZ && client.hitResult instanceof BlockHitResult zh) {
            PacketDistributor.sendToServer(new UndoPayload(zh.getBlockPos(), zh.getDirection(), false));
        }
        lastZ = isZKey;

        if (hasItem && isYKey && !lastY && client.hitResult instanceof BlockHitResult yh) {
            PacketDistributor.sendToServer(new UndoPayload(yh.getBlockPos(), yh.getDirection(), true));
        }
        lastY = isYKey;

        if (hasItem) {
            BlockPos currentLookPos = null;
            Direction currentLookSide = null;
            if (client.hitResult instanceof BlockHitResult lh) {
                currentLookPos = lh.getBlockPos();
                currentLookSide = lh.getDirection();
            }

            if (!Objects.equals(currentLookPos, lastLookPos) || currentLookSide != lastLookSide) {
                if (lastLookPos != null && lastLookSide != null
                        && lastLookPos.equals(lastPaintPos) && lastLookSide == lastPaintSide) {
                    PacketDistributor.sendToServer(new SnapshotPayload(lastLookPos, lastLookSide));
                }
                lastLookPos = currentLookPos;
                lastLookSide = currentLookSide;
            }
        } else {
            lastLookPos = null;
            lastLookSide = null;
        }

        if (hasItem) {
            int color = GraffitiItem.getColor(client.player.getMainHandItem());
            String hex = String.format("#%06X", color & 0xFFFFFF);

            Component msg = Component.literal("Color: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(hex).withStyle(s -> s.withColor(color & 0xFFFFFF)))
                    .append(Component.literal("  Mode: ").withStyle(ChatFormatting.GRAY))
                    .append(GraffitiHUD.getColoredToolName());

            client.player.displayClientMessage(msg, true);
        }
    }

    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        var client = Minecraft.getInstance();
        if (client.player == null) return;
        if (client.screen != null) return;

        ItemStack held = client.player.getMainHandItem();
        boolean isGraffitiTool = held.is(GraffitiMod.GRAFFITI_TOOL.get());
        boolean isBrush = held.is(GraffitiMod.BRUSH.get()) || held.is(GraffitiMod.WET_BRUSH.get());
        if (!isGraffitiTool && !isBrush) {
            if (paintLoop != null) {
                client.getSoundManager().stop(paintLoop);
                paintLoop = null;
            }
            lastRightDown = false;
            return;
        }

        boolean rightDown = GLFW.glfwGetMouseButton(client.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (isGraffitiTool && rightDown && !lastRightDown && client.hitResult instanceof BlockHitResult hit) {
            BlockPos pos = hit.getBlockPos();
            paintLoop = new SpraySoundInstance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            client.getSoundManager().play(paintLoop);
            PacketDistributor.sendToServer(new SprayPaintPayload(pos));
        }

        if (isGraffitiTool && !rightDown && lastRightDown) {
            if (paintLoop != null) {
                client.getSoundManager().stop(paintLoop);
                paintLoop = null;
            }
        }

        lastRightDown = rightDown;

        if (!rightDown) return;
        if (!(client.hitResult instanceof BlockHitResult hit)) return;

        BlockPos pos = hit.getBlockPos();
        Direction side = hit.getDirection();
        Vec3 r = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        int u = Math.min(15, Math.max(0, ClientItemHandler.getCoord(r, side, true)));
        int v = Math.min(15, Math.max(0, ClientItemHandler.getCoord(r, side, false)));

        if (pos.equals(lastPaintPos) && side == lastPaintSide && u == lastPaintU && v == lastPaintV) return;
        lastPaintPos = pos;
        lastPaintSide = side;
        lastPaintU = u;
        lastPaintV = v;

        if (isBrush) {
            PacketDistributor.sendToServer(new CleanPayload(pos, side, u, v, 3));

            long ck = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            var chunk = GraffitiRenderer.GRAFFITI_CACHE.get(ck);
            if (chunk != null) {
                var faces = chunk.get(pos.asLong());
                if (faces != null) {
                    int[][] grid = faces.get(side);
                    if (grid != null) {
                        int alphaReduction = held.is(GraffitiMod.WET_BRUSH.get()) ? 51 : 16;
                        for (int du = -3; du <= 3; du++) {
                            for (int dv = -3; dv <= 3; dv++) {
                                if (du * du + dv * dv > 9) continue;
                                int tu = u + du, tv = v + dv;
                                if (tu < 0 || tu >= 16 || tv < 0 || tv >= 16) continue;
                                int color = grid[tu][tv];
                                if (color == 0) continue;
                                int alpha = (color >> 24) & 0xFF;
                                alpha -= alphaReduction;
                                grid[tu][tv] = alpha <= 0 ? 0 : (alpha << 24) | (color & 0xFFFFFF);
                            }
                        }
                    }
                }
            }
        } else {
            ClientItemHandler.handleAttack(hit);
        }
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        GraffitiHUD.renderOverlay(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaTicks(), client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        var client = Minecraft.getInstance();
        if (client.player == null) return;

        boolean hasItem = client.player.getMainHandItem().is(GraffitiMod.GRAFFITI_TOOL.get());
        boolean isCtrlDown = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;

        if (hasItem && isCtrlDown) {
            GraffitiHUD.switchTool(event.getScrollDeltaY() > 0 ? 1 : -1);
            event.setCanceled(true);
        }
    }

    private static void clearClientCache() {
        if (GraffitiRenderer.GRAFFITI_CACHE != null) {
            GraffitiRenderer.GRAFFITI_CACHE.clear();
        }
        if (GraffitiRenderer.PIXELS != null) {
            GraffitiRenderer.PIXELS.clear();
        }
    }

    public static void handlePaintPacket(PaintPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            GraffitiRenderer.addPixelToCache(payload);
        });
    }

    public static void handleRemoveGraffiti(RemoveGraffitiPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            GraffitiRenderer.removeBlockFaces(payload.pos());
        });
    }

    public static void handleFaceSync(FaceSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            int[][] grid = new int[16][16];
            for (int i = 0; i < 256; i++) {
                grid[i / 16][i % 16] = payload.flatGrid()[i];
            }
            long ck = ChunkPos.asLong(payload.pos().getX() >> 4, payload.pos().getZ() >> 4);
            GraffitiRenderer.GRAFFITI_CACHE
                    .computeIfAbsent(ck, k -> new HashMap<>())
                    .computeIfAbsent(payload.pos().asLong(), k -> new EnumMap<>(Direction.class))
                    .put(payload.side(), grid);
        });
    }

    public static void handleSyncPacket(SyncGraffitiPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            GraffitiRenderer.GRAFFITI_CACHE.clear();
            for (PaintPayload p : payload.allPixels()) {
                GraffitiRenderer.addPixelToCache(p);
            }
            GraffitiMod.LOGGER.info("Loaded {} pixels", payload.allPixels().size());
        });
    }
}
