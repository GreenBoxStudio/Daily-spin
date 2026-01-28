package Greenbox.org.daily_case;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

@Mod.EventBusSubscriber(modid = Daily_case.MODID)
public class DailyCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("daily")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    NetworkHooks.openScreen(player,
                            new SimpleMenuProvider((id, inv, p) -> new DailyMenu(id, inv, player), Component.literal("Daily Case")),
                            buf -> {
                            });
                    return 1;
                }));

        // Command to spawn a Gambler villager
        event.getDispatcher().register(Commands.literal("spawngambler")
                .requires(source -> source.hasPermission(2)) // Requires OP level 2
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    BlockPos pos = player.blockPosition();

                    Villager villager = EntityType.VILLAGER.create(level);
                    if (villager != null) {
                        villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        villager.setVillagerData(villager.getVillagerData().setProfession(Daily_case.GAMBLER.get()));
                        level.addFreshEntity(villager);
                        ctx.getSource().sendSuccess(() -> Component.literal("Spawned a Gambler villager!"), true);
                        return 1;
                    }
                    return 0;
                })
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
                    .executes(ctx -> {
                        int count = IntegerArgumentType.getInteger(ctx, "count");
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition();

                        for (int i = 0; i < count; i++) {
                            Villager villager = EntityType.VILLAGER.create(level);
                            if (villager != null) {
                                double offsetX = (Math.random() - 0.5) * 2;
                                double offsetZ = (Math.random() - 0.5) * 2;
                                villager.setPos(pos.getX() + offsetX, pos.getY(), pos.getZ() + offsetZ);
                                villager.setVillagerData(villager.getVillagerData().setProfession(Daily_case.GAMBLER.get()));
                                level.addFreshEntity(villager);
                            }
                        }
                        final int spawnedCount = count;
                        ctx.getSource().sendSuccess(() -> Component.literal("Spawned " + spawnedCount + " Gambler villagers!"), true);
                        return 1;
                    })
                ));
    }
}
