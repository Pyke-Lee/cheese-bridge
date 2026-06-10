package kr.pyke.network.payload.s2c;

import kr.pyke.CheeseBridge;
import kr.pyke.client.chzzk.ChzzkManager;
import kr.pyke.client.soop.SoopManager;
import kr.pyke.util.PLATFORM;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record S2C_FinalTokenPayload(String accessToken, String platformName) implements CustomPacketPayload {
    public static final Type<S2C_FinalTokenPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(CheeseBridge.MOD_ID, "s2c_final_token"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2C_FinalTokenPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, S2C_FinalTokenPayload::accessToken,
        ByteBufCodecs.STRING_UTF8, S2C_FinalTokenPayload::platformName,
        S2C_FinalTokenPayload::new
    );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return ID; }

    public static void handle(S2C_FinalTokenPayload payload, ClientPlayNetworking.Context context) {
        CheeseBridge.LOGGER.info("[디버그] 클라이언트: 토큰 패킷 도착함! -> {} (Platform: {})", payload.accessToken(), payload.platformName());
        context.client().execute(() -> {
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