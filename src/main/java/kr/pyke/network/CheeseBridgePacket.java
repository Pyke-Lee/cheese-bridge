package kr.pyke.network;

import kr.pyke.CheeseBridge;
import kr.pyke.network.payload.c2s.C2S_AuthCodePayload;
import kr.pyke.network.payload.c2s.C2S_DonationPayload;
import kr.pyke.network.payload.c2s.C2S_LinkStatusPayload;
import kr.pyke.network.payload.c2s.C2S_RequestRefreshPayload;
import kr.pyke.network.payload.s2c.S2C_AuthUrlPayload;
import kr.pyke.network.payload.s2c.S2C_CheckLinkPayload;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class CheeseBridgePacket {
    private static final String PROTOCOL_VERSION = "1";

    private CheeseBridgePacket() { }

    @FunctionalInterface
    public interface ServerHandler<T> {
        void handle(T payload, MinecraftServer server, ServerPlayer player);
    }

    @FunctionalInterface
    public interface ClientHandler<T> {
        void handle(T payload, Minecraft client);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // 상대편에 모드가 없어도 접속 가능 (Fabric 버전과 동일한 동작)
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION)
            .optional()
            .executesOn(HandlerThread.NETWORK);

        // C2S (Client → Server)
        registerC2S(registrar, C2S_DonationPayload.TYPE, C2S_DonationPayload::handle);
        registerC2S(registrar, C2S_AuthCodePayload.TYPE, C2S_AuthCodePayload::handle);
        registerC2S(registrar, C2S_RequestRefreshPayload.TYPE, C2S_RequestRefreshPayload::handle);
        registerC2S(registrar, C2S_LinkStatusPayload.TYPE, C2S_LinkStatusPayload::handle);

        // S2C (Server → Client): 핸들러는 클라이언트에서만 호출됨
        registerS2C(registrar, S2C_AuthUrlPayload.TYPE, S2C_AuthUrlPayload::handle);
        registerS2C(registrar, S2C_FinalTokenPayload.TYPE, S2C_FinalTokenPayload::handle);
        registerS2C(registrar, S2C_CheckLinkPayload.TYPE, S2C_CheckLinkPayload::handle);
    }

    public static void sendToPlayer(ServerPlayer player, ModPayload payload) {
        if (!player.connection.hasChannel(payload)) { return; }
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToServer(ModPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static <T extends ModPayload> void registerC2S(PayloadRegistrar registrar, PayloadType<T> type, ServerHandler<T> handler) {
        registrar.playToServer(type.type(), type.codec(), (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) { handler.handle(payload, CheeseBridge.getServer(player), player); }
        });
    }

    private static <T extends ModPayload> void registerS2C(PayloadRegistrar registrar, PayloadType<T> type, ClientHandler<T> handler) {
        registrar.playToClient(type.type(), type.codec(), (payload, context) -> handler.handle(payload, Minecraft.getInstance()));
    }
}
