package kr.pyke.network.payload.c2s;

import kr.pyke.CheeseBridge;
import kr.pyke.integration.BridgeDataState;
import kr.pyke.integration.BridgeIntegration;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import kr.pyke.util.PLATFORM;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public record C2S_AuthCodePayload(String code, String state, String platformName) {
    public static final ResourceLocation ID = new ResourceLocation(CheeseBridge.MOD_ID, "c2s_auth_code");

    public static void encode(FriendlyByteBuf buf, C2S_AuthCodePayload payload) {
        buf.writeUtf(payload.code);
        buf.writeUtf(payload.state);
        buf.writeUtf(payload.platformName);
    }

    public static C2S_AuthCodePayload decode(FriendlyByteBuf buf) {
        return new C2S_AuthCodePayload(buf.readUtf(), buf.readUtf(), buf.readUtf());
    }

    public static void send(C2S_AuthCodePayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(buf, payload);
        ClientPlayNetworking.send(ID, buf);
    }

    public static void handle(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, PacketSender responseSender) {
        C2S_AuthCodePayload payload = decode(buf);
        server.execute(() -> {
            PLATFORM platform = PLATFORM.valueOf(payload.platformName());
            String jsonResponse = BridgeIntegration.exchangeCodeForToken(platform, payload.code(), payload.state());

            if (jsonResponse != null) {
                BridgeDataState.TokenInfo tokenInfo = BridgeIntegration.parseTokenResponse(jsonResponse);

                if (tokenInfo != null) {
                    BridgeDataState state = BridgeDataState.getServerState(server);
                    state.setToken(player.getUUID(), platform, tokenInfo);

                    S2C_FinalTokenPayload.send(player, new S2C_FinalTokenPayload(tokenInfo.accessToken(), platform.name()));
                    CheeseBridge.LOGGER.info("[인증] {} 토큰 발급 및 저장 완료!", platform);
                }
                else {
                    CheeseBridge.LOGGER.error("[인증] 토큰 발급 실패 (응답 내용): {}", jsonResponse);
                }
            }
        });
    }
}
