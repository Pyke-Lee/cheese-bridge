package kr.pyke.client.chzzk;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.socket.client.IO;
import io.socket.client.Socket;
import kr.pyke.CheeseBridge;
import kr.pyke.client.PykeLibClient;
import kr.pyke.network.payload.c2s.C2S_DonationPayload;
import kr.pyke.network.payload.c2s.C2S_RequestRefreshPayload;
import kr.pyke.util.PLATFORM;
import kr.pyke.util.constants.COLOR;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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

    private ChzzkManager() { }
    public static ChzzkManager getInstance() { return INSTANCE; }

    public void connect(String accessToken) {
        disconnect();
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

                socket.on(Socket.EVENT_CONNECT, args -> {
                    Minecraft.getInstance().execute(() -> PykeLibClient.sendSystemMessage(COLOR.LIME.getColor(), "치지직 연결 성공"));
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
        if (socket != null) {
            socket.disconnect();
            socket = null;
            CheeseBridge.LOGGER.info("치지직 연결 해제");
        }
    }
}