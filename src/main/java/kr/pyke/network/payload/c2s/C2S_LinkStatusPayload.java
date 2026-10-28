package kr.pyke.network.payload.c2s;

import kr.pyke.integration.LinkCheckCollector;
import kr.pyke.network.CheeseBridgePacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public record C2S_LinkStatusPayload(String requestId, String result) {
    public static void encode(FriendlyByteBuf buf, C2S_LinkStatusPayload payload) {
        buf.writeUtf(payload.requestId);
        buf.writeUtf(payload.result);
    }

    public static C2S_LinkStatusPayload decode(FriendlyByteBuf buf) {
        return new C2S_LinkStatusPayload(buf.readUtf(), buf.readUtf());
    }

    public static void send(C2S_LinkStatusPayload payload) {
        CheeseBridgePacket.sendToServer(payload);
    }

    public static void handle(C2S_LinkStatusPayload payload, MinecraftServer server, ServerPlayer player) {
        String playerName = player.getName().getString();
        server.execute(() -> LinkCheckCollector.onResult(payload.requestId(), playerName, payload.result()));
    }
}