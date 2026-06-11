package kr.pyke.integration;

import kr.pyke.type.PLATFORM;

public record DonationEvent(String donor, String donationAmount, String donationMessage, PLATFORM platform) {
    public int getAmount() {
        try { return Integer.parseInt(donationAmount); }
        catch (Exception e) { return 0; }
    }
}