package Greenbox.org.daily_case;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.ChatFormatting;

/**
 * Commands for Daily Case mod
 */
public class DailyCaseCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("daily")
            .executes(ctx -> openDailyMenu(ctx))
            .then(Commands.literal("stats")
                .executes(ctx -> showStats(ctx, ctx.getSource().getPlayerOrException())))
            .then(Commands.literal("reload")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> reloadConfig(ctx)))
            .then(Commands.literal("give")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("case", StringArgumentType.string())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> giveCaseKey(ctx,
                                EntityArgument.getPlayer(ctx, "player"),
                                StringArgumentType.getString(ctx, "case"),
                                IntegerArgumentType.getInteger(ctx, "amount")))))))
            .then(Commands.literal("bypass")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> toggleBypass(ctx, EntityArgument.getPlayer(ctx, "player")))))
        );
    }

    private static int openDailyMenu(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            player.openMenu(new SimpleMenuProvider(
                (windowId, playerInventory, p) -> new DailyMenu(windowId, playerInventory, p),
                Component.literal("Daily Case")
            ));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Fehler beim Öffnen des Menüs"));
            return 0;
        }
    }

    private static int showStats(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        int total = CaseStats.getTotalCasesOpened(player);
        int legendary = CaseStats.getLegendaryCount(player);
        int epic = CaseStats.getEpicCount(player);
        int rare = CaseStats.getRareCount(player);

        ctx.getSource().sendSuccess(() -> Component.literal("=== Daily Case Statistiken ===").withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Geöffnete Cases: " + total).withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Legendary Items: " + legendary).withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Epic Items: " + epic).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Rare Items: " + rare).withStyle(ChatFormatting.BLUE), false);

        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        // Config wird beim nächsten Zugriff neu geladen
        ctx.getSource().sendSuccess(() -> Component.literal("Config wird beim nächsten Start neu geladen"), true);
        return 1;
    }

    private static int giveCaseKey(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String caseId, int amount) {
        // TODO: Implement case keys when system is ready
        ctx.getSource().sendSuccess(() ->
            Component.literal("Case-Keys System noch nicht implementiert"), false);
        return 1;
    }

    private static int toggleBypass(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        // TODO: Implement bypass system
        ctx.getSource().sendSuccess(() ->
            Component.literal("Bypass-System noch nicht implementiert"), false);
        return 1;
    }
}
