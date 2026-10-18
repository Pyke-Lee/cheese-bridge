package kr.pyke.network;

import net.minecraft.network.FriendlyByteBuf;
//? if >=1.20.5 {
/*import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
*///?}

//? if >=1.20.5 {
/*public interface ModPayload extends CustomPacketPayload {
    @Override
    default Type<? extends CustomPacketPayload> type() { return payloadType().type(); }

*///?} else {
public interface ModPayload {
//?}
    PayloadType<?> payloadType();

    void write(FriendlyByteBuf buf);
}
