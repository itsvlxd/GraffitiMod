package its.vlxd.graffiti.client;

import its.vlxd.graffiti.GraffitiMod;
import its.vlxd.graffiti.client.gui.GraffitiHUD;
import its.vlxd.graffiti.client.gui.GraffitiScreen;
import its.vlxd.graffiti.client.renderer.GraffitiRenderer;
import its.vlxd.graffiti.client.renderer.PixelOutlineRenderer;
import its.vlxd.graffiti.config.GraffitiConfig;
import its.vlxd.graffiti.item.GraffitiItem;
import its.vlxd.graffiti.network.PaintPayload;
import its.vlxd.graffiti.network.SyncGraffitiPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = GraffitiMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientHandler {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        GraffitiConfig.load();
        GraffitiHUD.init();
        GraffitiRenderer.init();

        NeoForge.EVENT_BUS.addListener(ClientHandler::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ClientHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientHandler::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(ClientHandler::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(ClientHandler::onRenderGui);

        ClientPlayConnection.JOIN.register(() -> clearClientCache());
        ClientPlayConnection.DISCONNECT.register(() -> clearClientCache());
    }

    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(GraffitiMod.MOD_ID);

        registrar.playToClient(
                PaintPayload.TYPE,
                PaintPayload.CODEC,
                (payload, context) -> handlePaintPacket(payload, context)
        );

        registrar.playToClient(
                SyncGraffitiPayload.TYPE,
                SyncGraffitiPayload.CODEC,
                (payload, context) -> handleSyncPacket(payload, context)
        );
    }

    @SubscribeEvent
    public static void onRegisterColorHandlers(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                int itemColor = GraffitiItem.getColor(stack);
                return 0xFF000000 | (itemColor & 0xFFFFFF);
            }
            return 0xFFFFFFFF;
        }, GraffitiMod.GRAFFITI_TOOL);
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
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        GraffitiHUD.renderOverlay(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaTicks(), client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide) return;
        var player = event.getEntity();
        if (player == null) return;
        if (!player.getMainHandItem().is(GraffitiMod.GRAFFITI_TOOL)) return;

        var client = Minecraft.getInstance();
        if (client.hitResult instanceof BlockHitResult hit) {
            ClientItemHandler.handleAttack(hit);
            event.setCanceled(true);
        }
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide) return;
        var player = event.getEntity();
        if (player == null) return;
        if (!player.getMainHandItem().is(GraffitiMod.GRAFFITI_TOOL)) return;

        ClientItemHandler.openScreen(player.getMainHandItem());
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
