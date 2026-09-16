package com.github.beng420.kung.util;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public final class HypixelSkyBlockProfileClientTest {
    @Test public void providersKeepTheirHeadersAndRefreshTimestampsAcrossRateLimitAndServerRetries() throws Exception {
        for (String provider : List.of("Hypixel", "Adjectils")) {
            try (var server = new Server(new int[] {429, 503, 200}, "{\"success\":true}")) {
                assertTrue(load(provider, server.uri()).get(10, TimeUnit.SECONDS).get("success").getAsBoolean());
                assertEquals(3, server.requests.size());
                long previousTimestamp = 0;
                for (var request : server.requests) {
                    var headers = request.getRequestHeaders();
                    String query = request.getRequestURI().getRawQuery();
                    assertEquals("GET", request.getRequestMethod());
                    assertEquals("application/json", headers.getFirst("Accept"));
                    assertTrue(query.startsWith("uuid=sample+id%2B"));
                    long timestamp;
                    if (provider.equals("Hypixel")) {
                        assertEquals("test-key", headers.getFirst("API-Key"));
                        assertEquals("no-cache, no-store, max-age=0", headers.getFirst("Cache-Control"));
                        assertEquals("no-cache", headers.getFirst("Pragma"));
                        assertEquals("Kung-HypixelProfile", headers.getFirst("User-Agent"));
                        assertNull(headers.getFirst("X-Timestamp"));
                        timestamp = Long.parseLong(query.substring(query.indexOf("&_kungFresh=") + 12));
                    } else {
                        assertEquals("uuid=sample+id%2B", query);
                        assertNull(headers.getFirst("API-Key"));
                        assertNull(headers.getFirst("Cache-Control"));
                        assertNull(headers.getFirst("Pragma"));
                        assertEquals("Kung-CA50-AdjectilsFallback", headers.getFirst("User-Agent"));
                        timestamp = Long.parseLong(headers.getFirst("X-Timestamp"));
                    }
                    assertTrue("Retries need a fresh provider timestamp", timestamp > previousTimestamp);
                    previousTimestamp = timestamp;
                }
            }
        }
    }

    @Test public void retryableFailuresStopAtThreeWhileClientErrorsAndInvalidBodiesFailImmediately() throws Exception {
        for (String provider : List.of("Hypixel", "Adjectils")) {
            for (int status : new int[] {500, 400}) {
                try (var server = new Server(new int[] {status}, "{}")) {
                    var failure = assertThrows(ExecutionException.class, () -> load(provider, server.uri()).get(10, TimeUnit.SECONDS));
                    assertEquals(provider + " returned HTTP " + status, failure.getCause().getMessage());
                    assertEquals(status == 500 ? 3 : 1, server.requests.size());
                }
            }
            for (String body : List.of("{\"success\":false}", "not json")) {
                try (var server = new Server(new int[] {200}, body)) {
                    var failure = assertThrows(ExecutionException.class, () -> load(provider, server.uri()).get(10, TimeUnit.SECONDS));
                    if (body.startsWith("{")) assertEquals(provider + " returned success=false", failure.getCause().getMessage());
                    assertEquals(1, server.requests.size());
                }
            }
        }
    }

    @Test public void truncatedResponsesRetryAndKeepTheirTransportFailureAfterThreeAttempts() throws Exception {
        for (String provider : List.of("Hypixel", "Adjectils")) {
            try (var server = new ServerSocket()) {
                server.bind(new InetSocketAddress("127.0.0.1", 0));
                server.setSoTimeout(5_000);
                var responses = CompletableFuture.runAsync(() -> {
                    for (int attempt = 0; attempt < 3; attempt++) {
                        try (var socket = server.accept()) {
                            socket.setSoTimeout(2_000);
                            var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                            while (true) {
                                String line = input.readLine();
                                if (line == null || line.isEmpty()) break;
                            }
                            // Closing the raw socket guarantees EOF after the incomplete body.
                            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n"
                                + "Connection: close\r\n\r\n{").getBytes(StandardCharsets.US_ASCII));
                        } catch (IOException exception) {
                            throw new CompletionException(exception);
                        }
                    }
                });
                var endpoint = URI.create("http://127.0.0.1:" + server.getLocalPort() + "/profiles");
                var failure = assertThrows(ExecutionException.class, () -> load(provider, endpoint).get(10, TimeUnit.SECONDS));
                responses.get(10, TimeUnit.SECONDS);
                assertTrue(failure.getCause() instanceof IOException);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<JsonObject> load(String provider, URI endpoint) throws Exception {
        boolean hypixel = provider.equals("Hypixel");
        var method = HypixelSkyBlockProfileClient.class.getDeclaredMethod("load" + provider + "Object",
            hypixel ? new Class<?>[] {URI.class, String.class, String.class, String.class}
                : new Class<?>[] {URI.class, String.class, String.class});
        method.setAccessible(true);
        return (CompletableFuture<JsonObject>) method.invoke(HypixelSkyBlockProfileClient.INSTANCE,
            hypixel ? new Object[] {endpoint, "uuid", "sample id+", "test-key"}
                : new Object[] {endpoint, "uuid", "sample id+"});
    }

    private static final class Server implements AutoCloseable {
        private final HttpServer server;
        private final List<com.sun.net.httpserver.HttpExchange> requests = new ArrayList<>();

        Server(int[] statuses, String response) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/profiles", exchange -> {
                int status = statuses[Math.min(requests.size(), statuses.length - 1)];
                requests.add(exchange);
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
                finally { exchange.close(); }
            });
            server.start();
        }

        URI uri() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/profiles"); }
        @Override public void close() { server.stop(0); }
    }
}
