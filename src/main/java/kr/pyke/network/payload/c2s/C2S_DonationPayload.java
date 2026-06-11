package kr.pyke.network.payload.c2s;

import kr.pyke.CheeseBridge;
import kr.pyke.integration.BridgeIntegration;
import kr.pyke.integration.DonationEvent;
import kr.pyke.type.PLATFORM;
import kr.pyke.util.DonationLogger;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.Objects;

public record C2S_DonationPayload(String donor, String donationAmount, String donationMessage, String platform) {
    public static final ResourceLocation ID = new ResourceLocation(CheeseBridge.MOD_ID, "c2s_donation");

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
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(buf, payload);
        ClientPlayNetworking.send(ID, buf);
    }

    public static void handle(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, PacketSender responseSender) {
        C2S_DonationPayload payload = decode(buf);
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
