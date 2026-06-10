package kr.pyke.network.payload.c2s;

import kr.pyke.CheeseBridge;
import kr.pyke.integration.BridgeIntegration;
import kr.pyke.integration.DonationEvent;
import kr.pyke.util.DonationLogger;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record C2S_DonationPayload(String donor, String donationAmount, String donationMessage, String platform) implements CustomPacketPayload {
    public static final Type<C2S_DonationPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(CheeseBridge.MOD_ID, "c2s_donation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_DonationPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, C2S_DonationPayload::donor,
        ByteBufCodecs.STRING_UTF8, C2S_DonationPayload::donationAmount,
        ByteBufCodecs.STRING_UTF8, C2S_DonationPayload::donationMessage,
        ByteBufCodecs.STRING_UTF8, C2S_DonationPayload::platform,
        C2S_DonationPayload::new
    );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return ID; }

    public static void handle(C2S_DonationPayload payload, ServerPlayNetworking.Context context) {
        String receiverName = context.player().getName().getString();

        context.server().execute(() -> {
            try {
                DonationLogger.logDonation(payload.donor(), receiverName, payload.donationAmount());

                BridgeIntegration.triggerDonation(context.player(), new DonationEvent(payload.donor(), payload.donationAmount(), payload.donationMessage(), payload.platform()));
            }
            catch (Exception e) { CheeseBridge.LOGGER.error("플레이어 {}의 후원 보상 처리 중 시스템 예외 발생:", receiverName, e); }
        });
    }
}