package kr.pyke.client;

import kr.pyke.CheeseBridge;
import kr.pyke.client.chzzk.ChzzkManager;
import kr.pyke.client.soop.SoopManager;
import kr.pyke.command.IntegrationCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

public class CheeseBridgeClient {
    private CheeseBridgeClient() { }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> IntegrationCommand.register(event.getDispatcher()));

        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            ChzzkManager.getInstance().disconnect();
            SoopManager.getInstance().disconnect();
            CheeseBridge.LOGGER.info("서버 연결 종료로 인해 모든 소켓을 닫습니다.");
        });
    }

    public static void sendMessage(Player player, String message) {
        if (player == null) { return; }
        Component component = Component.literal("§6[SYSTEM]§r ").append(CheeseBridge.parseComponent(message));

        player.displayClientMessage(component, false);
    }
}
