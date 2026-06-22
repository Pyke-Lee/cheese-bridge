package kr.pyke.client;

import kr.pyke.CheeseBridge;
import kr.pyke.client.chzzk.ChzzkManager;
import kr.pyke.client.soop.SoopManager;
import kr.pyke.command.IntegrationCommand;
import kr.pyke.network.CheeseBridgePacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class CheeseBridgeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CheeseBridgePacket.registerClient();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> IntegrationCommand.register(dispatcher));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ChzzkManager.getInstance().disconnect();
            SoopManager.getInstance().disconnect();
            CheeseBridge.LOGGER.info("서버 연결 종료로 인해 모든 소켓을 닫습니다.");
        });
    }

    public static void sendMessage(Player player, String message) {
        if (player == null) { return; }
        Component component = CheeseBridge.parseComponent(message);

        player.sendSystemMessage(component);
    }
}