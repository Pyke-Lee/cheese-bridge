package kr.pyke.network.payload.s2c;

import kr.pyke.CheeseBridge;
import kr.pyke.client.chzzk.ChzzkManager;
import kr.pyke.client.soop.SoopManager;
import kr.pyke.util.PLATFORM;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record S2C_FinalTokenPayload(String accessToken, String platformName) {
    public static final ResourceLocation ID = new ResourceLocation(CheeseBridge.MOD_ID, "s2c_final_token");

    public static void encode(FriendlyByteBuf buf, S2C_FinalTokenPayload payload) {
        buf.writeUtf(payload.accessToken);
        buf.writeUtf(payload.platformName);
    }

    public static S2C_FinalTokenPayload decode(FriendlyByteBuf buf) {
        return new S2C_FinalTokenPayload(buf.readUtf(), buf.readUtf());
    }

    public static void send(ServerPlayer player, S2C_FinalTokenPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(buf, payload);
        ServerPlayNetworking.send(player, ID, buf);
    }

    public static void handle(Minecraft client, ClientPacketListener handler, FriendlyByteBuf buf, PacketSender responseSender) {
        S2C_FinalTokenPayload payload = decode(buf);
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
