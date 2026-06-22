package kr.pyke.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import kr.pyke.CheeseBridge;
import kr.pyke.config.CheeseBridgeConfig;
import kr.pyke.integration.BridgeDataState;
import kr.pyke.integration.BridgeIntegration;
import kr.pyke.integration.DonationEvent;
import kr.pyke.network.payload.s2c.S2C_AuthUrlPayload;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import kr.pyke.type.PLATFORM;
import kr.pyke.util.DonationLogger;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class DonationCommand {
    private DonationCommand() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        dispatcher.register(Commands.literal("후원")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("targets", EntityArgument.players())
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
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
            String platformArg = StringArgumentType.getString(ctx, "platform");
            int amount = IntegerArgumentType.getInteger(ctx, "donationAmount");

            CommandSourceStack source = ctx.getSource();
            ServerPlayer player = source.getPlayerOrException();
            String managerName = player.getName().getString();
            PLATFORM platformTag = platformArg.equals("숲") ? PLATFORM.SOOP : PLATFORM.CHZZK;

            for (ServerPlayer target : targets) {
                String targetName = target.getDisplayName().getString();

                source.getServer().execute(() -> {
                    DonationLogger.logDonationManager(targetName, String.valueOf(amount), managerName);
                    BridgeIntegration.triggerDonation(target, new DonationEvent("운영자", String.valueOf(amount), "수동 지급", platformTag));
                    CheeseBridge.sendPersonalMessage(player, String.format("&7%s&f님에게 &e%s(%s)&f 보상을 수동 지급했습니다.", targetName, amount, platformTag));
                });
            }

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
                S2C_FinalTokenPayload.send(player, new S2C_FinalTokenPayload(token.accessToken(), platform.name()));
            }
            else {
                String url = authPlatform(platform);
                S2C_AuthUrlPayload.send(player, new S2C_AuthUrlPayload(url, platform.name()));
            }
            return 1;
        }
        catch (Exception e) {
            CheeseBridge.LOGGER.error("명령어 실행 중 오류: ", e);
            return 0;
        }
    }

    public static String authPlatform(PLATFORM platform) {
        if (platform == PLATFORM.CHZZK) {
            String authState = UUID.randomUUID().toString();
            return String.format("https://chzzk.naver.com/account-interlock?clientId=%s&redirectUri=%s&state=%s",
                CheeseBridgeConfig.DATA.chzzk.clientID, "http://localhost:8080/callback", authState);
        }
        else {
            return String.format("https://openapi.sooplive.com/auth/code?client_id=%s&redirect_uri=%s&response_type=code",
                CheeseBridgeConfig.DATA.soop.clientID, "http://localhost:8080/callback");
        }
    }
}