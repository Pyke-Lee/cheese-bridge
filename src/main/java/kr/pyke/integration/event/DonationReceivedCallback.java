package kr.pyke.integration.event;

import kr.pyke.integration.DonationEvent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

public interface DonationReceivedCallback {
    Event<DonationReceivedCallback> DONATION_RECEIVED = EventFactory.createArrayBacked(DonationReceivedCallback.class,
        (listeners) -> (player, event) -> {
            for (DonationReceivedCallback listener : listeners) {
                listener.onDonationReceived(player, event);
            }
        }
    );

    void onDonationReceived(ServerPlayer player, DonationEvent event);

    static void registerHandler(BiConsumer<ServerPlayer, DonationEvent> handler) {
        DONATION_RECEIVED.register(handler::accept);
    }
}
