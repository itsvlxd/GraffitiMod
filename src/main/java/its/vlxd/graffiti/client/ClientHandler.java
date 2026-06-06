package its.vlxd.graffiti.client;

import its.vlxd.graffiti.GraffitiMod;
import its.vlxd.graffiti.client.gui.BrushScreen;
import its.vlxd.graffiti.client.gui.GalleryScreen;
import its.vlxd.graffiti.client.gui.GraffitiHUD;
import its.vlxd.graffiti.client.renderer.GraffitiRenderer;
import its.vlxd.graffiti.config.GraffitiConfig;
import its.vlxd.graffiti.item.BrushItem;
import its.vlxd.graffiti.item.GraffitiItem;
import its.vlxd.graffiti.network.CleanPayload;
import its.vlxd.graffiti.network.DebugPayload;
import its.vlxd.graffiti.network.FaceSyncPayload;
import its.vlxd.graffiti.network.GalleryListPayload;
import its.vlxd.graffiti.network.GalleryPastePayload;
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
import java.util.ArrayList;
import java.util.Objects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
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
    private static boolean lastG = false;

    private static BlockPos lastPaintPos = null;
    private static Direction lastPaintSide = null;
    private static int lastPaintU = -1;
    private static int lastPaintV = -1;

    private static BlockPos lastLookPos = null;
    private static Direction lastLookSide = null;

    private static ItemStack lastHeldItem = ItemStack.EMPTY;
    private static boolean lastRightDown = false;
    private static int sprayPaintTicks = 0;
    private static SoundInstance paintLoop = null;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        GraffitiConfig.load();
        GraffitiHUD.init();
        GraffitiRenderer.init();

        NeoForge.EVENT_BUS.addListener(ClientHandler::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ClientHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientHandler::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(ClientHandler::onRenderGui);
        NeoForge.EVENT_BUS.addListener(ClientHandler::onMouseScroll);

        ClientPlayConnection.JOIN.register(() -> {
            GraffitiRenderer.resetServerSyncFlag();
            clearClientCache();
        });
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
        GraffitiHUD.init();

        var client = Minecraft.getInstance();
        if (client.player == null) return;

        ItemStack held = client.player.getMainHandItem();
        if (held.getItem() != lastHeldItem.getItem()) {
            if (held.is(GraffitiMod.GRAFFITI_TOOL.get())) {
                PacketDistributor.sendToServer(new SprayEquipPayload());
            }
            lastHeldItem = held.copy();
        }

        // Preview mode handling
        if (GalleryScreen.previewActive) {
            if (GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
                GalleryScreen.previewActive = false;
                GalleryScreen.previewDesign = null;
                GalleryScreen.previewYOffset = 0;
            } else if (GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS && !lastG) {
                lastG = true;
                GalleryScreen.previewActive = false;
                GalleryScreen.previewDesign = null;
                GalleryScreen.previewYOffset = 0;
                if (held.is(GraffitiMod.GRAFFITI_TOOL.get())) {
                    client.setScreen(new GalleryScreen());
                }
            } else if (GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_PAGE_UP) == GLFW.GLFW_PRESS) {
                GalleryScreen.previewYOffset++;
            } else if (GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_PAGE_DOWN) == GLFW.GLFW_PRESS) {
                GalleryScreen.previewYOffset--;
            }
            client.player.displayClientMessage(
                    Component.literal("§7Preview — §ePgUp/PgDn §7move Y, §eRight-click §7to paste, §eESC §7to cancel"),
                    true);
            return; // block all other tick processing while previewing
        }

        boolean hasItem = held.is(GraffitiMod.GRAFFITI_TOOL.get());
        boolean isCKey = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
        boolean isZKey = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
        boolean isYKey = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_Y) == GLFW.GLFW_PRESS;
        boolean isGKey = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;

        if (client.screen == null && isCKey && !lastC) {
            if (held.is(GraffitiMod.GRAFFITI_TOOL.get())) {
                ClientItemHandler.openScreen(held);
            } else if (held.is(GraffitiMod.BRUSH.get()) || held.is(GraffitiMod.WET_BRUSH.get())) {
                Minecraft.getInstance().setScreen(new BrushScreen(held));
            }
        }
        lastC = isCKey;

        if (client.screen == null && isGKey && !lastG) {
            if (held.is(GraffitiMod.GRAFFITI_TOOL.get())) {
                client.setScreen(new GalleryScreen());
            }
        }
        lastG = isGKey;

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
            int mode = GraffitiItem.getToolMode(client.player.getMainHandItem());
            if (mode == GraffitiItem.TOOL_SELECT) {
                Component selMsg;
                if (GalleryScreen.selPos1 != null && GalleryScreen.selPos2 != null) {
                    selMsg = Component.literal("\u00a7aSelect mode \u00a77- \u00a7aPos1 "
                            + GalleryScreen.selPos1.toShortString() + " \u00a77\u2192 \u00a7aPos2 "
                            + GalleryScreen.selPos2.toShortString() + " \u00a77Press G to save");
                } else if (GalleryScreen.selPos1 != null) {
                    selMsg = Component.literal("\u00a7aSelect mode \u00a77- \u00a7eRight-click a second block");
                } else {
                    selMsg = Component.literal("\u00a7aSelect mode \u00a77- \u00a7eRight-click to set first corner");
                }
                client.player.displayClientMessage(selMsg, true);
            } else {
                int color = GraffitiItem.getColor(client.player.getMainHandItem());
                String hex = String.format("#%06X", color & 0xFFFFFF);
                int shape = GraffitiItem.getBrushShape(client.player.getMainHandItem());
                String shapeName = GraffitiItem.getShapeName(shape);

                Component msg = Component.literal("Color: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(hex).withStyle(s -> s.withColor(color & 0xFFFFFF)))
                        .append(Component.literal("  Shape: ").withStyle(ChatFormatting.GRAY))
                        .append(GraffitiHUD.getColoredShapeName(shapeName, shape))
                        .append(Component.literal("  Mode: ").withStyle(ChatFormatting.GRAY))
                        .append(GraffitiHUD.getColoredToolName());

                client.player.displayClientMessage(msg, true);
            }
        }

        boolean isBrush = held.is(GraffitiMod.BRUSH.get()) || held.is(GraffitiMod.WET_BRUSH.get());
        if (!hasItem && isBrush) {
            int size = BrushItem.getSize(client.player.getMainHandItem());
            int shape = BrushItem.getShape(client.player.getMainHandItem());
            String shapeName = BrushItem.getShapeName(shape);

            Component msg = Component.literal("Size: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(size)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  Shape: ").withStyle(ChatFormatting.GRAY))
                    .append(GraffitiHUD.getColoredShapeName(shapeName, shape));

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

        // Select mode: right-click sets selection corners
        if (isGraffitiTool && !GalleryScreen.previewActive && GraffitiItem.getToolMode(held) == GraffitiItem.TOOL_SELECT
                && rightDown && !lastRightDown && client.hitResult instanceof BlockHitResult selHit) {
            BlockPos hitPos = selHit.getBlockPos();
            if (GalleryScreen.selPos1 == null || (GalleryScreen.selPos2 != null)) {
                GalleryScreen.selPos1 = hitPos;
                GalleryScreen.selPos2 = null;
                client.player.displayClientMessage(Component.literal("§7Position 1: §a" + hitPos.toShortString()), true);
            } else {
                GalleryScreen.selPos2 = hitPos;
                client.player.displayClientMessage(Component.literal("§7Position 2: §a" + hitPos.toShortString()), true);
            }
            lastRightDown = rightDown;
            return;
        }

        // Select mode: skip all paint handling on held right-click
        if (isGraffitiTool && GraffitiItem.getToolMode(held) == GraffitiItem.TOOL_SELECT) {
            lastRightDown = rightDown;
            return;
        }

        // Preview mode paste on right-click
        if (GalleryScreen.previewActive && rightDown && !lastRightDown && client.hitResult instanceof BlockHitResult previewHit) {
            BlockPos adjustedPos = previewHit.getBlockPos().offset(0, GalleryScreen.previewYOffset, 0);
            PacketDistributor.sendToServer(new GalleryPastePayload(
                    GalleryScreen.previewDesign.id(), adjustedPos, previewHit.getDirection()));
            GalleryScreen.previewActive = false;
            GalleryScreen.previewDesign = null;
            GalleryScreen.previewYOffset = 0;
            lastRightDown = rightDown;
            return;
        }

        if (isGraffitiTool && rightDown && !lastRightDown && client.hitResult instanceof BlockHitResult hit) {
            if (!client.player.isCreative() && held.getDamageValue() >= held.getMaxDamage()) {
                lastRightDown = rightDown;
                return;
            }
            BlockPos pos = hit.getBlockPos();
            paintLoop = new SpraySoundInstance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            client.getSoundManager().play(paintLoop);
            PacketDistributor.sendToServer(new SprayPaintPayload(pos));
            sprayPaintTicks = 0;
        }

        if (isGraffitiTool && rightDown) {
            sprayPaintTicks++;
            if (sprayPaintTicks >= 10 && client.hitResult instanceof BlockHitResult hit) {
                sprayPaintTicks = 0;
                PacketDistributor.sendToServer(new SprayPaintPayload(hit.getBlockPos()));
            }
        } else {
            sprayPaintTicks = 0;
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
            if (!client.player.isCreative() && held.is(GraffitiMod.WET_BRUSH.get()) && held.getDamageValue() >= held.getMaxDamage()) return;
            int brushRad = BrushItem.getSize(held) - 1;
            int shape = BrushItem.getShape(held);
            PacketDistributor.sendToServer(new CleanPayload(pos, side, u, v, brushRad, shape));
        } else {
            ClientItemHandler.handleAttack(hit);
        }
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {}

    private static void clearClientCache() {
        if (GraffitiRenderer.GRAFFITI_CACHE != null) {
            GraffitiRenderer.GRAFFITI_CACHE.clear();
        }
        if (GraffitiRenderer.BAKED_CACHE != null) {
            GraffitiRenderer.BAKED_CACHE.clear();
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
            String dim = GraffitiRenderer.currentDim();
            int[][] grid = new int[16][16];
            for (int i = 0; i < 256; i++) {
                grid[i / 16][i % 16] = payload.flatGrid()[i];
            }
            long ck = ChunkPos.asLong(payload.pos().getX() >> 4, payload.pos().getZ() >> 4);
            GraffitiRenderer.GRAFFITI_CACHE
                    .computeIfAbsent(dim, k -> new java.util.concurrent.ConcurrentHashMap<>())
                    .computeIfAbsent(ck, k -> new HashMap<>())
                    .computeIfAbsent(payload.pos().asLong(), k -> new EnumMap<>(Direction.class))
                    .put(payload.side(), grid);

            GraffitiRenderer.invalidateFace(dim, ck, payload.pos().asLong(), payload.side());
        });
    }

    public static void handleSyncPacket(SyncGraffitiPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            GraffitiRenderer.GRAFFITI_CACHE.clear();
            GraffitiRenderer.BAKED_CACHE.clear();
            synchronized (GraffitiRenderer.PIXELS) {
                GraffitiRenderer.PIXELS.clear();
            }
            for (PaintPayload p : payload.allPixels()) {
                GraffitiRenderer.addPixelToCache(p);
            }
            GraffitiRenderer.markServerSyncReceived();
            GraffitiMod.LOGGER.info("Loaded {} pixels", payload.allPixels().size());
        });
    }

    public static void handleDebugPacket(DebugPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            GraffitiRenderer.setDebugMode(payload.enabled());
        });
    }

    public static void handleGalleryList(GalleryListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            GalleryScreen.receiveDesigns(payload.designs());
        });
    }

    public static void onMouseScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
        if (GalleryScreen.previewActive) {
            GalleryScreen.previewYOffset += (int)Math.signum(event.getScrollDeltaY());
            event.setCanceled(true);
        }
    }
}
