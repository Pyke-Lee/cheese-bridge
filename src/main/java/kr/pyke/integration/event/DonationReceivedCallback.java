package kr.pyke.integration.event;

import kr.pyke.integration.DonationEvent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

public interface DonationReceivedCallback {
    Event<DonationReceivedCallback> DONATION_RECEIVED = EventFactory.createArrayBacked(DonationReceivedCallback.class,
        (listeners) -> (player, event) -> {
            for (DonationReceivedCallback listener : listeners) {
                listener.onDonationReceived(player, event);
            }
        }
    );

    void onDonationReceived(ServerPlayer player, DonationEvent event);
}
