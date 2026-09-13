package kr.pyke.network.payload.s2c;

import kr.pyke.CheeseBridge;
import kr.pyke.client.LinkCheckClient;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record S2C_CheckLinkPayload(String requestId) {
    public static final ResourceLocation ID = new ResourceLocation(CheeseBridge.MOD_ID, "s2c_check_link");

    public static void encode(FriendlyByteBuf buf, S2C_CheckLinkPayload payload) {
        buf.writeUtf(payload.requestId);
    }

    public static S2C_CheckLinkPayload decode(FriendlyByteBuf buf) {
        return new S2C_CheckLinkPayload(buf.readUtf());
    }

    public static void send(ServerPlayer player, S2C_CheckLinkPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(buf, payload);
        ServerPlayNetworking.send(player, ID, buf);
    }

    public static void handle(Minecraft client, ClientPacketListener handler, FriendlyByteBuf buf, PacketSender responseSender) {
        S2C_CheckLinkPayload payload = decode(buf);
        client.execute(() -> LinkCheckClient.runCheck(payload.requestId()));
    }
}