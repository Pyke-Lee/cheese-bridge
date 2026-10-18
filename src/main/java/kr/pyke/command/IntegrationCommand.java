package kr.pyke.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import kr.pyke.CheeseBridge;
import kr.pyke.client.BridgeAuthServer;
import kr.pyke.client.CheeseBridgeClient;
import kr.pyke.client.chzzk.ChzzkManager;
import kr.pyke.client.soop.SoopManager;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
*///?} else {
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
//?}
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
//? if >=1.21.11 {
/*import net.minecraft.util.Util;
*///?} else {
import net.minecraft.Util;
//?}

//? if >=26.3 {
/*import com.mojang.blaze3d.Blaze3D;
*///?}

import java.net.URI;

public class IntegrationCommand {
    private static final BridgeAuthServer AUTH_SERVER = new BridgeAuthServer();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        //? if >=26.1 {
        /*dispatcher.register(ClientCommands.literal("연동해제")
        *///?} else {
        dispatcher.register(ClientCommandManager.literal("연동해제")
        //?}
            .executes(IntegrationCommand::executeDisconnect)
        );
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
            //? if >=26.3 {
            /*Blaze3D.openUri(new URI(url));
            *///?} else {
            Util.getPlatform().openUri(new URI(url));
            //?}
        }
        catch (Exception e) {
            CheeseBridge.LOGGER.error("URL 열기 실패: {}", url, e);
        }
    }
}