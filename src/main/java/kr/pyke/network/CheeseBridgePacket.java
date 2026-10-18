package kr.pyke.network;

import kr.pyke.CheeseBridge;
import kr.pyke.network.payload.c2s.C2S_AuthCodePayload;
import kr.pyke.network.payload.c2s.C2S_DonationPayload;
import kr.pyke.network.payload.c2s.C2S_LinkStatusPayload;
import kr.pyke.network.payload.c2s.C2S_RequestRefreshPayload;
import kr.pyke.network.payload.s2c.S2C_AuthUrlPayload;
import kr.pyke.network.payload.s2c.S2C_CheckLinkPayload;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.20.5 {
/*import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
*///?} else {
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
//?}

public class CheeseBridgePacket {
    private CheeseBridgePacket() { }

    @FunctionalInterface
    public interface ServerHandler<T> {
        void handle(T payload, MinecraftServer server, ServerPlayer player);
    }

    @FunctionalInterface
    public interface ClientHandler<T> {
        void handle(T payload, Minecraft client);
    }

    public static void registerServer() {
        // S2C (Server → Client)
        registerS2CType(S2C_AuthUrlPayload.TYPE);
        registerS2CType(S2C_FinalTokenPayload.TYPE);
        registerS2CType(S2C_CheckLinkPayload.TYPE);

        // C2S (Client → Server)
        registerC2S(C2S_DonationPayload.TYPE, C2S_DonationPayload::handle);
        registerC2S(C2S_AuthCodePayload.TYPE, C2S_AuthCodePayload::handle);
        registerC2S(C2S_RequestRefreshPayload.TYPE, C2S_RequestRefreshPayload::handle);
        registerC2S(C2S_LinkStatusPayload.TYPE, C2S_LinkStatusPayload::handle);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        registerS2C(S2C_AuthUrlPayload.TYPE, S2C_AuthUrlPayload::handle);
        registerS2C(S2C_FinalTokenPayload.TYPE, S2C_FinalTokenPayload::handle);
        registerS2C(S2C_CheckLinkPayload.TYPE, S2C_CheckLinkPayload::handle);
    }

    public static void sendToPlayer(ServerPlayer player, ModPayload payload) {
        //? if >=1.20.5 {
        /*ServerPlayNetworking.send(player, payload);
        *///?} else {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ServerPlayNetworking.send(player, payload.payloadType().id(), buf);
        //?}
    }

    @Environment(EnvType.CLIENT)
    public static void sendToServer(ModPayload payload) {
        //? if >=1.20.5 {
        /*ClientPlayNetworking.send(payload);
        *///?} else {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ClientPlayNetworking.send(payload.payloadType().id(), buf);
        //?}
    }

    private static <T extends ModPayload> void registerS2CType(PayloadType<T> type) {
        //? if >=26.1 {
        /*PayloadTypeRegistry.clientboundPlay().register(type.type(), type.codec());
        *///?} else if >=1.20.5 {
        /*PayloadTypeRegistry.playS2C().register(type.type(), type.codec());
        *///?}
    }

    private static <T extends ModPayload> void registerC2S(PayloadType<T> type, ServerHandler<T> handler) {
        //? if >=26.1 {
        /*PayloadTypeRegistry.serverboundPlay().register(type.type(), type.codec());
        *///?} else if >=1.20.5 {
        /*PayloadTypeRegistry.playC2S().register(type.type(), type.codec());
        *///?}

        //? if >=1.20.5 {
        /*ServerPlayNetworking.registerGlobalReceiver(type.type(), (payload, context) -> handler.handle(payload, CheeseBridge.getServer(context.player()), context.player()));
        *///?} else {
        ServerPlayNetworking.registerGlobalReceiver(type.id(), (server, player, listener, buf, sender) -> handler.handle(type.decode(buf), server, player));
        //?}
    }

    @Environment(EnvType.CLIENT)
    private static <T extends ModPayload> void registerS2C(PayloadType<T> type, ClientHandler<T> handler) {
        //? if >=1.20.5 {
        /*ClientPlayNetworking.registerGlobalReceiver(type.type(), (payload, context) -> handler.handle(payload, context.client()));
        *///?} else {
        ClientPlayNetworking.registerGlobalReceiver(type.id(), (client, listener, buf, sender) -> handler.handle(type.decode(buf), client));
        //?}
    }
}
