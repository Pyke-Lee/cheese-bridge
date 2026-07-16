package kr.pyke.client.state;

import kr.pyke.type.PLATFORM;

public record ConnectionStatus(PLATFORM platform, boolean tokenExists, boolean socketConnected, boolean eventSubscribed, String detail) {
    public enum State { CONNECTED, DISCONNECTED, TOKEN_EXPIRED, ERROR }

    public State state() {
        if (socketConnected && eventSubscribed) { return State.CONNECTED; }
        if (!tokenExists) { return State.DISCONNECTED; }
        if (detail != null && detail.contains("만료")) { return State.TOKEN_EXPIRED; }

        return State.ERROR;
    }

    public String summary() {
        return switch (state()) {
            case CONNECTED -> platform.name() + " 후원 연동 정상";
            case DISCONNECTED -> platform.name() + " 연동 안 됨";
            case TOKEN_EXPIRED -> platform.name() + " 토큰 만료";
            case ERROR -> platform.name() + " 연결 오류" + (detail != null ? " (" + detail + ")" : "");
        };
    }
}
