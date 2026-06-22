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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class CheeseBridge implements ModInitializer {
	public static final String MOD_ID = "cheese-bridge";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CheeseBridgeConfig.loadConfiguration();

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

	public static void sendPersonalMessage(ServerPlayer player, String message) {
		Component component = Component.literal("§6[SYSTEM]§r ").append(parseComponent(message));

		player.sendSystemMessage(component);
	}

	public static void sendServerMessage(MinecraftServer server, String message) {
		Collection<ServerPlayer> players = server.getPlayerList().getPlayers();
		Component component = Component.literal("§6[SYSTEM]§r ").append(parseComponent(message));
		for (ServerPlayer player : players) {
			player.sendSystemMessage(component);
		}
	}

	public static void broadcastMessage(MinecraftServer server, String message) {
		Collection<ServerPlayer> players = server.getPlayerList().getPlayers();
		Component component = Component.literal("ꅑ ").append(parseComponent(message));
		for (ServerPlayer player : players) {
			player.sendSystemMessage(Component.empty());
			player.sendSystemMessage(component);
			player.sendSystemMessage(Component.empty());
		}
	}

	public static MutableComponent parseComponent(String message) {
		if (message.isEmpty()) { return Component.empty(); }

		String formattedMessage = message.replace("&", "§");

		return Component.literal(formattedMessage).copy();
	}
}