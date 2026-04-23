package kr.pyke.network;

import kr.pyke.network.payload.c2s.C2S_AuthCodePayload;
import kr.pyke.network.payload.c2s.C2S_DonationPayload;
import kr.pyke.network.payload.c2s.C2S_RequestRefreshPayload;
import kr.pyke.network.payload.s2c.S2C_AuthUrlPayload;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class CheeseBridgePacket {
    private CheeseBridgePacket() { }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_DonationPayload.ID, C2S_DonationPayload::handle);
        ServerPlayNetworking.registerGlobalReceiver(C2S_AuthCodePayload.ID, C2S_AuthCodePayload::handle);
        ServerPlayNetworking.registerGlobalReceiver(C2S_RequestRefreshPayload.ID, C2S_RequestRefreshPayload::handle);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(S2C_AuthUrlPayload.ID, S2C_AuthUrlPayload::handle);
        ClientPlayNetworking.registerGlobalReceiver(S2C_FinalTokenPayload.ID, S2C_FinalTokenPayload::handle);
    }
}
