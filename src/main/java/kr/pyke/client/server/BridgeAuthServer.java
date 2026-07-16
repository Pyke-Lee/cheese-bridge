package kr.pyke.client.server;

import com.sun.net.httpserver.HttpServer;
import kr.pyke.CheeseBridge;
import kr.pyke.network.payload.c2s.C2S_AuthCodePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class BridgeAuthServer {
    private HttpServer server;
    private final int PORT = 8080;

    public void start(String expectedPlatform) {
        try {
            if (server != null) {
                server.stop(0);
            }

            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/callback", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                String code = null;
                String state = null;

                if (query != null) {
                    for (String pair : query.split("&")) {
                        int idx = pair.indexOf("=");
                        if (idx <= 0) { continue; }
                        String key = pair.substring(0, idx);
                        String value = pair.substring(idx + 1);
                        if (key.equals("code")) { code = value; }
                        else if (key.equals("state")) { state = value; }
                    }
                }

                String response;
                if (code != null) {
                    String finalState = (state != null) ? state : "state_ok";
                    ClientPlayNetworking.send(new C2S_AuthCodePayload(code, finalState, expectedPlatform));
                    response = "<html><head><meta charset='utf-8'></head><body style='text-align:center;'><h1>" + expectedPlatform + " 인증 완료!</h1></body></html>";
                }
                else {
                    response = "<html><body><h1>실패</h1></body></html>";
                }

                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }

                new Thread(() -> {
                    try { Thread.sleep(1000); }
                    catch (Exception ignored) {}
                    stop();
                }).start();
            });
            server.start();
        }
        catch (Exception e) {
            CheeseBridge.LOGGER.error("인증 서버 에러: ", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}