package kr.pyke;

import kr.pyke.command.DonationCommand;
import kr.pyke.config.CheeseBridgeConfig;
import kr.pyke.integration.BridgeDataState;
import kr.pyke.network.CheeseBridgePacket;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import kr.pyke.type.PLATFORM;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheeseBridge implements ModInitializer {
	public static final String MOD_ID = "cheese-bridge";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CheeseBridgeConfig.loadConfiguration();

		CheeseBridgePacket.registerCodec();
		CheeseBridgePacket.registerServer();

		CommandRegistrationCallback.EVENT.register(DonationCommand::register);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			server.execute(() -> {
				ServerPlayer player = handler.getPlayer();

				BridgeDataState state = BridgeDataState.getServerState(server);

				for (PLATFORM platform : PLATFORM.values()) {
					BridgeDataState.TokenInfo tokenInfo = state.getToken(player.getUUID(), platform);

					if (tokenInfo != null) {
						ServerPlayNetworking.send(player, new S2C_FinalTokenPayload(tokenInfo.accessToken(), platform.name()));
						LOGGER.info("[인증] {} 님의 {} 토큰을 로드하여 자동 연결합니다.", player.getName().getString(), platform);
					}
				}
			});
		});
	}
}