package kr.pyke.network.payload.s2c;

import kr.pyke.CheeseBridge;
import kr.pyke.client.chzzk.ChzzkManager;
import kr.pyke.client.soop.SoopManager;
import kr.pyke.network.CheeseBridgePacket;
import kr.pyke.network.ModPayload;
import kr.pyke.network.PayloadType;
import kr.pyke.type.PLATFORM;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record S2C_FinalTokenPayload(String accessToken, String platformName) implements ModPayload {
    public static final PayloadType<S2C_FinalTokenPayload> TYPE = PayloadType.create("s2c_final_token", S2C_FinalTokenPayload::decode);

    @Override
    public PayloadType<S2C_FinalTokenPayload> payloadType() { return TYPE; }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(accessToken);
        buf.writeUtf(platformName);
    }

    public static S2C_FinalTokenPayload decode(FriendlyByteBuf buf) {
        return new S2C_FinalTokenPayload(buf.readUtf(), buf.readUtf());
    }

    public static void send(ServerPlayer player, S2C_FinalTokenPayload payload) {
        CheeseBridgePacket.sendToPlayer(player, payload);
    }

    public static void handle(S2C_FinalTokenPayload payload, Minecraft client) {
        CheeseBridge.LOGGER.info("[디버그] 클라이언트: 토큰 패킷 도착함! -> {} (Platform: {})", payload.accessToken(), payload.platformName());
        client.execute(() -> {
            PLATFORM platform = PLATFORM.valueOf(payload.platformName());

            if (platform == PLATFORM.CHZZK) {
                ChzzkManager.getInstance().connect(payload.accessToken());
            }
            else if (platform == PLATFORM.SOOP) {
                SoopManager.getInstance().connect(payload.accessToken());
            }
        });
    }
}
