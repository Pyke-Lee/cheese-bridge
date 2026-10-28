package kr.pyke.integration.event;

import kr.pyke.integration.DonationEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public interface DonationReceivedCallback {
    List<DonationReceivedCallback> LISTENERS = new CopyOnWriteArrayList<>();

    void onDonationReceived(ServerPlayer player, DonationEvent event);

    static void register(DonationReceivedCallback listener) {
        LISTENERS.add(listener);
    }

    static void registerHandler(BiConsumer<ServerPlayer, DonationEvent> handler) {
        register(handler::accept);
    }

    static void invoke(ServerPlayer player, DonationEvent event) {
        for (DonationReceivedCallback listener : LISTENERS) {
            listener.onDonationReceived(player, event);
        }
    }
}
