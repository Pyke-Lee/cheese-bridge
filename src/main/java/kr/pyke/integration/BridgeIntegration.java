package kr.pyke.integration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import kr.pyke.CheeseBridge;
import kr.pyke.config.CheeseBridgeConfig;
import kr.pyke.integration.event.DonationReceivedCallback;
import kr.pyke.util.PLATFORM;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class BridgeIntegration {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    public static void triggerDonation(ServerPlayer player, DonationEvent event) {
        DonationReceivedCallback.DONATION_RECEIVED.invoker().onDonationReceived(player, event);
    }

    public static BridgeDataState.TokenInfo parseTokenResponse(String jsonResponse) {
        if (jsonResponse == null) { return null; }

        try {
            JsonObject json = GSON.fromJson(jsonResponse, JsonObject.class);
            if (json.has("content")) {
                json = json.getAsJsonObject("content");
            }

            String accessToken = null;
            String refreshToken = null;

            if (json.has("accessToken")) { accessToken = json.get("accessToken").getAsString(); }
            else if (json.has("access_token")) { accessToken = json.get("access_token").getAsString(); }

            if (json.has("refreshToken")) { refreshToken = json.get("refreshToken").getAsString(); }
            else if (json.has("refresh_token")) { refreshToken = json.get("refresh_token").getAsString(); }

            if (accessToken != null) {
                return new BridgeDataState.TokenInfo(accessToken, refreshToken);
            }
        }
        catch (Exception e) { CheeseBridge.LOGGER.error("토큰 파싱 중 오류: ", e); }
        return null;
    }

    public static String exchangeCodeForToken(PLATFORM platform, String authCode, String authState) {
        try {
            String url = "";
            String body = "";
            String contentType = "application/json";

            if (platform == PLATFORM.CHZZK) {
                url = "https://chzzk.naver.com/auth/v1/token";
                JsonObject json = new JsonObject();
                json.addProperty("grantType", "authorization_code");
                json.addProperty("clientId", CheeseBridgeConfig.DATA.chzzk.clientID);
                json.addProperty("clientSecret", CheeseBridgeConfig.DATA.chzzk.clientSecret);
                json.addProperty("code", authCode);
                json.addProperty("state", authState);
                body = GSON.toJson(json);
            }
            else if (platform == PLATFORM.SOOP) {
                url = "https://openapi.sooplive.com/auth/token";
                contentType = "application/x-www-form-urlencoded";
                body = String.format("grant_type=authorization_code&client_id=%s&client_secret=%s&redirect_uri=%s&code=%s",
                    CheeseBridgeConfig.DATA.soop.clientID,
                    CheeseBridgeConfig.DATA.soop.clientSecret,
                    "http://localhost:8080/callback",
                    authCode);
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            CheeseBridge.LOGGER.info("[{}] 토큰 응답: {}", platform, response.body());
            return response.body();
        }
        catch (Exception e) {
            CheeseBridge.LOGGER.error("토큰 교환 중 오류 발생: ", e);
            return null;
        }
    }

    public static String refreshAccessToken(PLATFORM platform, String refreshToken) {
        try {
            String url = "";
            String body = "";
            String contentType = "application/json";

            if (platform == PLATFORM.CHZZK) {
                url = "https://chzzk.naver.com/auth/v1/token";
                JsonObject json = new JsonObject();
                json.addProperty("grantType", "refresh_token");
                json.addProperty("refreshToken", refreshToken);
                json.addProperty("clientId", CheeseBridgeConfig.DATA.chzzk.clientID);
                json.addProperty("clientSecret", CheeseBridgeConfig.DATA.chzzk.clientSecret);
                body = GSON.toJson(json);
            }
            else if (platform == PLATFORM.SOOP) {
                url = "https://openapi.sooplive.com/auth/token";
                contentType = "application/x-www-form-urlencoded";
                body = String.format("grant_type=refresh_token&client_id=%s&client_secret=%s&refresh_token=%s",
                    CheeseBridgeConfig.DATA.soop.clientID,
                    CheeseBridgeConfig.DATA.soop.clientSecret,
                    refreshToken);
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
            return null;
        }
        catch (Exception e) {
            CheeseBridge.LOGGER.error("토큰 갱신 중 예외 발생: ", e);
            return null;
        }
    }
}