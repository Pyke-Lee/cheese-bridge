package kr.pyke.network.payload.c2s;

import kr.pyke.CheeseBridge;
import kr.pyke.PykeLib;
import kr.pyke.command.DonationCommand;
import kr.pyke.integration.BridgeDataState;
import kr.pyke.integration.BridgeIntegration;
import kr.pyke.network.payload.s2c.S2C_AuthUrlPayload;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import kr.pyke.type.PLATFORM;
import kr.pyke.util.constants.COLOR;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public record C2S_RequestRefreshPayload(String platformName) {
    public static final ResourceLocation ID = new ResourceLocation(CheeseBridge.MOD_ID, "c2s_request_refresh");

    public static void encode(FriendlyByteBuf buf, C2S_RequestRefreshPayload payload) {
        buf.writeUtf(payload.platformName);
    }

    public static C2S_RequestRefreshPayload decode(FriendlyByteBuf buf) {
        return new C2S_RequestRefreshPayload(buf.readUtf());
    }

    public static void send(C2S_RequestRefreshPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(buf, payload);
        ClientPlayNetworking.send(ID, buf);
    }

    public static void handle(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, PacketSender responseSender) {
        C2S_RequestRefreshPayload payload = decode(buf);
        server.execute(() -> {
            PLATFORM platform = PLATFORM.valueOf(payload.platformName());
            BridgeDataState state = BridgeDataState.getServerState(server);
            BridgeDataState.TokenInfo tokenInfo = state.getToken(player.getUUID(), platform);

            if (tokenInfo != null && tokenInfo.refreshToken() != null) {
                CheeseBridge.LOGGER.info("[갱신] {} 님의 {} 토큰 갱신을 시도합니다.", player.getName().getString(), platform);
                String jsonResponse = BridgeIntegration.refreshAccessToken(platform, tokenInfo.refreshToken());

                BridgeDataState.TokenInfo newToken = BridgeIntegration.parseTokenResponse(jsonResponse);

                if (newToken != null) {
                    String finalRefresh = (newToken.refreshToken() != null) ? newToken.refreshToken() : tokenInfo.refreshToken();
                    state.setToken(player.getUUID(), platform, new BridgeDataState.TokenInfo(newToken.accessToken(), finalRefresh));

                    S2C_FinalTokenPayload.send(player, new S2C_FinalTokenPayload(newToken.accessToken(), platform.name()));
                    CheeseBridge.LOGGER.info("[갱신] 성공! 클라이언트에 새 토큰 전송 완료.");
                    return;
                }
            }

            CheeseBridge.LOGGER.warn("[갱신] 토큰 갱신 불가. 재인증을 요청합니다.");

            String url = DonationCommand.authPlatform(platform);
            PykeLib.sendSystemMessage(java.util.List.of(player), COLOR.RED.getColor(), "인증 세션이 만료되었습니다. 다시 로그인을 진행해주세요.");
            S2C_AuthUrlPayload.send(player, new S2C_AuthUrlPayload(url, platform.name()));
        });
    }
}
