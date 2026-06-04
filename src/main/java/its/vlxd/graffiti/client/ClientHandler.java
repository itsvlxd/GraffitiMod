package its.vlxd.graffiti.client;

import its.vlxd.graffiti.GraffitiMod;
import its.vlxd.graffiti.client.gui.GraffitiHUD;
import its.vlxd.graffiti.client.renderer.GraffitiRenderer;
import its.vlxd.graffiti.client.renderer.PixelOutlineRenderer;
import its.vlxd.graffiti.config.GraffitiConfig;
import its.vlxd.graffiti.item.GraffitiItem;
import its.vlxd.graffiti.network.PaintPayload;
import its.vlxd.graffiti.network.SyncGraffitiPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = GraffitiMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientHandler {
    private static boolean lastC = false;

    private static BlockPos lastPaintPos = null;
    private static Direction lastPaintSide = null;
    private static int lastPaintU = -1;
    private static int lastPaintV = -1;

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
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            PixelOutlineRenderer.render(event);
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        GraffitiHUD.onClientTick();

        var client = Minecraft.getInstance();
        if (client.player == null) return;

        boolean hasItem = client.player.getMainHandItem().is(GraffitiMod.GRAFFITI_TOOL.get());
        boolean isCKey = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;

        if (hasItem && isCKey && !lastC) {
            ClientItemHandler.openScreen(client.player.getMainHandItem());
        }
        lastC = isCKey;
    }

    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        var client = Minecraft.getInstance();
        if (client.player == null) return;
        if (client.screen != null) return;

        if (!client.player.getMainHandItem().is(GraffitiMod.GRAFFITI_TOOL.get())) return;

        if (GLFW.glfwGetMouseButton(client.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS) return;
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

        ClientItemHandler.handleAttack(hit);
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
