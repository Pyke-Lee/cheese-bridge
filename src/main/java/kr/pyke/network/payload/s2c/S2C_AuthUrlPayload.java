package kr.pyke.network.payload.s2c;

import kr.pyke.command.IntegrationCommand;
import kr.pyke.network.CheeseBridgePacket;
import kr.pyke.network.ModPayload;
import kr.pyke.network.PayloadType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record S2C_AuthUrlPayload(String url, String platformName) implements ModPayload {
    public static final PayloadType<S2C_AuthUrlPayload> TYPE = PayloadType.create("s2c_auth_url", S2C_AuthUrlPayload::decode);

    @Override
    public PayloadType<S2C_AuthUrlPayload> payloadType() { return TYPE; }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(url);
        buf.writeUtf(platformName);
    }

    public static S2C_AuthUrlPayload decode(FriendlyByteBuf buf) {
        return new S2C_AuthUrlPayload(buf.readUtf(), buf.readUtf());
    }

    public static void send(ServerPlayer player, S2C_AuthUrlPayload payload) {
        CheeseBridgePacket.sendToPlayer(player, payload);
    }

    public static void handle(S2C_AuthUrlPayload payload, Minecraft client) {
        client.execute(() -> IntegrationCommand.startAuthProcess(payload.url(), payload.platformName()));
    }
}
