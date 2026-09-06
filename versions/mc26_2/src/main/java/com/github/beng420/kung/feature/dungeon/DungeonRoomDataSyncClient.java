package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.KungMod;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class DungeonRoomDataSyncClient {
    public static final DungeonRoomDataSyncClient INSTANCE = new DungeonRoomDataSyncClient();

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private volatile String statusMessage = "Idle";

    private DungeonRoomDataSyncClient() {
    }

    public String statusMessage() {
        return statusMessage;
    }

    public boolean configured() {
        return !DungeonMapOverlayConfig.INSTANCE.roomSyncServerUrl().isBlank();
    }

    public boolean active() {
        return DungeonMapOverlayConfig.INSTANCE.roomSyncEnabled() && configured();
    }

    public void pullAsync(Minecraft client) {
        if (!active()) {
            send(client, "Room Sync ist nicht aktiv. Setze Server + enable.");
            return;
        }

        statusMessage = "Pulling...";
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = requestBuilder(endpoint("rooms"))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                ensureSuccess(response, "Pull");
                DungeonKnownRoomCatalog.RemoteCacheResult result =
                    DungeonKnownRoomCatalog.updateRemoteCache(response.body());
                statusMessage = "Pulled " + result.roomCount() + " rooms";
                send(client, "Room Sync Pull ok: rooms=" + result.roomCount()
                    + " variants=" + result.variantCount()
                    + " cells=" + result.componentCount());
            } catch (IOException | InterruptedException | RuntimeException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                statusMessage = "Pull failed";
                KungMod.LOGGER.warn("Failed to pull dungeon room sync data.", exception);
                send(client, "Room Sync Pull fehlgeschlagen. Siehe latest.log.");
            }
        });
    }

    public void pushRoomReportAsync(String roomReportJson) {
        if (!active() || !DungeonMapOverlayConfig.INSTANCE.roomSyncUploadEnabled()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                pushRoomReport(roomReportJson);
            } catch (IOException | InterruptedException | RuntimeException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                statusMessage = "Push failed";
                KungMod.LOGGER.warn("Failed to push dungeon room report.", exception);
            }
        });
    }

    public void pushRoomReportAsync(Minecraft client, String roomReportJson) {
        if (!active()) {
            send(client, "Room Sync ist nicht aktiv. Setze Server + enable.");
            return;
        }

        statusMessage = "Pushing...";
        CompletableFuture.runAsync(() -> {
            try {
                PushResult result = pushRoomReport(roomReportJson);
                statusMessage = "Pushed";
                send(client, "Room Sync Push ok: " + result.message());
            } catch (IOException | InterruptedException | RuntimeException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                statusMessage = "Push failed";
                KungMod.LOGGER.warn("Failed to push dungeon room report.", exception);
                send(client, "Room Sync Push fehlgeschlagen. Siehe latest.log.");
            }
        });
    }

    private PushResult pushRoomReport(String roomReportJson) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(endpoint("rooms/report"))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(roomReportJson))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response, "Push");
        return new PushResult(response.body().isBlank() ? "gesendet" : response.body());
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Kung-RoomSync");
        String token = DungeonMapOverlayConfig.INSTANCE.roomSyncToken();
        if (!token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private static void ensureSuccess(HttpResponse<?> response, String operation) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(operation + " returned HTTP " + response.statusCode());
        }
    }

    private static URI endpoint(String path) {
        String base = DungeonMapOverlayConfig.INSTANCE.roomSyncServerUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        try {
            return new URI(base + "/" + path);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid room sync URL.", exception);
        }
    }

    private static void send(Minecraft client, String message) {
        if (client == null || client.player == null) {
            return;
        }
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal(message));
            }
        });
    }

    private record PushResult(String message) {
    }
}
