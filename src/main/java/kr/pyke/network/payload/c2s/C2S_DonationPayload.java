package kr.pyke.network.payload.c2s;

import kr.pyke.CheeseBridge;
import kr.pyke.integration.BridgeIntegration;
import kr.pyke.integration.DonationEvent;
import kr.pyke.network.CheeseBridgePacket;
import kr.pyke.type.PLATFORM;
import kr.pyke.util.DonationLogger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public record C2S_DonationPayload(String donor, String donationAmount, String donationMessage, String platform) {
    public static void encode(FriendlyByteBuf buf, C2S_DonationPayload payload) {
        buf.writeUtf(payload.donor);
        buf.writeUtf(payload.donationAmount);
        buf.writeUtf(payload.donationMessage);
        buf.writeUtf(payload.platform);
    }

    public static C2S_DonationPayload decode(FriendlyByteBuf buf) {
        return new C2S_DonationPayload(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf());
    }

    public static void send(C2S_DonationPayload payload) {
        CheeseBridgePacket.sendToServer(payload);
    }

    public static void handle(C2S_DonationPayload payload, MinecraftServer server, ServerPlayer player) {
        String receiverName = player.getName().getString();

        server.execute(() -> {
            try {
                DonationLogger.logDonation(payload.donor(), receiverName, payload.donationAmount());

                PLATFORM platform = PLATFORM.NONE;
                if (Objects.equals(payload.platform(), "SOOP")) { platform = PLATFORM.SOOP; }
                else if (Objects.equals(payload.platform(), "CHZZK")) { platform = PLATFORM.CHZZK; }

                BridgeIntegration.triggerDonation(player, new DonationEvent(payload.donor(), payload.donationAmount(), payload.donationMessage(), platform));
            }
            catch (Exception e) { CheeseBridge.LOGGER.error("플레이어 {}의 후원 보상 처리 중 시스템 예외 발생:", receiverName, e); }
        });
    }
}
