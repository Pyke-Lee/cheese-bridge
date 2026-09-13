package kr.pyke.client.chzzk;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.socket.client.IO;
import io.socket.client.Socket;
import kr.pyke.CheeseBridge;
import kr.pyke.client.CheeseBridgeClient;
import kr.pyke.network.payload.c2s.C2S_DonationPayload;
import kr.pyke.network.payload.c2s.C2S_RequestRefreshPayload;
import kr.pyke.type.PLATFORM;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ChzzkManager {
    private static final ChzzkManager INSTANCE = new ChzzkManager();
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private Socket socket;

    public enum LinkState { CONNECTED, DISCONNECTED, TOKEN_EXPIRED, NO_TOKEN, ERROR }
    private volatile String accessToken;
    private volatile LinkState lastKnownStatus = LinkState.DISCONNECTED;
    private volatile String lastCloseReason = "";

    private ChzzkManager() { }
    public static ChzzkManager getInstance() { return INSTANCE; }

    public void connect(String accessToken) {
        disconnect();
        this.accessToken = accessToken;
        CheeseBridge.LOGGER.info("[CHZZK] 연결 시작");

        new Thread(() -> {
            try {
                HttpRequest authRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://openapi.chzzk.naver.com/open/v1/sessions/auth"))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET().build();

                HttpResponse<String> authResponse = httpClient.send(authRequest, HttpResponse.BodyHandlers.ofString());

                if (authResponse.statusCode() == 401) {
                    lastKnownStatus = LinkState.TOKEN_EXPIRED;
                    lastCloseReason = "토큰 재발급";
                    C2S_RequestRefreshPayload.send(new C2S_RequestRefreshPayload(PLATFORM.CHZZK.name()));
                    return;
                }
                if (authResponse.statusCode() != 200) {
                    lastKnownStatus = LinkState.ERROR;
                    lastCloseReason = "인증 오류(" + authResponse.statusCode() + ")";
                    return;
                }

                JsonObject responseJson = gson.fromJson(authResponse.body(), JsonObject.class);
                String socketUrl = responseJson.getAsJsonObject("content").get("url").getAsString();

                IO.Options options = new IO.Options();
                options.transports = new String[]{"websocket"};
                options.reconnection = true;

                socket = IO.socket(socketUrl, options);

                socket.on(Socket.EVENT_CONNECT, args -> {
                    lastKnownStatus = LinkState.CONNECTED;
                    Minecraft.getInstance().execute(() -> CheeseBridgeClient.sendMessage(Minecraft.getInstance().player, "치지직 연결 성공!"));
                });

                socket.on(Socket.EVENT_DISCONNECT, args -> {
                    lastKnownStatus = LinkState.DISCONNECTED;    // 소켓 끊김 기록
                    if (lastCloseReason == null || lastCloseReason.isEmpty()) { lastCloseReason = "끊김 복구"; }
                });

                socket.on("SYSTEM", args -> {
                    try {
                        JsonObject payload = gson.fromJson(args[0].toString(), JsonObject.class);
                        String type = payload.get("type").getAsString();
                        if ("connected".equals(type)) {
                            String sessionKey = payload.getAsJsonObject("data").get("sessionKey").getAsString();
                            requestSubscription(accessToken, sessionKey);
                        }
                    }
                    catch (Exception e) { CheeseBridge.LOGGER.error("시스템 메시지 오류", e); }
                });

                socket.on("DONATION", args -> {
                    try {
                        JsonObject data = gson.fromJson(args[0].toString(), JsonObject.class);
                        String amount = data.get("payAmount").getAsString();
                        String text = data.has("donationText") ? data.get("donationText").getAsString() : "";
                        String nickname = data.has("donatorNickname") ? data.get("donatorNickname").getAsString() : "익명";
                        C2S_DonationPayload.send(new C2S_DonationPayload(nickname, amount, text, "CHZZK"));
                    }
                    catch (Exception e) { CheeseBridge.LOGGER.error("후원 처리 오류", e); }
                });

                socket.connect();
            }
            catch (Throwable e) {
                lastKnownStatus = LinkState.ERROR;
                lastCloseReason = "연결 예외";
                CheeseBridge.LOGGER.error("치지직 연결 예외", e);
            }
        }).start();
    }

    private void requestSubscription(String accessToken, String sessionKey) {
        try {
            String url = "https://openapi.chzzk.naver.com/open/v1/sessions/events/subscribe/donation?sessionKey=" + sessionKey;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        catch (Exception e) { CheeseBridge.LOGGER.error("구독 요청 예외", e); }
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket = null;
            lastKnownStatus = LinkState.DISCONNECTED;   // 수동 해제 기록
            CheeseBridge.LOGGER.info("치지직 연결 해제");
        }
    }

    public boolean hasToken() { return accessToken != null; }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public LinkState getLastKnownStatus() {
        if (accessToken == null) { return LinkState.NO_TOKEN; }
        return lastKnownStatus;
    }

    public String getLastCloseReason() {
        return (lastCloseReason == null || lastCloseReason.isEmpty()) ? "끊김 복구" : lastCloseReason;
    }

    public void forceReconnect() {
        if (accessToken == null) { return; }
        connect(accessToken);
    }
}