package kr.pyke.network.payload.s2c;

import kr.pyke.CheeseBridge;
import kr.pyke.command.IntegrationCommand;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record S2C_AuthUrlPayload(String url, String platformName) implements CustomPacketPayload {
    public static final Type<S2C_AuthUrlPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(CheeseBridge.MOD_ID, "s2c_auth_url"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2C_AuthUrlPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, S2C_AuthUrlPayload::url,
        ByteBufCodecs.STRING_UTF8, S2C_AuthUrlPayload::platformName,
        S2C_AuthUrlPayload::new
    );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return ID; }

    public static void handle(S2C_AuthUrlPayload payload, ClientPlayNetworking.Context context) {
        context.client().execute(() -> IntegrationCommand.startAuthProcess(payload.url(), payload.platformName()));
    }
}