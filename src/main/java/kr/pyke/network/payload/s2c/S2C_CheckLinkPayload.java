package kr.pyke.network.payload.s2c;

import kr.pyke.client.LinkCheckClient;
import kr.pyke.network.CheeseBridgePacket;
import kr.pyke.network.ModPayload;
import kr.pyke.network.PayloadType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record S2C_CheckLinkPayload(String requestId) implements ModPayload {
    public static final PayloadType<S2C_CheckLinkPayload> TYPE = PayloadType.create("s2c_check_link", S2C_CheckLinkPayload::decode);

    @Override
    public PayloadType<S2C_CheckLinkPayload> payloadType() { return TYPE; }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(requestId);
    }

    public static S2C_CheckLinkPayload decode(FriendlyByteBuf buf) {
        return new S2C_CheckLinkPayload(buf.readUtf());
    }

    public static void send(ServerPlayer player, S2C_CheckLinkPayload payload) {
        CheeseBridgePacket.sendToPlayer(player, payload);
    }

    public static void handle(S2C_CheckLinkPayload payload, Minecraft client) {
        client.execute(() -> LinkCheckClient.runCheck(payload.requestId()));
    }
}
