package kr.pyke.network.payload.s2c;

import kr.pyke.command.IntegrationCommand;
import kr.pyke.network.CheeseBridgePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record S2C_AuthUrlPayload(String url, String platformName) {
    public static void encode(FriendlyByteBuf buf, S2C_AuthUrlPayload payload) {
        buf.writeUtf(payload.url);
        buf.writeUtf(payload.platformName);
    }

    public static S2C_AuthUrlPayload decode(FriendlyByteBuf buf) {
        return new S2C_AuthUrlPayload(buf.readUtf(), buf.readUtf());
    }

    public static void send(ServerPlayer player, S2C_AuthUrlPayload payload) {
        CheeseBridgePacket.sendToPlayer(player, payload);
    }

    public static void handle(S2C_AuthUrlPayload payload) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> IntegrationCommand.startAuthProcess(payload.url(), payload.platformName()));
    }
}
