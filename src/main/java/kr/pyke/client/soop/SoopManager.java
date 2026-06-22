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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SoopManager {
    private static final SoopManager INSTANCE = new SoopManager();
    private static final String SOOPLIVE_ROOT_DOMAIN = "sooplive.com";

    // 재연결 설정
    private static final long KEEPALIVE_SEC = 30;          // 기존 60 -> 30초로 단축
    private static final long RECONNECT_BASE_SEC = 3;      // 첫 재시도 지연
    private static final long RECONNECT_MAX_SEC = 60;      // 최대 지연(상한)
    private static final int  MAX_REFRESH_RETRY = 1;       // 한 끊김당 토큰 갱신 1회 시도

    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private WebSocketClient webSocket;
    private ScheduledExecutorService keepAliveScheduler;
    private ScheduledExecutorService reconnectScheduler;

    private String chatNo, ticket, bjId;
    private volatile boolean isLoggedIn = false;

    // 상태 관리
    private volatile String accessToken;                // 현재 사용 중인 토큰(재연결에 재사용)
    private volatile boolean manualDisconnect = false;  // 사용자가 의도적으로 끊었는지
    private volatile int reconnectAttempts = 0;         // 연속 실패 횟수(백오프 계산용)
    private volatile int refreshAttempts = 0;           // 이번 끊김 사이클에서 갱신 시도 횟수
    private volatile boolean announceOnJoin = false;    // 사용자가 직접 연동했을 때만 "연동 성공" 메시지 노출
    private volatile boolean silentReconnect = false;   // 토큰 갱신 등 내부 복구로 connect()가 재호출될 때 메시지 억제

    private SoopManager() { }
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
        this.manualDisconnect = false;
        this.reconnectAttempts = 0;
        this.refreshAttempts = 0;
        // 토큰 갱신 등 내부 복구로 인한 재연결이면 메시지를 띄우지 않는다.
        this.announceOnJoin = !silentReconnect;
        this.silentReconnect = false;
        this.accessToken = accessToken;
        doConnect();
    }

    private synchronized void doConnect() {
        if (manualDisconnect) { return; }

        closeSocketOnly();
        this.isLoggedIn = false;
        CheeseBridge.LOGGER.info("[SOOP] 연결 프로세스 시작... (시도 #{})", reconnectAttempts + 1);

        final String token = this.accessToken;

        new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://openapi.sooplive.com/broad/access/chatinfo"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("access_token=" + token))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    CheeseBridge.LOGGER.error("[SOOP] 채팅 정보 실패: {}", response.body());
                    if (response.statusCode() == 401) { handleTokenExpired("HTTP 401"); }
                    else { scheduleReconnect("chatinfo HTTP " + response.statusCode()); }
                    return;
                }

                JsonObject json = gson.fromJson(response.body(), JsonObject.class);

                if (json.has("error")) {
                    String error = json.get("error").getAsString();
                    if (error.equals("expired_token") || error.equals("invalid_token")) {
                        handleTokenExpired(error);
                        return;
                    }
                }

                if (!json.has("result")) {
                    CheeseBridge.LOGGER.error("[SOOP] 응답에 result 필드가 없습니다: {}", response.body());
                    scheduleReconnect("no result field");
                    return;
                }

                int resultCode = json.get("result").getAsInt();
                CheeseBridge.LOGGER.info("[SOOP] 수신된 resultCode: {}", resultCode);

                if (resultCode != 1) {
                    String errorMsg = json.has("msg") ? json.get("msg").getAsString() : "Unknown Error";

                    if (resultCode == -10) {
                        CheeseBridge.LOGGER.warn("[SOOP] 인증 실패(-10) -> 토큰 갱신");
                        handleTokenExpired("result -10");
                    }
                    else if (resultCode == -1302) {
                        CheeseBridge.LOGGER.warn("[SOOP] 연동 실패: 방송 중이 아님");
                        Minecraft.getInstance().execute(() -> CheeseBridgeClient.sendMessage(Minecraft.getInstance().player, "숲(SOOP) 연동 실패: 생방송 중일 때만 연동이 가능합니다."));
                    }
                    else {
                        CheeseBridge.LOGGER.error("[SOOP] API 상세 에러: {} (코드: {})", errorMsg, resultCode);
                        Minecraft.getInstance().execute(() -> CheeseBridgeClient.sendMessage(Minecraft.getInstance().player, "숲(SOOP) API 오류: " + errorMsg));
                        scheduleReconnect("result " + resultCode);
                    }
                    return;
                }

                JsonObject data = json.getAsJsonArray("data").get(0).getAsJsonObject();
                String chatIp = data.get("chat_ip").getAsString();
                int chatPort = data.get("chat_port").getAsInt();
                this.chatNo = data.get("chat_no").getAsString();
                this.ticket = data.get("key").getAsString();

                JsonElement idElement = data.get("id");
                this.bjId = idElement.isJsonObject()
                    ? idElement.getAsJsonObject().get("userId").getAsString()
                    : idElement.getAsString();

                String wsUrl = buildWsUrl(chatIp, chatPort, bjId);
                CheeseBridge.LOGGER.info("[SOOP] WebSocket 연결 시도: {}", wsUrl);

                webSocket = new WebSocketClient(URI.create(wsUrl)) {
                    @Override
                    public void onOpen(ServerHandshake handshakedata) {
                        CheeseBridge.LOGGER.info("[SOOP] 소켓 연결됨. 로그인 시도.");
                        List<String> loginBody = new ArrayList<>();
                        loginBody.add("");
                        loginBody.add("");
                        loginBody.add("16");
                        send(SoopProtocol.makePacket(SoopProtocol.SVC_LOGIN, loginBody));
                        startKeepAlive();
                    }

                    @Override
                    public void onMessage(String message) {
                        CheeseBridge.LOGGER.debug("[SOOP] 텍스트 프레임 수신: {}", message);
                    }

                    @Override
                    public void onMessage(ByteBuffer bytes) {
                        byte[] data = new byte[bytes.remaining()];
                        bytes.get(data);
                        if (data.length > 14) {
                            String svcStr = new String(data, 2, 4, StandardCharsets.UTF_8);
                            int svc = 0;
                            try { svc = Integer.parseInt(svcStr); }
                            catch (NumberFormatException ignored) { }
                            String bodyOnly = new String(data, 14, data.length - 14, StandardCharsets.UTF_8);
                            handlePacket(svc, bodyOnly);
                        }
                    }

                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        CheeseBridge.LOGGER.warn("[SOOP] 소켓 닫힘 - 코드: {}, 사유: {}, 원격여부: {}", code, reason, remote);
                        stopKeepAlive();
                        scheduleReconnect("socket closed(" + code + ")");
                    }

                    @Override
                    public void onError(Exception ex) {
                        CheeseBridge.LOGGER.error("[SOOP] 에러", ex);
                    }
                };

                webSocket.connect();
            }
            catch (Exception e) {
                CheeseBridge.LOGGER.error("[SOOP] 연결 중 예외", e);
                scheduleReconnect("exception: " + e.getMessage());
            }
        }, "Soop-Connect-Thread").start();
    }

    private void handleTokenExpired(String reason) {
        if (manualDisconnect) { return; }

        if (refreshAttempts >= MAX_REFRESH_RETRY) {
            CheeseBridge.LOGGER.warn("[SOOP] 토큰 갱신 재시도 한도 초과({}). 재인증 필요.", reason);
            Minecraft.getInstance().execute(() -> CheeseBridgeClient.sendMessage(Minecraft.getInstance().player, "숲(SOOP) 인증이 만료되었습니다. /후원연동 숲 으로 다시 연결해주세요."));
            return;
        }

        refreshAttempts++;
        this.silentReconnect = true;  // 갱신 성공 후 따라오는 connect()는 재연결이므로 메시지 억제
        CheeseBridge.LOGGER.warn("[SOOP] 토큰 만료 감지({}) -> 갱신 요청", reason);
        // 서버에 갱신 요청. 서버는 갱신 후 S2C_FinalTokenPayload 로 새 토큰을 내려보내고,
        // 클라이언트의 S2C_FinalTokenPayload.handle 에서 SoopManager.connect(newToken) 이 다시 호출된다.
        ClientPlayNetworking.send(new C2S_RequestRefreshPayload(PLATFORM.SOOP.name()));
    }

    private synchronized void scheduleReconnect(String reason) {
        if (manualDisconnect) { return; }
        if (reconnectScheduler != null) { return; } // 이미 예약돼 있으면 중복 방지

        long delay = Math.min(RECONNECT_BASE_SEC * (1L << Math.min(reconnectAttempts, 5)), RECONNECT_MAX_SEC);
        reconnectAttempts++;
        CheeseBridge.LOGGER.info("[SOOP] {}초 후 재연결 예약 (사유: {})", delay, reason);

        reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Soop-Reconnect-Thread");
            t.setDaemon(true);
            return t;
        });
        reconnectScheduler.schedule(() -> {
            synchronized (this) {
                if (reconnectScheduler != null) {
                    reconnectScheduler.shutdown();
                    reconnectScheduler = null;
                }
            }
            doConnect();
        }, delay, TimeUnit.SECONDS);
    }

    private void handlePacket(int svc, String body) {
        try {
            List<String> parts = SoopProtocol.parseBody(body);
            if (parts.isEmpty()) { return; }

            if (svc == SoopProtocol.SVC_LOGIN && !isLoggedIn) {
                this.isLoggedIn = true;
                this.reconnectAttempts = 0;  // 연결 성공 -> 백오프 리셋
                this.refreshAttempts = 0;
                CheeseBridge.LOGGER.info("[SOOP] 로그인 승인됨. 채널 입장 시도.");
                List<String> joinBody = new ArrayList<>();
                joinBody.add(chatNo);
                joinBody.add(ticket);
                joinBody.add("5");
                joinBody.add("");
                joinBody.add("");
                webSocket.send(SoopProtocol.makePacket(SoopProtocol.SVC_JOINCH, joinBody));
                if (announceOnJoin) {
                    announceOnJoin = false;  // 최초 연동 시 1회만 노출, 이후 재연결에서는 표시 안 함
                    Minecraft.getInstance().execute(() -> CheeseBridgeClient.sendMessage(Minecraft.getInstance().player, "숲(SOOP) 연동 성공!"));
                }
                return;
            }

            // 수신 body 는 항상 구분자(\f)로 시작하므로 parts.get(0) 은 빈 문자열 -> base=1.
            int base = parts.getFirst().isEmpty() ? 1 : 0;
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
        ClientPlayNetworking.send(new C2S_DonationPayload(nickname, String.valueOf(count), donationType, "SOOP"));
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
        ClientPlayNetworking.send(new C2S_DonationPayload(label, String.valueOf(count), label, "SOOP"));
    }

    private void startKeepAlive() {
        stopKeepAlive();
        keepAliveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Soop-KeepAlive-Thread");
            t.setDaemon(true);
            return t;
        });
        keepAliveScheduler.scheduleAtFixedRate(() -> {
            if (webSocket != null && webSocket.isOpen()) {
                webSocket.send(SoopProtocol.makePacket(SoopProtocol.SVC_KEEPALIVE, new ArrayList<>()));
            }
        }, KEEPALIVE_SEC, KEEPALIVE_SEC, TimeUnit.SECONDS);
    }

    private void stopKeepAlive() {
        if (keepAliveScheduler != null) {
            keepAliveScheduler.shutdownNow();
            keepAliveScheduler = null;
        }
    }

    private synchronized void closeSocketOnly() {
        stopKeepAlive();
        if (webSocket != null) {
            try { webSocket.close(); } catch (Exception ignored) { }
            webSocket = null;
        }
    }

    public synchronized void disconnect() {
        this.manualDisconnect = true;
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
            reconnectScheduler = null;
        }
        closeSocketOnly();
        this.isLoggedIn = false;
        CheeseBridge.LOGGER.info("숲(SOOP) 연결 해제됨.");
    }
}