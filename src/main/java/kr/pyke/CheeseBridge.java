package kr.pyke;

import kr.pyke.client.CheeseBridgeClient;
import kr.pyke.command.DonationCommand;
import kr.pyke.config.CheeseBridgeConfig;
import kr.pyke.integration.BridgeDataState;
import kr.pyke.network.CheeseBridgePacket;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import kr.pyke.type.PLATFORM;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CheeseBridge.MOD_ID)
public class CheeseBridge {
	public static final String MOD_ID = "cheese_bridge";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public CheeseBridge() {
		CheeseBridgeConfig.loadConfiguration();

		CheeseBridgePacket.register();

		MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
			DonationCommand.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection())
		);

		MinecraftForge.EVENT_BUS.addListener(CheeseBridge::onPlayerLoggedIn);

		if (FMLEnvironment.dist == Dist.CLIENT) {
			CheeseBridgeClient.init();
		}
	}

	private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) { return; }
		MinecraftServer server = player.getServer();
		if (server == null) { return; }

		server.execute(() -> {
			BridgeDataState state = BridgeDataState.getServerState(server);

			for (PLATFORM platform : PLATFORM.values()) {
				BridgeDataState.TokenInfo tokenInfo = state.getToken(player.getUUID(), platform);

				if (tokenInfo != null) {
					S2C_FinalTokenPayload.send(player, new S2C_FinalTokenPayload(tokenInfo.accessToken(), platform.name()));
					LOGGER.info("[인증] {} 님의 {} 토큰을 로드하여 자동 연결합니다.", player.getName().getString(), platform);
				}
			}
		});
	}

	public static MutableComponent parseComponent(String message) {
		if (message.isEmpty()) { return Component.empty(); }

		String formattedMessage = message.replace("&", "§");

		return Component.literal(formattedMessage).copy();
	}
}
