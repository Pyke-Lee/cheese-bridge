package kr.pyke.network.payload.c2s;

import kr.pyke.CheeseBridge;
import kr.pyke.integration.BridgeDataState;
import kr.pyke.integration.BridgeIntegration;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import kr.pyke.util.PLATFORM;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record C2S_AuthCodePayload(String code, String state, String platformName) implements CustomPacketPayload {
    public static final Type<C2S_AuthCodePayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(CheeseBridge.MOD_ID, "c2s_auth_code"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_AuthCodePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, C2S_AuthCodePayload::code,
        ByteBufCodecs.STRING_UTF8, C2S_AuthCodePayload::state,
        ByteBufCodecs.STRING_UTF8, C2S_AuthCodePayload::platformName,
        C2S_AuthCodePayload::new
    );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return ID; }

    public static void handle(C2S_AuthCodePayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            PLATFORM platform = PLATFORM.valueOf(payload.platformName());
            String jsonResponse = BridgeIntegration.exchangeCodeForToken(platform, payload.code(), payload.state());

            if (jsonResponse != null) {
                BridgeDataState.TokenInfo tokenInfo = BridgeIntegration.parseTokenResponse(jsonResponse);

                if (tokenInfo != null) {
                    BridgeDataState state = BridgeDataState.getServerState(context.server());
                    state.setToken(context.player().getUUID(), platform, tokenInfo);

                    ServerPlayNetworking.send(context.player(), new S2C_FinalTokenPayload(tokenInfo.accessToken(), platform.name()));
                    CheeseBridge.LOGGER.info("[인증] {} 토큰 발급 및 저장 완료!", platform);
                }
                else {
                    CheeseBridge.LOGGER.error("[인증] 토큰 발급 실패 (응답 내용): {}", jsonResponse);
                }
            }
        });
    }
}