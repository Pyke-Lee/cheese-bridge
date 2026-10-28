package kr.pyke.network;

import kr.pyke.CheeseBridge;
import kr.pyke.network.payload.c2s.C2S_AuthCodePayload;
import kr.pyke.network.payload.c2s.C2S_DonationPayload;
import kr.pyke.network.payload.c2s.C2S_LinkStatusPayload;
import kr.pyke.network.payload.c2s.C2S_RequestRefreshPayload;
import kr.pyke.network.payload.s2c.S2C_AuthUrlPayload;
import kr.pyke.network.payload.s2c.S2C_CheckLinkPayload;
import kr.pyke.network.payload.s2c.S2C_FinalTokenPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class CheeseBridgePacket {
    private static final String PROTOCOL_VERSION = "1";

    // 상대편에 모드가 없어도 접속 가능 (Fabric 버전과 동일한 동작)
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(CheeseBridge.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
        NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)
    );

    private static int nextId = 0;

    private CheeseBridgePacket() { }

    @FunctionalInterface
    public interface ServerHandler<T> {
        void handle(T payload, MinecraftServer server, ServerPlayer player);
    }

    public static void register() {
        // C2S (Client → Server)
        registerC2S(C2S_DonationPayload.class, C2S_DonationPayload::encode, C2S_DonationPayload::decode, C2S_DonationPayload::handle);
        registerC2S(C2S_AuthCodePayload.class, C2S_AuthCodePayload::encode, C2S_AuthCodePayload::decode, C2S_AuthCodePayload::handle);
        registerC2S(C2S_RequestRefreshPayload.class, C2S_RequestRefreshPayload::encode, C2S_RequestRefreshPayload::decode, C2S_RequestRefreshPayload::handle);
        registerC2S(C2S_LinkStatusPayload.class, C2S_LinkStatusPayload::encode, C2S_LinkStatusPayload::decode, C2S_LinkStatusPayload::handle);

        // S2C (Server → Client): 핸들러는 클라이언트에서만 호출됨
        registerS2C(S2C_AuthUrlPayload.class, S2C_AuthUrlPayload::encode, S2C_AuthUrlPayload::decode, S2C_AuthUrlPayload::handle);
        registerS2C(S2C_FinalTokenPayload.class, S2C_FinalTokenPayload::encode, S2C_FinalTokenPayload::decode, S2C_FinalTokenPayload::handle);
        registerS2C(S2C_CheckLinkPayload.class, S2C_CheckLinkPayload::encode, S2C_CheckLinkPayload::decode, S2C_CheckLinkPayload::handle);
    }

    public static void sendToPlayer(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
    }

    private static <T> void registerC2S(Class<T> type, BiConsumer<FriendlyByteBuf, T> encoder, Function<FriendlyByteBuf, T> decoder, ServerHandler<T> handler) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
            .encoder((payload, buf) -> encoder.accept(buf, payload))
            .decoder(decoder)
            .consumerNetworkThread((payload, context) -> {
                ServerPlayer player = context.get().getSender();
                if (player != null) { handler.handle(payload, player.server, player); }
                context.get().setPacketHandled(true);
            })
            .add();
    }

    private static <T> void registerS2C(Class<T> type, BiConsumer<FriendlyByteBuf, T> encoder, Function<FriendlyByteBuf, T> decoder, Consumer<T> handler) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder((payload, buf) -> encoder.accept(buf, payload))
            .decoder(decoder)
            .consumerNetworkThread((payload, context) -> {
                handler.accept(payload);
                context.get().setPacketHandled(true);
            })
            .add();
    }
}
