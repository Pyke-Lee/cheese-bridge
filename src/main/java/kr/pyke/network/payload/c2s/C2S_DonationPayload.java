package kr.pyke.network.payload.c2s;

import kr.pyke.CheeseBridge;
import kr.pyke.integration.BridgeIntegration;
import kr.pyke.integration.DonationEvent;
import kr.pyke.network.CheeseBridgePacket;
import kr.pyke.network.ModPayload;
import kr.pyke.network.PayloadType;
import kr.pyke.type.PLATFORM;
import kr.pyke.util.DonationLogger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public record C2S_DonationPayload(String donor, String donationAmount, String donationMessage, String platform) implements ModPayload {
    public static final PayloadType<C2S_DonationPayload> TYPE = PayloadType.create("c2s_donation", C2S_DonationPayload::decode);

    @Override
    public PayloadType<C2S_DonationPayload> payloadType() { return TYPE; }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(donor);
        buf.writeUtf(donationAmount);
        buf.writeUtf(donationMessage);
        buf.writeUtf(platform);
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
