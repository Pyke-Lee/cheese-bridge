package kr.pyke.network;

import kr.pyke.CheeseBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Function;

public final class PayloadType<T extends ModPayload> {
    private final ResourceLocation id;
    private final Function<FriendlyByteBuf, T> decoder;
    private final CustomPacketPayload.Type<T> type;
    private final StreamCodec<FriendlyByteBuf, T> codec;

    private PayloadType(ResourceLocation id, Function<FriendlyByteBuf, T> decoder) {
        this.id = id;
        this.decoder = decoder;
        this.type = new CustomPacketPayload.Type<>(id);
        this.codec = CustomPacketPayload.codec(ModPayload::write, decoder::apply);
    }

    public static <T extends ModPayload> PayloadType<T> create(String path, Function<FriendlyByteBuf, T> decoder) {
        return new PayloadType<>(CheeseBridge.id(path), decoder);
    }

    public ResourceLocation id() { return id; }

    public T decode(FriendlyByteBuf buf) { return decoder.apply(buf); }

    public CustomPacketPayload.Type<T> type() { return type; }

    public StreamCodec<FriendlyByteBuf, T> codec() { return codec; }
}
