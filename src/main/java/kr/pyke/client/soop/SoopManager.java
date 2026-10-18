package kr.pyke.client.soop;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import kr.pyke.CheeseBridge;
import kr.pyke.client.CheeseBridgeClient;
import kr.pyke.network.payload.c2s.C2S_DonationPayload;
import kr.pyke.network.payload.c2s.C2S_RequestRefreshPayload;
import kr.pyke.type.PLATFORM;
import kr.pyke.util.SoopProtocol;
import net.minecraft.client.Minecraft;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.extensions.IExtension;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.IProtocol;
import org.java_websocket.protocols.Protocol;

import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SoopManager {
    private static final SoopManager INSTANCE = new SoopManager();
    private static final String SOOPLIVE_ROOT_DOMAIN = "sooplive.com";
    private static final String CHAT_INFO_URL = "https://openapi.sooplive.com/broad/access/chatinfo";
    private static final String CHAT_SUBPROTOCOL = "chat";

    // 재연결 설정
    private static final long KEEPALIVE_SEC = 30;          // 기존 60 -> 30초로 단축
    private static final long RECONNECT_BASE_SEC = 3;      // 첫 재시도 지연
    private static final long RECONNECT_MAX_SEC = 60;      // 최대 지연(상한)
    private static final int  MAX_REFRESH_RETRY = 1;       // 한 끊김당 토큰 갱신 1회 시도
    private static final long WATCHDOG_TIMEOUT_MS = 90_000; // 마지막 수신 후 이 시간 지나면 강제 재연결
    private static final long SUPERVISOR_TICK_MS = 5_000;
    private static final long LOGIN_TIMEOUT_MS = 20_000;
    private static final long ROOM_CHECK_MS = 60_000;
    private static final long ATTEMPT_TIMEOUT_MS = 30_000;
    private static final long REFRESH_TIMEOUT_MS = 60_000;
    private static final long HTTP_TIMEOUT_SEC = 10;
    private static final int  WS_CONNECT_TIMEOUT_MS = 10_000;
    private static final long CONTROL_WAIT_MS = 3_000;

    // 연동 상태 (연동확인 기능 내부용)
    public enum LinkState { CONNECTED, DISCONNECTED, TOKEN_EXPIRED, NO_TOKEN, ERROR }

    private enum ChatInfoStatus { OK, TOKEN_EXPIRED, NOT_ON_AIR, API_ERROR, TRANSIENT, FAILED }

    private record ChatInfo(String chatIp, int chatPort, String chatNo, String ticket, String bjId, String broadNo) { }

    private record ChatInfoResult(ChatInfoStatus status, ChatInfo info, int resultCode, String detail) {
        static ChatInfoResult of(ChatInfoStatus status, int resultCode, String detail) {
            return new ChatInfoResult(status, null, resultCode, detail);
        }
    }

    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SEC))
            .build();

    private volatile Thread schedulerThread;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Soop-Scheduler-Thread");
        t.setDaemon(true);
        schedulerThread = t;
        return t;
    });
    private final ExecutorService httpWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Soop-Connect-Thread");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService socketCloser = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Soop-Close-Thread");
        t.setDaemon(true);
        return t;
    });

    private volatile SoopConnection connection;
    private ScheduledFuture<?> reconnectTask;
    private long attemptSeq = 0L;
    private boolean attemptInFlight = false;
    private long attemptStartedAt = 0L;
    private long refreshRequestedAt = 0L;
    private boolean parked = false;

    // 상태 관리
    private volatile String accessToken;                // 현재 사용 중인 토큰(재연결에 재사용)
    private volatile boolean manualDisconnect = false;  // 사용자가 의도적으로 끊었는지
    private volatile int reconnectAttempts = 0;         // 연속 실패 횟수(백오프 계산용)
    private volatile int refreshAttempts = 0;           // 이번 끊김 사이클에서 갱신 시도 횟수
    private volatile boolean announceOnJoin = false;    // 사용자가 직접 연동했을 때만 "연동 성공" 메시지 노출
    private volatile boolean silentReconnect = false;   // 토큰 갱신 등 내부 복구로 connect()가 재호출될 때 메시지 억제

    // 연동확인 지원용 상태 스냅샷
    private volatile LinkState lastKnownStatus = LinkState.DISCONNECTED; // onOpen/onClose/watchdog/만료가 갱신하는 마지막 상태
    private volatile String lastCloseReason = "";       // 마지막으로 끊긴 사유(연동확인 표기용)

    private SoopManager() {
        scheduler.scheduleWithFixedDelay(this::supervise, SUPERVISOR_TICK_MS, SUPERVISOR_TICK_MS, TimeUnit.MILLISECONDS);
    }

    public static SoopManager getInstance() { return INSTANCE; }

    private static String buildWsUrl(String chatIp, int chatPort, String bjId) {
        String[] octets = chatIp.split("\\.");
        String hexIp = String.format("%02X%02X%02X%02X",
                Integer.parseInt(octets[0]),
                Integer.parseInt(octets[1]),
                Integer.parseInt(octets[2]),
                Integer.parseInt(octets[3]));
        return String.format("wss://chat-%s.%s:%d/Websocket/%s", hexIp, SOOPLIVE_ROOT_DOMAIN, chatPort + 1, bjId);
    }

    public void connect(String accessToken) {
        this.accessToken = accessToken;
        runOnScheduler(() -> {
            this.manualDisconnect = false;
            this.parked = false;
            this.refreshRequestedAt = 0L;
            this.reconnectAttempts = 0;
            this.refreshAttempts = 0;
            // 토큰 갱신 등 내부 복구로 인한 재연결이면 메시지를 띄우지 않는다.
            this.announceOnJoin = !silentReconnect;
            this.silentReconnect = false;
            startAttempt();
        });
    }

    public void disconnect() {
        runOnScheduler(() -> {
            this.manualDisconnect = true;
            cancelReconnect();
            invalidateAttempt();
            this.refreshRequestedAt = 0L;
            abandonConnection(false);
            this.lastKnownStatus = LinkState.DISCONNECTED;  // 수동 해제 기록
            CheeseBridge.LOGGER.info("숲(SOOP) 연결 해제됨.");
        });
    }

    public void forceReconnect() {
        if (accessToken == null) { return; }

        awaitOnScheduler(() -> {
            if (manualDisconnect) { return; }

            this.silentReconnect = true;
            this.parked = false;
            this.reconnectAttempts = 0;
            cancelReconnect();
            invalidateAttempt();
            abandonConnection(false);
            scheduleReconnect("manual /연동확인");
        });
    }

    public boolean hasToken() { return accessToken != null; }

    public boolean isConnected() {
        SoopConnection c = connection;
        return c != null && c.isOpen() && c.loggedIn;
    }

    public LinkState getLastKnownStatus() {
        if (accessToken == null) { return LinkState.NO_TOKEN; }

        return lastKnownStatus;
    }

    public String getLastCloseReason() {
        return (lastCloseReason == null || lastCloseReason.isEmpty()) ? "끊김 복구" : lastCloseReason;
    }

    private void startAttempt() {
        cancelReconnect();
        abandonConnection(false);
        if (manualDisconnect) { return; }

        final long attemptId = ++attemptSeq;
        final String token = this.accessToken;
        attemptInFlight = true;
        attemptStartedAt = System.currentTimeMillis();
        CheeseBridge.LOGGER.info("[SOOP] 연결 프로세스 시작... (시도 #{})", reconnectAttempts + 1);

        httpWorker.execute(() -> {
            ChatInfoResult result = fetchChatInfo(token, true);
            runOnScheduler(() -> onChatInfo(attemptId, result));
        });
    }

    private void invalidateAttempt() {
        attemptSeq++;
        attemptInFlight = false;
    }

    private void onChatInfo(long attemptId, ChatInfoResult result) {
        if (attemptId != attemptSeq || !attemptInFlight) { return; }

        attemptInFlight = false;
        if (manualDisconnect) { return; }

        if (result.resultCode() != 0) { CheeseBridge.LOGGER.info("[SOOP] 수신된 resultCode: {}", result.resultCode()); }

        switch (result.status()) {
            case OK -> openConnection(attemptId, result.info());
            case TOKEN_EXPIRED -> handleTokenExpired(result.detail());
            case NOT_ON_AIR -> handleNotOnAir();
            case API_ERROR -> handleApiError(result);
            case TRANSIENT -> scheduleReconnect(result.detail());
            case FAILED -> {
                lastKnownStatus = LinkState.ERROR;                     // 연결 예외 기록
                lastCloseReason = "연결 예외";
                scheduleReconnect(result.detail());
            }
        }
    }

    private void openConnection(long attemptId, ChatInfo info) {
        String wsUrl = buildWsUrl(info.chatIp(), info.chatPort(), info.bjId());
        CheeseBridge.LOGGER.info("[SOOP] WebSocket 연결 시도: {}", wsUrl);

        SoopConnection c = new SoopConnection(attemptId, URI.create(wsUrl), info);
        connection = c;
        try { c.connect(); }
        catch (Exception e) {
            CheeseBridge.LOGGER.error("[SOOP] 연결 중 예외", e);
            lastKnownStatus = LinkState.ERROR;
            lastCloseReason = "연결 예외";
            abandonConnection(true);
            scheduleReconnect("exception: " + e.getMessage());
        }
    }

    private void handleNotOnAir() {
        if (announceOnJoin) {
            CheeseBridge.LOGGER.warn("[SOOP] 연동 실패: 방송 중이 아님");
            parked = true;
            sendChat("숲(SOOP) 연동 실패: 생방송 중일 때만 연동이 가능합니다.");
            return;
        }

        CheeseBridge.LOGGER.warn("[SOOP] 방송 중이 아님 -> 방송 재개 대기");
        lastKnownStatus = LinkState.DISCONNECTED;
        lastCloseReason = "방송 재개 대기";
        scheduleReconnect("not on air");
    }

    private void handleApiError(ChatInfoResult result) {
        CheeseBridge.LOGGER.error("[SOOP] API 상세 에러: {} (코드: {})", result.detail(), result.resultCode());
        sendChat("숲(SOOP) API 오류: " + result.detail());
        scheduleReconnect("result " + result.resultCode());
    }

    private void handleTokenExpired(String reason) {
        if (manualDisconnect) { return; }

        lastKnownStatus = LinkState.TOKEN_EXPIRED;   // 만료 감지 기록(연동확인 표기용)
        lastCloseReason = "토큰 재발급";

        if (refreshAttempts >= MAX_REFRESH_RETRY) {
            CheeseBridge.LOGGER.warn("[SOOP] 토큰 갱신 재시도 한도 초과({}). 재인증 필요.", reason);
            parked = true;
            sendChat("숲(SOOP) 인증이 만료되었습니다. /후원연동 숲 으로 다시 연결해주세요.");
            return;
        }

        refreshAttempts++;
        this.silentReconnect = true;  // 갱신 성공 후 따라오는 connect()는 재연결이므로 메시지 억제
        this.refreshRequestedAt = System.currentTimeMillis();
        CheeseBridge.LOGGER.warn("[SOOP] 토큰 만료 감지({}) -> 갱신 요청", reason);
        // 서버에 갱신 요청. 서버는 갱신 후 S2C_FinalTokenPayload 로 새 토큰을 내려보내고,
        // 클라이언트의 S2C_FinalTokenPayload.handle 에서 SoopManager.connect(newToken) 이 다시 호출된다.
        try { C2S_RequestRefreshPayload.send(new C2S_RequestRefreshPayload(PLATFORM.SOOP.name())); }
        catch (Exception e) { CheeseBridge.LOGGER.error("[SOOP] 토큰 갱신 요청 전송 실패", e); }
    }

    private void scheduleReconnect(String reason) {
        if (manualDisconnect) { return; }
        if (reconnectTask != null) { return; } // 이미 예약돼 있으면 중복 방지

        long delay = Math.min(RECONNECT_BASE_SEC * (1L << Math.min(reconnectAttempts, 5)), RECONNECT_MAX_SEC);
        reconnectAttempts++;
        CheeseBridge.LOGGER.info("[SOOP] {}초 후 재연결 예약 (사유: {})", delay, reason);

        reconnectTask = scheduler.schedule(() -> {
            reconnectTask = null;
            startAttempt();
        }, delay, TimeUnit.SECONDS);
    }

    private void cancelReconnect() {
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }

    private void abandonConnection(boolean hard) {
        SoopConnection c = connection;
        if (c == null) { return; }

        connection = null;
        c.shutdown(hard);
    }

    private void recover(SoopConnection c, String reconnectReason) {
        if (connection != c) { return; }

        abandonConnection(true);
        scheduleReconnect(reconnectReason);
    }

    private void supervise() {
        try {
            long now = System.currentTimeMillis();
            SoopConnection c = connection;
            if (c != null) { superviseConnection(c, now); }
            else { superviseIdle(now); }
        }
        catch (Throwable t) {
            CheeseBridge.LOGGER.error("[SOOP] 감시 작업 중 오류", t);
        }
    }

    private void superviseConnection(SoopConnection c, long now) {
        if (!c.loggedIn) {
            if (now - c.createdAt > LOGIN_TIMEOUT_MS) {
                CheeseBridge.LOGGER.warn("[SOOP] {}ms 내 로그인 응답 없음 -> 강제 재연결", LOGIN_TIMEOUT_MS);
                lastKnownStatus = LinkState.DISCONNECTED;
                lastCloseReason = "로그인 응답 없음";
                recover(c, "login timeout");
            }
            return;
        }

        // 워치독: 일정 시간 무수신이면 half-open 등으로 판단하고 강제 재연결
        if (now - c.lastMessageAt > WATCHDOG_TIMEOUT_MS) {
            CheeseBridge.LOGGER.warn("[SOOP] 워치독: {}ms 무수신 -> 강제 재연결", WATCHDOG_TIMEOUT_MS);
            lastKnownStatus = LinkState.DISCONNECTED;   // 워치독 강제 종료 기록
            lastCloseReason = "무응답 복구";             // half-open 감지 재연결
            recover(c, "watchdog timeout");
            return;
        }

        if (now - c.lastKeepAliveAt >= TimeUnit.SECONDS.toMillis(KEEPALIVE_SEC)) {
            c.lastKeepAliveAt = now;
            c.sendKeepAlive();
        }

        if (now - c.lastRoomCheckAt >= ROOM_CHECK_MS) {
            c.lastRoomCheckAt = now;
            requestRoomCheck(c);
        }
    }

    private void superviseIdle(long now) {
        if (manualDisconnect || parked || accessToken == null || reconnectTask != null) { return; }

        if (attemptInFlight) {
            if (now - attemptStartedAt > ATTEMPT_TIMEOUT_MS) {
                CheeseBridge.LOGGER.warn("[SOOP] 연결 시도 응답 없음 -> 재시도");
                invalidateAttempt();
                scheduleReconnect("attempt timeout");
            }
            return;
        }

        if (refreshRequestedAt > 0L) {
            if (now - refreshRequestedAt > REFRESH_TIMEOUT_MS) {
                CheeseBridge.LOGGER.warn("[SOOP] 토큰 갱신 응답 없음 -> 재시도");
                refreshRequestedAt = 0L;
                scheduleReconnect("refresh timeout");
            }
            return;
        }

        CheeseBridge.LOGGER.warn("[SOOP] 연결도 예약도 없는 상태 감지 -> 재연결");
        scheduleReconnect("idle recovery");
    }

    private void requestRoomCheck(SoopConnection c) {
        final String token = this.accessToken;
        httpWorker.execute(() -> {
            ChatInfoResult result = fetchChatInfo(token, false);
            runOnScheduler(() -> onRoomCheck(c, result));
        });
    }

    private void onRoomCheck(SoopConnection c, ChatInfoResult result) {
        if (connection != c || result.status() != ChatInfoStatus.OK) { return; }
        if (c.isSameRoom(result.info())) { return; }

        CheeseBridge.LOGGER.warn("[SOOP] 채팅방 변경 감지(방송 재시작) -> 재연결");
        lastKnownStatus = LinkState.DISCONNECTED;
        lastCloseReason = "방송 재시작 복구";
        reconnectAttempts = 0;
        abandonConnection(false);
        scheduleReconnect("room changed");
    }

    private void onSocketOpened(SoopConnection c) {
        if (connection != c) {
            c.shutdown(true);
            return;
        }

        CheeseBridge.LOGGER.info("[SOOP] 소켓 연결됨. 로그인 시도.");
        c.sendLogin();
    }

    private void onLoggedIn(SoopConnection c) {
        if (connection != c || c.loggedIn) { return; }

        c.loggedIn = true;
        c.lastMessageAt = System.currentTimeMillis();
        this.reconnectAttempts = 0;  // 연결 성공 -> 백오프 리셋
        this.refreshAttempts = 0;
        this.lastKnownStatus = LinkState.CONNECTED;  // 로그인 승인 = 정상 연결 기록
        CheeseBridge.LOGGER.info("[SOOP] 로그인 승인됨. 채널 입장 시도.");
        c.sendJoin();
        if (announceOnJoin) {
            announceOnJoin = false;  // 최초 연동 시 1회만 노출, 이후 재연결에서는 표시 안 함
            sendChat("숲(SOOP) 연동 성공!");
        }
    }

    private void onBroadcastClosed(SoopConnection c) {
        if (connection != c) { return; }

        CheeseBridge.LOGGER.warn("[SOOP] 방송 종료 신호(SVC {}) 수신 -> 채팅 정보 재확인", SoopProtocol.SVC_CLOSE_BROAD);
        lastKnownStatus = LinkState.DISCONNECTED;
        lastCloseReason = "방송 재시작 복구";
        reconnectAttempts = 0;
        abandonConnection(false);
        scheduleReconnect("broadcast closed");
    }

    private void onSocketClosed(SoopConnection c, int code, String reason, boolean remote) {
        if (connection != c) {
            CheeseBridge.LOGGER.info("[SOOP] 이전 소켓 종료 이벤트 무시 - 코드: {}", code);
            return;
        }

        CheeseBridge.LOGGER.warn("[SOOP] 소켓 닫힘 - 코드: {}, 사유: {}, 원격여부: {}", code, reason, remote);
        lastKnownStatus = LinkState.DISCONNECTED;      // 소켓 닫힘 기록
        lastCloseReason = "끊김 복구";                  // 원격/로컬 종료로 인한 재연결
        connection = null;
        scheduleReconnect("socket closed(" + code + ")");
    }

    private void handleFrame(SoopConnection c, byte[] data) {
        if (data.length < SoopProtocol.HEADER_SIZE) { return; }
        if (SoopProtocol.readRetCode(data) < 0) { return; }

        int svc = SoopProtocol.readServiceCode(data);
        if (svc == SoopProtocol.SVC_CLOSE_BROAD) {
            runOnScheduler(() -> onBroadcastClosed(c));
            return;
        }

        if (data.length > SoopProtocol.HEADER_SIZE) {
            String bodyOnly = new String(data, SoopProtocol.HEADER_SIZE, data.length - SoopProtocol.HEADER_SIZE, StandardCharsets.UTF_8);
            handlePacket(c, svc, bodyOnly);
        }
    }

    private void handlePacket(SoopConnection c, int svc, String body) {
        try {
            List<String> parts = SoopProtocol.parseBody(body);
            if (parts.isEmpty()) { return; }

            if (svc == SoopProtocol.SVC_LOGIN) {
                if (!c.loginReceived) {
                    c.loginReceived = true;
                    runOnScheduler(() -> onLoggedIn(c));
                }
                return;
            }

            // 수신 body 는 항상 구분자(\f)로 시작하므로 parts.get(0) 은 빈 문자열 -> base=1.
            int base = parts.get(0).isEmpty() ? 1 : 0;

            // 일반 별풍선: SDK 기준 e[2]=nick, e[3]=count
            if (svc == SoopProtocol.SVC_SENDBALLOON) {
                emitBalloon(parts, base + 2, base + 3, "별풍선");
            }
            // 중계방 별풍선: SDK 기준 e[4]=nick, e[5]=count
            else if (svc == SoopProtocol.SVC_SENDBALLOONSUB) {
                emitBalloon(parts, base + 4, base + 5, "별풍선");
            }
            // 영상풍선: SDK 기준 e[3]=nick, e[4]=count
            else if (svc == SoopProtocol.SVC_VIDEO_BALLOON) {
                emitBalloon(parts, base + 3, base + 4, "영상별풍선");
            }
            else if (svc == SoopProtocol.SVC_MISSION) {
                String jsonStr = parts.get(base);
                if (jsonStr == null || !jsonStr.trim().startsWith("{")) {
                    jsonStr = null;
                    for (String p : parts) {
                        if (p != null && p.trim().startsWith("{")) { jsonStr = p; break; }
                    }
                }
                if (jsonStr != null) { handleMission(jsonStr); }
            }
        }
        catch (Exception e) {
            CheeseBridge.LOGGER.error("[SOOP] 패킷 처리 중 오류: ", e);
        }
    }

    private void emitBalloon(List<String> parts, int nickIdx, int countIdx, String donationType) {
        if (parts.size() <= countIdx || parts.size() <= nickIdx) { return; }

        String nickname = parts.get(nickIdx);
        String amount = parts.get(countIdx);

        int count;
        try { count = Integer.parseInt(amount.trim()); }
        catch (NumberFormatException e) { return; }
        if (count <= 0) { return; }

        CheeseBridge.LOGGER.info("[SOOP] {} 감지: {} ({}개)", donationType, nickname, count);
        C2S_DonationPayload.send(new C2S_DonationPayload(nickname, String.valueOf(count), donationType, "SOOP"));
    }

    private void handleMission(String jsonStr) {
        try {
            JsonObject m = gson.fromJson(jsonStr, JsonObject.class);
            if (m == null || !m.has("type")) { return; }
            String type = m.get("type").getAsString();

            switch (type) {
                case "SETTLE":
                case "CHALLENGE_SETTLE":
                    emitMissionSettle(m, type.equals("SETTLE") ? "대결미션정산" : "도전미션정산");
                    break;
                default:
                    break;
            }
        }
        catch (Exception e) {
            CheeseBridge.LOGGER.error("[SOOP] 미션 JSON 파싱 오류: ", e);
        }
    }

    private void emitMissionSettle(JsonObject m, String label) {
        if (!m.has("settle_count")) { return; }

        int count;
        try { count = Integer.parseInt(m.get("settle_count").getAsString().trim()); }
        catch (Exception e) { return; }
        if (count <= 0) { return; }

        CheeseBridge.LOGGER.info("[SOOP] {} 감지: 총 {}개", label, count);
        C2S_DonationPayload.send(new C2S_DonationPayload(label, String.valueOf(count), label, "SOOP"));
    }

    private ChatInfoResult fetchChatInfo(String token, boolean logErrors) {
        CompletableFuture<HttpResponse<String>> future = null;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CHAT_INFO_URL))
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SEC))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("access_token=" + token))
                    .build();

            future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> response = future.get(HTTP_TIMEOUT_SEC * 2, TimeUnit.SECONDS);
            return parseChatInfo(response, logErrors);
        }
        catch (TimeoutException e) {
            future.cancel(true);
            if (logErrors) { CheeseBridge.LOGGER.error("[SOOP] 채팅 정보 요청 시간 초과"); }
            return ChatInfoResult.of(ChatInfoStatus.FAILED, 0, "chatinfo timeout");
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ChatInfoResult.of(ChatInfoStatus.FAILED, 0, "interrupted");
        }
        catch (Exception e) {
            if (logErrors) { CheeseBridge.LOGGER.error("[SOOP] 연결 중 예외", e); }
            return ChatInfoResult.of(ChatInfoStatus.FAILED, 0, "exception: " + e.getMessage());
        }
    }

    private ChatInfoResult parseChatInfo(HttpResponse<String> response, boolean logErrors) {
        if (response.statusCode() != 200) {
            if (logErrors) { CheeseBridge.LOGGER.error("[SOOP] 채팅 정보 실패: {}", response.body()); }
            if (response.statusCode() == 401) { return ChatInfoResult.of(ChatInfoStatus.TOKEN_EXPIRED, 0, "HTTP 401"); }
            return ChatInfoResult.of(ChatInfoStatus.TRANSIENT, 0, "chatinfo HTTP " + response.statusCode());
        }

        JsonObject json = gson.fromJson(response.body(), JsonObject.class);

        if (json.has("error")) {
            String error = json.get("error").getAsString();
            if (error.equals("expired_token") || error.equals("invalid_token")) {
                return ChatInfoResult.of(ChatInfoStatus.TOKEN_EXPIRED, 0, error);
            }
        }

        if (!json.has("result")) {
            if (logErrors) { CheeseBridge.LOGGER.error("[SOOP] 응답에 result 필드가 없습니다: {}", response.body()); }
            return ChatInfoResult.of(ChatInfoStatus.TRANSIENT, 0, "no result field");
        }

        int resultCode = json.get("result").getAsInt();

        if (resultCode != 1) {
            String errorMsg = json.has("msg") ? json.get("msg").getAsString() : "Unknown Error";

            if (resultCode == -10) { return ChatInfoResult.of(ChatInfoStatus.TOKEN_EXPIRED, resultCode, "result -10"); }
            if (resultCode == -1302) { return ChatInfoResult.of(ChatInfoStatus.NOT_ON_AIR, resultCode, errorMsg); }
            return ChatInfoResult.of(ChatInfoStatus.API_ERROR, resultCode, errorMsg);
        }

        JsonObject data = json.getAsJsonArray("data").get(0).getAsJsonObject();
        JsonElement idElement = data.get("id");
        String bjId = idElement.isJsonObject()
                ? idElement.getAsJsonObject().get("userId").getAsString()
                : idElement.getAsString();
        String broadNo = data.has("broad_no") && !data.get("broad_no").isJsonNull() ? data.get("broad_no").getAsString() : "";

        ChatInfo info = new ChatInfo(
                data.get("chat_ip").getAsString(),
                data.get("chat_port").getAsInt(),
                data.get("chat_no").getAsString(),
                data.get("key").getAsString(),
                bjId,
                broadNo);

        return new ChatInfoResult(ChatInfoStatus.OK, info, resultCode, "");
    }

    private void sendChat(String message) {
        Minecraft.getInstance().execute(() -> CheeseBridgeClient.sendMessage(Minecraft.getInstance().player, message));
    }

    private void runOnScheduler(Runnable task) {
        if (Thread.currentThread() == schedulerThread) {
            task.run();
            return;
        }

        scheduler.execute(() -> {
            try { task.run(); }
            catch (Throwable t) { CheeseBridge.LOGGER.error("[SOOP] 상태 처리 중 오류", t); }
        });
    }

    private void awaitOnScheduler(Runnable task) {
        if (Thread.currentThread() == schedulerThread) {
            task.run();
            return;
        }

        Future<?> future = scheduler.submit(task);
        try { future.get(CONTROL_WAIT_MS, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (Exception e) { CheeseBridge.LOGGER.error("[SOOP] 상태 처리 대기 중 오류", e); }
    }

    private static Draft_6455 createDraft() {
        List<IProtocol> protocols = new ArrayList<>();
        protocols.add(new Protocol(CHAT_SUBPROTOCOL));
        protocols.add(new Protocol(""));
        return new Draft_6455(Collections.<IExtension>emptyList(), protocols);
    }

    private final class SoopConnection extends WebSocketClient {
        private final long id;
        private final ChatInfo info;
        private final long createdAt = System.currentTimeMillis();
        private volatile long lastMessageAt = createdAt;           // 마지막 패킷 수신 시각(워치독용)
        private long lastKeepAliveAt = createdAt;
        private long lastRoomCheckAt = createdAt;
        private volatile boolean loginReceived = false;
        private volatile boolean loggedIn = false;

        private SoopConnection(long id, URI uri, ChatInfo info) {
            super(uri, createDraft(), null, WS_CONNECT_TIMEOUT_MS);
            this.id = id;
            this.info = info;
        }

        private boolean isCurrent() {
            return connection == this;
        }

        private boolean isSameRoom(ChatInfo other) {
            if (!info.chatNo().equals(other.chatNo())) { return false; }
            if (info.broadNo().isEmpty() || other.broadNo().isEmpty()) { return true; }

            return info.broadNo().equals(other.broadNo());
        }

        private void sendLogin() {
            List<String> loginBody = new ArrayList<>();
            loginBody.add("");
            loginBody.add("");
            loginBody.add("16");
            sendSafely(SoopProtocol.makePacket(SoopProtocol.SVC_LOGIN, loginBody));
        }

        private void sendJoin() {
            List<String> joinBody = new ArrayList<>();
            joinBody.add(info.chatNo());
            joinBody.add(info.ticket());
            joinBody.add("5");
            joinBody.add("");
            joinBody.add("");
            sendSafely(SoopProtocol.makePacket(SoopProtocol.SVC_JOINCH, joinBody));
        }

        private void sendKeepAlive() {
            sendSafely(SoopProtocol.makePacket(SoopProtocol.SVC_KEEPALIVE, new ArrayList<>()));
        }

        private void sendSafely(byte[] packet) {
            try { send(packet); }
            catch (Exception e) { CheeseBridge.LOGGER.warn("[SOOP] 패킷 전송 실패(#{}): {}", id, e.getMessage()); }
        }

        private void shutdown(boolean hard) {
            socketCloser.execute(() -> closeNow(hard));
        }

        private void closeNow(boolean hard) {
            try {
                if (!hard && isOpen()) {
                    close();
                    return;
                }

                Socket socket = getSocket();
                if (socket != null) { socket.close(); }
                else { close(); }
            }
            catch (Exception ignored) { }
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            runOnScheduler(() -> onSocketOpened(this));
        }

        @Override
        public void onMessage(String message) {
            CheeseBridge.LOGGER.debug("[SOOP] 텍스트 프레임 수신: {}", message);
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            if (!isCurrent()) { return; }

            lastMessageAt = System.currentTimeMillis();
            byte[] data = new byte[bytes.remaining()];
            bytes.get(data);
            handleFrame(this, data);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            runOnScheduler(() -> onSocketClosed(this, code, reason, remote));
        }

        @Override
        public void onError(Exception ex) {
            CheeseBridge.LOGGER.error("[SOOP] 에러", ex);
        }
    }
}
