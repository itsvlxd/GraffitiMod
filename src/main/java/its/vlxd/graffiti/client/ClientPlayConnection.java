package its.vlxd.graffiti.client;

import its.vlxd.graffiti.GraffitiMod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

public class ClientPlayConnection {
    private static final List<Runnable> joinHandlers = new ArrayList<>();
    private static final List<Runnable> disconnectHandlers = new ArrayList<>();
    private static boolean initialized = false;

    public static void register(Runnable onJoin, Runnable onDisconnect) {
        joinHandlers.add(onJoin);
        disconnectHandlers.add(onDisconnect);
        ensureInit();
    }

    public static class JOIN {
        public static void register(Runnable handler) {
            joinHandlers.add(handler);
            ensureInit();
        }
    }

    public static class DISCONNECT {
        public static void register(Runnable handler) {
            disconnectHandlers.add(handler);
            ensureInit();
        }
    }

    private static void ensureInit() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            GraffitiMod.LOGGER.info("Client connected, clearing graffiti cache");
            joinHandlers.forEach(Runnable::run);
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            GraffitiMod.LOGGER.info("Client disconnected, clearing graffiti cache");
            disconnectHandlers.forEach(Runnable::run);
        });
    }
}
