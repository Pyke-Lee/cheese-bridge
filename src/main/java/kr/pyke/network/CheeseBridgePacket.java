package kr.pyke.network;

import kr.pyke.network.payload.c2s.C2S_AuthCodePayload;
import kr.pyke.network.payload.c2s.C2S_DonationPayload;
import kr.pyke.network.payload.c2s.C2S_RequestRefreshPayload;
import kr.pyke.network.payload.s2c.S2C_AuthUrlPayload;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class CheeseBridgePacket {
    private CheeseBridgePacket() { }

    public static void registerCodec() {
        // S2C (Server → Client)
        PayloadTypeRegistry.clientboundPlay().register(S2C_AuthUrlPayload.ID, S2C_AuthUrlPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(S2C_FinalTokenPayload.ID, S2C_FinalTokenPayload.STREAM_CODEC);

        // C2S (Client → Server)
        PayloadTypeRegistry.serverboundPlay().register(C2S_DonationPayload.ID, C2S_DonationPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(C2S_AuthCodePayload.ID, C2S_AuthCodePayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(C2S_RequestRefreshPayload.ID, C2S_RequestRefreshPayload.STREAM_CODEC);
    }

    public static void registerServer() {
        // C2S_DonationPayload
        ServerPlayNetworking.registerGlobalReceiver(C2S_DonationPayload.ID, C2S_DonationPayload::handle);
        // C2S_AuthCodePayload
        ServerPlayNetworking.registerGlobalReceiver(C2S_AuthCodePayload.ID, C2S_AuthCodePayload::handle);
        // C2S_RequestRefreshPayload
        ServerPlayNetworking.registerGlobalReceiver(C2S_RequestRefreshPayload.ID, C2S_RequestRefreshPayload::handle);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        // S2C_AuthUrlPayload
        ClientPlayNetworking.registerGlobalReceiver(S2C_AuthUrlPayload.ID, S2C_AuthUrlPayload::handle);
        // S2C_FinalTokenPayload
        ClientPlayNetworking.registerGlobalReceiver(S2C_FinalTokenPayload.ID, S2C_FinalTokenPayload::handle);
    }
}
