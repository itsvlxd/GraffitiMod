package its.vlxd.graffiti.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import its.vlxd.graffiti.GraffitiMod;
import its.vlxd.graffiti.network.DebugPayload;
import its.vlxd.graffiti.network.SyncGraffitiPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class GraffitiCommands {
    private static final String PREFIX = "§6§lGraffitiMod §8§l┃ §7";
    private static final Map<UUID, Long> PENDING_CONFIRMS = new HashMap<>();
    private static final long CONFIRM_TIMEOUT = 30_000;
    private static boolean debugEnabled = false;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("graffiti")
                .then(Commands.literal("version")
                        .executes(GraffitiCommands::version))
                .then(Commands.literal("debug")
                        .requires(src -> src.hasPermission(2))
                        .executes(GraffitiCommands::debug))
                .then(Commands.literal("clean")
                        .requires(src -> src.hasPermission(2))
                        .executes(GraffitiCommands::clean))
                .then(Commands.literal("confirm")
                        .requires(src -> src.hasPermission(2))
                        .executes(GraffitiCommands::confirm))
        );
    }

    private static int version(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();

        source.sendSuccess(() -> Component.literal(PREFIX)
                .append(Component.literal("A creative mod that allows players to paint on any block surface with customizable colors and tools.")
                        .withStyle(ChatFormatting.GRAY)), false);

        String version = net.neoforged.fml.ModList.get().getModContainerById("graffiti")
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");

        source.sendSuccess(() -> Component.literal(PREFIX)
                .append(Component.literal("Version: ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(version)
                        .withStyle(ChatFormatting.WHITE)), false);

        source.sendSuccess(() -> Component.literal(PREFIX)
                .append(Component.literal("Author: ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("@itsvlxd")
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.GOLD)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/itsvlxd/GraffitiMod"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§7Click to open §eGitHub§7!")))
                        )), false);

        return 1;
    }

    private static int debug(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) return 0;

        debugEnabled = !debugEnabled;

        var server = player.getServer();
        if (server != null) {
            PacketDistributor.sendToPlayer((ServerPlayer) player, new DebugPayload(debugEnabled));
        }

        source.sendSuccess(() -> Component.literal(PREFIX)
                .append(Component.literal("Debug visualization ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(debugEnabled ? "ENABLED" : "DISABLED")
                        .withStyle(debugEnabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);

        return 1;
    }

    private static int clean(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) return 0;

        if (GraffitiMod.SERVER_CACHE.isEmpty()) {
            source.sendSuccess(() -> Component.literal(PREFIX)
                    .append(Component.literal("There is no graffiti to clean.").withStyle(ChatFormatting.GRAY)), false);
            return 1;
        }

        PENDING_CONFIRMS.put(player.getUUID(), System.currentTimeMillis());

        source.sendSuccess(() -> Component.literal(PREFIX)
                .append(Component.literal("This will ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("DELETE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal(" all graffiti on the entire server!").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal(PREFIX)
                .append(Component.literal("This action ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("CANNOT").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal(" be undone!").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal(PREFIX)
                .append(Component.literal("Type ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("/graffiti confirm").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" within 30 seconds to proceed.").withStyle(ChatFormatting.GRAY)), false);

        return 1;
    }

    private static int confirm(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null || player.getServer() == null) return 0;

        var uuid = player.getUUID();
        Long time = PENDING_CONFIRMS.get(uuid);
        if (time == null || System.currentTimeMillis() - time > CONFIRM_TIMEOUT) {
            source.sendSuccess(() -> Component.literal(PREFIX)
                    .append(Component.literal("No pending clean action. Use ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("/graffiti clean").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" first.").withStyle(ChatFormatting.GRAY)), false);
            return 0;
        }

        PENDING_CONFIRMS.remove(uuid);

        var server = player.getServer();
        var level = server.getLevel(player.level().dimension());
        if (level == null) return 0;

        GraffitiMod.SERVER_CACHE.clear();
        GraffitiMod.UNDO_HISTORY.clear();
        GraffitiMod.LAST_PAINT_TICK.clear();

        var clipboardIter = GraffitiMod.PLAYER_CLIPBOARD.entrySet().iterator();
        while (clipboardIter.hasNext()) {
            clipboardIter.next();
            clipboardIter.remove();
        }

        if (!GraffitiMod.SUBMERGED_BRUSHES.isEmpty()) {
            GraffitiMod.SUBMERGED_BRUSHES.clear();
        }

        try {
            File saveFile = new File(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "graffiti.bin");
            if (saveFile.exists()) {
                saveFile.delete();
            }
        } catch (Exception ignored) {}

        var emptySync = new SyncGraffitiPayload(new ArrayList<>());
        for (var p : level.players()) {
            PacketDistributor.sendToPlayer(p, emptySync);
        }

        source.sendSuccess(() -> Component.literal(PREFIX)
                .append(Component.literal("All graffiti has been ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("CLEANED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                .append(Component.literal(" from the server!").withStyle(ChatFormatting.GRAY)), true);

        return 1;
    }
}
