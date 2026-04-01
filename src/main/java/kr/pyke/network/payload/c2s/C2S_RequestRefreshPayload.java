package kr.pyke.network.payload.c2s;

import kr.pyke.CheeseBridge;
import kr.pyke.PykeLib;
import kr.pyke.command.DonationCommand;
import kr.pyke.integration.BridgeDataState;
import kr.pyke.integration.BridgeIntegration;
import kr.pyke.network.payload.s2c.S2C_AuthUrlPayload;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import kr.pyke.util.PLATFORM;
import kr.pyke.type.COLOR;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public record C2S_RequestRefreshPayload(String platformName) implements CustomPacketPayload {
    public static final Type<C2S_RequestRefreshPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(CheeseBridge.MOD_ID, "c2s_request_refresh"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_RequestRefreshPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, C2S_RequestRefreshPayload::platformName,
        C2S_RequestRefreshPayload::new
    );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return ID; }

    public static void handle(C2S_RequestRefreshPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            PLATFORM platform = PLATFORM.valueOf(payload.platformName());
            BridgeDataState state = BridgeDataState.getServerState(context.server());
            BridgeDataState.TokenInfo tokenInfo = state.getToken(context.player().getUUID(), platform);

            if (tokenInfo != null && tokenInfo.refreshToken() != null) {
                CheeseBridge.LOGGER.info("[갱신] {} 님의 {} 토큰 갱신을 시도합니다.", context.player().getName().getString(), platform);
                String jsonResponse = BridgeIntegration.refreshAccessToken(platform, tokenInfo.refreshToken());

                BridgeDataState.TokenInfo newToken = BridgeIntegration.parseTokenResponse(jsonResponse);

                if (newToken != null) {
                    String finalRefresh = (newToken.refreshToken() != null) ? newToken.refreshToken() : tokenInfo.refreshToken();
                    state.setToken(context.player().getUUID(), platform, new BridgeDataState.TokenInfo(newToken.accessToken(), finalRefresh));

                    ServerPlayNetworking.send(context.player(), new S2C_FinalTokenPayload(newToken.accessToken(), platform.name()));
                    CheeseBridge.LOGGER.info("[갱신] 성공! 클라이언트에 새 토큰 전송 완료.");
                    return;
                }
            }

            CheeseBridge.LOGGER.warn("[갱신] 토큰 갱신 불가. 재인증을 요청합니다.");

            String url = DonationCommand.authPlatform(platform);
            PykeLib.sendSystemMessage(java.util.List.of(context.player()), COLOR.RED.getColor(), "인증 세션이 만료되었습니다. 다시 로그인을 진행해주세요.");
            ServerPlayNetworking.send(context.player(), new S2C_AuthUrlPayload(url, platform.name()));
        });
    }
}