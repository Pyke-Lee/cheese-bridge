package kr.pyke.client.manager.chzzk;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.socket.client.IO;
import io.socket.client.Socket;
import kr.pyke.CheeseBridge;
import kr.pyke.client.CheeseBridgeClient;
import kr.pyke.client.state.ConnectionStatus;
import kr.pyke.network.payload.c2s.C2S_DonationPayload;
import kr.pyke.network.payload.c2s.C2S_RequestRefreshPayload;
import kr.pyke.type.PLATFORM;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

public class ChzzkManager {
    private static final ChzzkManager INSTANCE = new ChzzkManager();
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private Socket socket;
    private volatile String accessToken;

    private ChzzkManager() { }
    public static ChzzkManager getInstance() { return INSTANCE; }

    public boolean hasToken() {
        return accessToken != null;
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public ConnectionStatus getLocalStatus() {
        boolean hasToken = accessToken != null;
        boolean connected = socket != null && socket.connected();
        if (!hasToken) { return new ConnectionStatus(PLATFORM.CHZZK, false, false, false, null); }
        return new ConnectionStatus(PLATFORM.CHZZK, true, connected, connected, connected ? "연동 중" : "연결 대기");
    }

    public void checkStatus(Consumer<ConnectionStatus> callback) {
        if (accessToken == null) {
            callback.accept(new ConnectionStatus(PLATFORM.CHZZK, false, false, false, null));
            return;
        }

        boolean socketOk = isConnected();

        new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://openapi.chzzk.naver.com/open/v1/sessions?size=10&page=0"))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .GET().build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 401) {
                    callback.accept(new ConnectionStatus(PLATFORM.CHZZK, true, socketOk, false, "토큰 만료"));
                    return;
                }

                if (response.statusCode() != 200) {
                    callback.accept(new ConnectionStatus(PLATFORM.CHZZK, true, socketOk, false, "HTTP " + response.statusCode()));
                    return;
                }

                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                JsonObject content = json.getAsJsonObject("content");
                if (content == null) {
                    callback.accept(new ConnectionStatus(PLATFORM.CHZZK, true, socketOk, false, "응답 파싱 실패"));
                    return;
                }

                JsonArray sessions = content.getAsJsonArray("data");
                boolean donationSubscribed = false;
                String detail = "활성 세션 없음";

                if (sessions != null) {
                    for (var sessionEl : sessions) {
                        JsonObject session = sessionEl.getAsJsonObject();
                        boolean active = !session.has("disconnectedDate") || session.get("disconnectedDate").isJsonNull();
                        if (!active) { continue; }

                        JsonArray events = session.getAsJsonArray("subscribedEvents");
                        if (events == null) { continue; }

                        for (var eventEl : events) {
                            String eventType = eventEl.getAsJsonObject().get("eventType").getAsString();
                            if ("DONATION".equals(eventType)) {
                                donationSubscribed = true;
                                detail = "DONATION 구독 중";
                                break;
                            }
                        }
                        if (donationSubscribed) { break; }
                    }
                }

                callback.accept(new ConnectionStatus(PLATFORM.CHZZK, true, socketOk, donationSubscribed, detail));
            }
            catch (Exception e) {
                CheeseBridge.LOGGER.error("[CHZZK] 상태 확인 중 오류", e);
                callback.accept(new ConnectionStatus(PLATFORM.CHZZK, true, socketOk, false, e.getMessage()));
            }
        }, "Chzzk-Status-Thread").start();
    }

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
                    ClientPlayNetworking.send(new C2S_RequestRefreshPayload(PLATFORM.CHZZK.name()));
                    return;
                }
                if (authResponse.statusCode() != 200) return;

                JsonObject responseJson = gson.fromJson(authResponse.body(), JsonObject.class);
                String socketUrl = responseJson.getAsJsonObject("content").get("url").getAsString();

                IO.Options options = new IO.Options();
                options.transports = new String[]{"websocket"};
                options.reconnection = true;

                socket = IO.socket(socketUrl, options);

                socket.on(Socket.EVENT_CONNECT, args ->
                        Minecraft.getInstance().execute(() ->
                                CheeseBridgeClient.sendMessage(Minecraft.getInstance().player, "치지직 연결 성공")));

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
                        ClientPlayNetworking.send(new C2S_DonationPayload(nickname, amount, text, "CHZZK"));
                    }
                    catch (Exception e) { CheeseBridge.LOGGER.error("후원 처리 오류", e); }
                });

                socket.connect();
            }
            catch (Throwable e) { CheeseBridge.LOGGER.error("치지직 연결 예외", e); }
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
        this.accessToken = null;
        if (socket != null) {
            socket.disconnect();
            socket = null;
            CheeseBridge.LOGGER.info("치지직 연결 해제");
        }
    }
}