package kr.pyke.network.payload.s2c;

import kr.pyke.CheeseBridge;
import kr.pyke.command.IntegrationCommand;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record S2C_AuthUrlPayload(String url, String platformName) {
    public static final ResourceLocation ID = new ResourceLocation(CheeseBridge.MOD_ID, "s2c_auth_url");

    public static void encode(FriendlyByteBuf buf, S2C_AuthUrlPayload payload) {
        buf.writeUtf(payload.url);
        buf.writeUtf(payload.platformName);
    }

    public static S2C_AuthUrlPayload decode(FriendlyByteBuf buf) {
        return new S2C_AuthUrlPayload(buf.readUtf(), buf.readUtf());
    }

    public static void send(ServerPlayer player, S2C_AuthUrlPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(buf, payload);
        ServerPlayNetworking.send(player, ID, buf);
    }

    public static void handle(Minecraft client, ClientPacketListener handler, FriendlyByteBuf buf, PacketSender responseSender) {
        S2C_AuthUrlPayload payload = decode(buf);
        client.execute(() -> IntegrationCommand.startAuthProcess(payload.url(), payload.platformName()));
    }
}
