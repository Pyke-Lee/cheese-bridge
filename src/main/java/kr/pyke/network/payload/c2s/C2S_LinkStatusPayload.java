package kr.pyke.network.payload.c2s;

import kr.pyke.CheeseBridge;
import kr.pyke.integration.LinkCheckCollector;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public record C2S_LinkStatusPayload(String requestId, String result) {
    public static final ResourceLocation ID = new ResourceLocation(CheeseBridge.MOD_ID, "c2s_link_status");

    public static void encode(FriendlyByteBuf buf, C2S_LinkStatusPayload payload) {
        buf.writeUtf(payload.requestId);
        buf.writeUtf(payload.result);
    }

    public static C2S_LinkStatusPayload decode(FriendlyByteBuf buf) {
        return new C2S_LinkStatusPayload(buf.readUtf(), buf.readUtf());
    }

    public static void send(C2S_LinkStatusPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(buf, payload);
        ClientPlayNetworking.send(ID, buf);
    }

    public static void handle(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, PacketSender responseSender) {
        C2S_LinkStatusPayload payload = decode(buf);
        String playerName = player.getName().getString();
        server.execute(() -> LinkCheckCollector.onResult(payload.requestId(), playerName, payload.result()));
    }
}