package kr.pyke.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import kr.pyke.PykeLib;
import kr.pyke.config.CheeseBridgeConfig;
import kr.pyke.integration.BridgeDataState;
import kr.pyke.integration.BridgeIntegration;
import kr.pyke.integration.DonationEvent;
import kr.pyke.network.payload.s2c.S2C_AuthUrlPayload;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import kr.pyke.util.DonationLogger;
import kr.pyke.util.PLATFORM;
import kr.pyke.util.constants.COLOR;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DonationCommand {
    private DonationCommand() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        dispatcher.register(Commands.literal("후원")
            .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(2))))
            .then(Commands.argument("targetPlayer", EntityArgument.player())
                .then(Commands.argument("platform", StringArgumentType.string())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("\"치지직\"", "\"숲\""), builder))
                    .then(Commands.argument("donationAmount", IntegerArgumentType.integer(0))
                        .executes(DonationCommand::executeManualDonation)
                    )
                )
            )
        );

        dispatcher.register(Commands.literal("후원연동")
            .then(Commands.argument("platform", StringArgumentType.string())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("\"치지직\"", "\"숲\""), builder))
                .executes(DonationCommand::executeIntegrationConnect)
            )
        );
    }

    private static int executeManualDonation(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer targetPlayer = EntityArgument.getPlayer(ctx, "targetPlayer");
            String platformArg = StringArgumentType.getString(ctx, "platform");
            int amount = IntegerArgumentType.getInteger(ctx, "donationAmount");
            ServerPlayer player = ctx.getSource().getPlayerOrException();

            CommandSourceStack source = ctx.getSource();
            String managerName = Objects.requireNonNull(source.getPlayer()).getName().getString();
            String targetPlayerName = targetPlayer.getDisplayName().getString();

            String platformTag = platformArg.equals("숲") ? "숲" : "치지직";

            source.getServer().execute(() -> {
                DonationLogger.logDonationManager(targetPlayerName, String.valueOf(amount), managerName);
                BridgeIntegration.triggerDonation(targetPlayer, new DonationEvent("운영자", String.valueOf(amount), "수동 지급", platformTag));
                PykeLib.sendSystemMessage(player, COLOR.LIME.getColor(), String.format("&7%s&f님에게 &e%s(%s)&f 보상을 수동 지급했습니다.", targetPlayerName, amount, platformTag));
            });

            return 1;
        }
        catch (Exception e) {
            return 0;
        }
    }

    private static int executeIntegrationConnect(CommandContext<CommandSourceStack> ctx) {
        String arg = StringArgumentType.getString(ctx, "platform");
        PLATFORM platform = arg.equals("숲") ? PLATFORM.SOOP : (arg.equals("치지직") ? PLATFORM.CHZZK : null);

        if (platform == null) {
            ctx.getSource().sendFailure(Component.literal("사용법: /후원연동 <치지직/숲>"));
            return 0;
        }

        return executeConnect(ctx.getSource(), platform);
    }

    private static int executeConnect(CommandSourceStack source, PLATFORM platform) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            BridgeDataState state = BridgeDataState.getServerState(source.getServer());
            BridgeDataState.TokenInfo token = state.getToken(player.getUUID(), platform);

            if (token != null) {
                ServerPlayNetworking.send(player, new S2C_FinalTokenPayload(token.accessToken(), platform.name()));
            }
            else {
                String url;
                if (platform == PLATFORM.CHZZK) {
                    String authState = UUID.randomUUID().toString();
                    url = String.format("https://chzzk.naver.com/account-interlock?clientId=%s&redirectUri=%s&state=%s",
                        CheeseBridgeConfig.DATA.chzzk.clientID, "http://localhost:8080/callback", authState);
                }
                else {
                    url = String.format("https://openapi.sooplive.com/auth/code?client_id=%s&redirect_uri=%s",
                        CheeseBridgeConfig.DATA.soop.clientID, "http://localhost:8080/callback");
                }
                ServerPlayNetworking.send(player, new S2C_AuthUrlPayload(url, platform.name()));
            }
            return 1;
        }
        catch (Exception e) {
            return 0;
        }
    }
}