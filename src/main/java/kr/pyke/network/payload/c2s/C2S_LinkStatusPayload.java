package kr.pyke.network.payload.c2s;

import kr.pyke.integration.LinkCheckCollector;
import kr.pyke.network.CheeseBridgePacket;
import kr.pyke.network.ModPayload;
import kr.pyke.network.PayloadType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public record C2S_LinkStatusPayload(String requestId, String result) implements ModPayload {
    public static final PayloadType<C2S_LinkStatusPayload> TYPE = PayloadType.create("c2s_link_status", C2S_LinkStatusPayload::decode);

    @Override
    public PayloadType<C2S_LinkStatusPayload> payloadType() { return TYPE; }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(requestId);
        buf.writeUtf(result);
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
