package com.frotafantasma.analytics.clash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClashRoyaleClientTest {
    private HttpServer server;
    private final List<String> paths = new ArrayList<>();
    private final List<String> authorizationHeaders = new ArrayList<>();
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
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
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

    private ClashRoyaleClient client() {
        return new ClashRoyaleClient(new ClashRoyaleProperties(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "#RQJGC9GV", "test-token"));
    }
}
