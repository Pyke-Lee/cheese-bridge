package kr.pyke.client;

import kr.pyke.client.chzzk.ChzzkManager;
import kr.pyke.client.soop.SoopManager;
import kr.pyke.network.payload.c2s.C2S_LinkStatusPayload;

public final class LinkCheckClient {
    private static final long POLL_TIMEOUT_MS = 10_000;
    private static final long POLL_INTERVAL_MS = 250;

    private LinkCheckClient() { }

    public static void runCheck(String requestId) {
        Thread t = new Thread(() -> {
            String result = buildResult();
            C2S_LinkStatusPayload.send(new C2S_LinkStatusPayload(requestId, result));
        }, "CheeseBridge-LinkCheck");
        t.setDaemon(true);
        t.start();
    }

    private static String buildResult() {
        SoopManager soop = SoopManager.getInstance();
        ChzzkManager chzzk = ChzzkManager.getInstance();

        boolean soopHas = soop.hasToken();
        boolean chzzkHas = chzzk.hasToken();
        if (!soopHas && !chzzkHas) { return "(미연동)"; }

        SoopManager.LinkState soopPrev = null;
        ChzzkManager.LinkState chzzkPrev = null;
        String soopReason = null;
        String chzzkReason = null;

        if (soopHas) {
            soopPrev = soop.getLastKnownStatus();
            soopReason = soop.getLastCloseReason();
            soop.forceReconnect();
        }
        if (chzzkHas) {
            chzzkPrev = chzzk.getLastKnownStatus();
            chzzkReason = chzzk.getLastCloseReason();
            chzzk.forceReconnect();
        }

        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            boolean soopDone = !soopHas || soop.isConnected();
            boolean chzzkDone = !chzzkHas || chzzk.isConnected();
            if (soopDone && chzzkDone) { break; }
            try { Thread.sleep(POLL_INTERVAL_MS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }

        StringBuilder sb = new StringBuilder();
        if (soopHas) {
            sb.append("SOOP:").append(judge(soop.isConnected(), soopPrev == SoopManager.LinkState.CONNECTED,
                    soop.getLastKnownStatus() == SoopManager.LinkState.TOKEN_EXPIRED, soopReason));
        }
        if (chzzkHas) {
            if (!sb.isEmpty()) { sb.append("  "); }
            sb.append("CHZZK:").append(judge(chzzk.isConnected(), chzzkPrev == ChzzkManager.LinkState.CONNECTED,
                    chzzk.getLastKnownStatus() == ChzzkManager.LinkState.TOKEN_EXPIRED, chzzkReason));
        }
        return sb.toString();
    }

    // 결과 판정
    // - 지금 연결됨 + 이전에도 연결됨      -> 정상
    // - 지금 연결됨 + 이전에 끊겨 있었음    -> 재연결(사유)
    // - 지금 미연결 + 만료 상태            -> 토큰 재발급 중 (기존 refresh 경로가 백그라운드로 진행)
    // - 지금 미연결 그 외                  -> 오류(사유)
    private static String judge(boolean nowConnected, boolean wasConnected, boolean tokenExpired, String reason) {
        if (nowConnected) {
            if (wasConnected) { return "정상"; }
            return "재연결(" + reason + ")";
        }
        if (tokenExpired) { return "토큰 재발급 중"; }

        return "오류(" + reason + ")";
    }
}