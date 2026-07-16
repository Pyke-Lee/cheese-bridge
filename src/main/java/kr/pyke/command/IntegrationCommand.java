package kr.pyke.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import kr.pyke.CheeseBridge;
import kr.pyke.client.server.BridgeAuthServer;
import kr.pyke.client.CheeseBridgeClient;
import kr.pyke.client.manager.chzzk.ChzzkManager;
import kr.pyke.client.manager.soop.SoopManager;
import kr.pyke.client.state.ConnectionStatus;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

public class IntegrationCommand {
    private static final BridgeAuthServer AUTH_SERVER = new BridgeAuthServer();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("연동해제")
            .executes(IntegrationCommand::executeDisconnect)
        );

        dispatcher.register(ClientCommands.literal("연동확인")
                .executes(IntegrationCommand::executeStatus)
        );
    }

    private static int executeStatus(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().getClient().execute(() -> {
            CheeseBridgeClient.sendMessage(Minecraft.getInstance().player, "§7연동 상태 확인 중...");

            AtomicInteger pending = new AtomicInteger(2);

            ChzzkManager.getInstance().checkStatus(status -> {
                Minecraft.getInstance().execute(() -> sendStatusMessage(status));
                if (pending.decrementAndGet() == 0) {
                    Minecraft.getInstance().execute(() ->
                            CheeseBridgeClient.sendMessage(Minecraft.getInstance().player, "§7상태 확인 완료."));
                }
            });

            SoopManager.getInstance().checkStatus(status -> {
                Minecraft.getInstance().execute(() -> sendStatusMessage(status));
                if (pending.decrementAndGet() == 0) {
                    Minecraft.getInstance().execute(() ->
                            CheeseBridgeClient.sendMessage(Minecraft.getInstance().player, "§7상태 확인 완료."));
                }
            });
        });

        return 1;
    }

    private static void sendStatusMessage(ConnectionStatus status) {
        String color = switch (status.state()) {
            case CONNECTED -> "§a";
            case DISCONNECTED -> "§7";
            case TOKEN_EXPIRED -> "§c";
            case ERROR -> "§e";
        };

        String icon = switch (status.state()) {
            case CONNECTED -> "●";
            case DISCONNECTED -> "○";
            case TOKEN_EXPIRED -> "✕";
            case ERROR -> "△";
        };

        StringBuilder sb = new StringBuilder();
        sb.append(color).append(icon).append(" ").append(status.summary());

        if (status.state() == ConnectionStatus.State.CONNECTED && status.detail() != null) {
            sb.append(" §8(").append(status.detail()).append(")");
        }

        CheeseBridgeClient.sendMessage(Minecraft.getInstance().player, sb.toString());
    }

    private static int executeDisconnect(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().getClient().execute(() -> {
            try {
                ChzzkManager.getInstance().disconnect();
                SoopManager.getInstance().disconnect();
                AUTH_SERVER.stop();
                CheeseBridgeClient.sendMessage(Minecraft.getInstance().player, "모든 후원 연동이 해제되었습니다.");
            }
            catch (Exception e) { CheeseBridge.LOGGER.error("연동 해제 명령어 실행 중 오류 발생: ", e); }
        });

        return 1;
    }

    public static void startAuthProcess(String url, String platformName) {
        AUTH_SERVER.start(platformName);
        openUrl(url);
        CheeseBridgeClient.sendMessage(Minecraft.getInstance().player, platformName + " 로그인을 진행해주세요.");
    }

    private static void openUrl(String url) {
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(url);
            Util.getPlatform().openUri(new URI(url));
        }
        catch (Exception e) {
            CheeseBridge.LOGGER.error("URL 열기 실패: {}", url, e);
        }
    }
}