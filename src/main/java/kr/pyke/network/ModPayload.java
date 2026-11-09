package kr.pyke.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface ModPayload extends CustomPacketPayload {
    @Override
    default Type<? extends CustomPacketPayload> type() { return payloadType().type(); }

    PayloadType<?> payloadType();

    void write(FriendlyByteBuf buf);
}
