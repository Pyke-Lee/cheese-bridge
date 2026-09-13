package kr.pyke.integration;

import kr.pyke.network.payload.s2c.S2C_CheckLinkPayload;
import kr.pyke.type.MESSAGE_TYPE;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class LinkCheckCollector {
    private static final long COLLECT_TIMEOUT_SEC = 12;

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "CheeseBridge-LinkCheck-Collector");
                t.setDaemon(true);
                return t;
            });

    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();

    private LinkCheckCollector() { }

    private static final class Pending {
        final MinecraftServer server;
        final UUID adminUuid;
        final java.util.Set<UUID> targets;
        final Map<String, String> results = new LinkedHashMap<>();
        volatile boolean finished = false;

        Pending(MinecraftServer server, UUID adminUuid, java.util.Set<UUID> targets) {
            this.server = server;
            this.adminUuid = adminUuid;
            this.targets = java.util.concurrent.ConcurrentHashMap.newKeySet();
            this.targets.addAll(targets);
        }
    }

    public static void start(MinecraftServer server, ServerPlayer admin) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        String requestId = UUID.randomUUID().toString();

        java.util.Set<UUID> targets = java.util.concurrent.ConcurrentHashMap.newKeySet();
        for (ServerPlayer p : players) { targets.add(p.getUUID()); }

        Pending pending = new Pending(server, admin.getUUID(), targets);
        PENDING.put(requestId, pending);

        for (ServerPlayer p : players) {
            S2C_CheckLinkPayload.send(p, new S2C_CheckLinkPayload(requestId));
        }

        int total = targets.size();
        MESSAGE_TYPE.PERSONAL.send(admin, "§e연동 상태 점검 시작... (§f" + total + "§e명 응답 대기, 최대 " + COLLECT_TIMEOUT_SEC + "초)");

        SCHEDULER.schedule(() -> finish(requestId, true), COLLECT_TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    public static void onResult(String requestId, String playerName, String result) {
        Pending pending = PENDING.get(requestId);
        if (pending == null || pending.finished) { return; }

        synchronized (pending) {
            pending.results.put(playerName, result);
            if (pending.results.size() >= pending.targets.size()) {
                finish(requestId, false);
            }
        }
    }

    private static void finish(String requestId, boolean byTimeout) {
        Pending pending = PENDING.remove(requestId);
        if (pending == null || pending.finished) { return; }
        pending.finished = true;

        pending.server.execute(() -> {
            ServerPlayer admin = pending.server.getPlayerList().getPlayer(pending.adminUuid);
            if (admin == null) { return; }

            int total = pending.targets.size();
            int answered = pending.results.size();

            MESSAGE_TYPE.PERSONAL.send(admin,
                    "§6연동 상태 점검 §7(§f" + total + "§7명 중 §f" + answered + "§7명 응답)");

            for (Map.Entry<String, String> e : pending.results.entrySet()) {
                MESSAGE_TYPE.PERSONAL.send(admin, "§7- §f" + e.getKey() + "  §r" + e.getValue());
            }

            int noResponse = total - answered;
            if (noResponse > 0) {
                MESSAGE_TYPE.PERSONAL.send(admin, "§7- (응답 없음 " + noResponse + "명)");
            }
        });
    }
}