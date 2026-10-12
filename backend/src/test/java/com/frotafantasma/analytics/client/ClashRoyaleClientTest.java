package com.frotafantasma.analytics.client;

import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClashRoyaleClientTest {
    private HttpServer server;
    private ExecutorService executor;
    private String retryAfter;
    private int failFirstRequests = Integer.MAX_VALUE;
    private long responseDelayMillis;
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
    private int responseStatus = 200;
    private String responseBodyOverride;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getRawPath());
            authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            String body = responseBodyOverride != null ? responseBodyOverride
                    : exchange.getRequestURI().getPath().endsWith("/members")
                    ? "{\"items\":[{\"tag\":\"#PLAYER\",\"name\":\"Player\",\"trophies\":9000}]}"
                    : "{\"tag\":\"#RQJGC9GV\",\"name\":\"Frota Fantasma\",\"members\":1}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (retryAfter != null) {
                exchange.getResponseHeaders().set("Retry-After", retryAfter);
            }
            try {
                Thread.sleep(responseDelayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(paths.size() <= failFirstRequests ? responseStatus : 200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void enviaTagCodificadaETokenNosDoisEndpoints() {
        ClashRoyaleClient client = client();

        assertEquals("Frota Fantasma", client.getClan().name());
        assertEquals("#PLAYER", client.getMembers().items().getFirst().tag());
        assertEquals(List.of("/v1/clans/%23RQJGC9GV", "/v1/clans/%23RQJGC9GV/members"), paths);
        assertEquals(List.of("Bearer test-token", "Bearer test-token"), authorizationHeaders);
    }

    @Test
    void traduzLimiteDeRequisicoesSemExporRespostaExterna() {
        responseStatus = 429;

        ClashRoyaleException exception = assertThrows(ClashRoyaleException.class, () -> client().getClan());

        assertEquals(503, exception.status().value());
        assertEquals("UPSTREAM_RATE_LIMITED", exception.code());
    }

    @Test
    void traduzClaNaoEncontrado() {
        responseStatus = 404;

        ClashRoyaleException exception = assertThrows(ClashRoyaleException.class, () -> client().getClan());

        assertEquals(404, exception.status().value());
        assertEquals("CLAN_NOT_FOUND", exception.code());
    }

    @Test
    void traduzJsonInvalido() {
        responseBodyOverride = "{invalid";

        ClashRoyaleException exception = assertThrows(ClashRoyaleException.class, () -> client().getClan());

        assertEquals(502, exception.status().value());
        assertEquals("INVALID_UPSTREAM_RESPONSE", exception.code());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {400, 401, 403, 404})
    void naoRepeteErrosPermanentes(int status) {
        responseStatus = status;
        assertThrows(ClashRoyaleException.class, () -> client().getClan());
        assertEquals(1, paths.size());
    }

    @Test
    void repeteFalhaTemporariaERecupera() {
        responseStatus = 503;
        failFirstRequests = 1;
        assertEquals("Frota Fantasma", client().getClan().name());
        assertEquals(2, paths.size());
    }

    @Test
    void limitaNumeroDeTentativas() {
        responseStatus = 503;
        var error = assertThrows(ClashRoyaleException.class, () -> client().getClan());
        assertEquals(503, error.status().value());
        assertEquals(2, paths.size());
    }

    @Test
    void aplicaTimeoutConfigurado() {
        responseDelayMillis = 500;
        var error = assertThrows(ClashRoyaleException.class, () -> client().getClan());
        assertEquals(504, error.status().value());
        assertEquals("UPSTREAM_TIMEOUT", error.code());
        assertEquals(2, paths.size());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"30", "Thu, 1 Jan 2026 00:00:30 GMT", "invalid"})
    void respeitaCooldownEntreEndpointsERetomaDepois(String header) {
        Clock clock = org.mockito.Mockito.mock(Clock.class);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        org.mockito.Mockito.when(clock.instant()).thenReturn(now);
        var client = new ClashRoyaleClient(properties(), clock, duration -> {});
        responseStatus = 429;
        retryAfter = header;

        var error = assertThrows(ClashRoyaleException.class, client::getClan);
        assertEquals(30L, error.retryAfterSeconds());
        responseStatus = 200;
        assertThrows(ClashRoyaleException.class, client::getMembers);
        assertEquals(1, paths.size());

        org.mockito.Mockito.when(clock.instant()).thenReturn(now.plusSeconds(31));
        assertEquals(1, client.getMembers().items().size());
        assertEquals(2, paths.size());
    }

    @Test
    void preservaInterrupcaoDuranteRetry() {
        responseStatus = 503;
        var client = new ClashRoyaleClient(properties(), Clock.systemUTC(), duration -> {
            throw new InterruptedException();
        });
        try {
            var error = assertThrows(ClashRoyaleException.class, client::getClan);
            assertEquals("UPSTREAM_INTERRUPTED", error.code());
            org.junit.jupiter.api.Assertions.assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, paths.size());
        } finally {
            Thread.interrupted();
        }
    }

    private ClashRoyaleClient client() {
        return new ClashRoyaleClient(properties());
    }

    private ClashRoyaleProperties properties() {
        return new ClashRoyaleProperties(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "#RQJGC9GV", "test-token", Duration.ofSeconds(1), Duration.ofMillis(100),
                2, Duration.ofMillis(1), Duration.ofSeconds(30));
    }
}
