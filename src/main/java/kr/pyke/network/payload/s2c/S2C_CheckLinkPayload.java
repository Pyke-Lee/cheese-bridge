package kr.pyke.network.payload.s2c;

import kr.pyke.client.LinkCheckClient;
import kr.pyke.network.CheeseBridgePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record S2C_CheckLinkPayload(String requestId) {
    public static void encode(FriendlyByteBuf buf, S2C_CheckLinkPayload payload) {
        buf.writeUtf(payload.requestId);
    }

    public static S2C_CheckLinkPayload decode(FriendlyByteBuf buf) {
        return new S2C_CheckLinkPayload(buf.readUtf());
    }

    public static void send(ServerPlayer player, S2C_CheckLinkPayload payload) {
        CheeseBridgePacket.sendToPlayer(player, payload);
    }

    public static void handle(S2C_CheckLinkPayload payload) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> LinkCheckClient.runCheck(payload.requestId()));
    }
}