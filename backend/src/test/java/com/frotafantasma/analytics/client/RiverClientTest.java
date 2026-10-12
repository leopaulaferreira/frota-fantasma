package com.frotafantasma.analytics.client;

import static org.assertj.core.api.Assertions.*;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiverClientTest {
    HttpServer server;
    ClashRoyaleClient client;
    AtomicInteger requests = new AtomicInteger();
    boolean repeatCursor;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getRawQuery();
            boolean secondPage = query != null && query.contains("after=a%2Bb%2F%3D");
            String body;
            if (path.endsWith("currentriverrace")) {
                body = "{\"periodType\":\"warDay\",\"sectionIndex\":0,\"periodIndex\":6,\"clan\":{\"tag\":\"#RQJGC9GV\",\"participants\":[{\"tag\":\"#P0Y\",\"fame\":1600,\"decksUsed\":12}]}}";
            } else {
                String item = path.endsWith("members") ? "{\"tag\":\"" + (secondPage ? "#L0Y" : "#P0Y") + "\"}"
                        : "{\"seasonId\":136,\"sectionIndex\":" + (secondPage ? 2 : 3) + "}";
                body = "{\"items\":[" + item + "],\"paging\":{\"cursors\":{" +
                        (secondPage && !repeatCursor ? "" : "\"after\":\"a+b/=\"") + "}}}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        client = new ClashRoyaleClient(new ClashRoyaleProperties("http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "#RQJGC9GV", "test-token", Duration.ofSeconds(1), Duration.ofSeconds(1), 1,
                Duration.ofMillis(1), Duration.ofSeconds(30)));
    }

    @AfterEach
    void close() { server.stop(0); }

    @Test
    void coletaTodasAsPaginasECodificaCursor() {
        assertThat(client.getAllMembers(5)).extracting(m -> m.tag()).containsExactly("#P0Y", "#L0Y");
        assertThat(client.getRiverRaceLog(5)).extracting(w -> w.sectionIndex()).containsExactly(3, 2);
        assertThat(requests.get()).isEqualTo(4);
    }

    @Test
    void naoRetornaListaParcialQuandoAtingeLimite() {
        assertThatThrownBy(() -> client.getAllMembers(1)).isInstanceOfSatisfying(ClashRoyaleException.class,
                e -> assertThat(e.code()).isEqualTo("INCOMPLETE_UPSTREAM_RESPONSE"));
    }

    @Test
    void rejeitaCicloDePaginacao() {
        repeatCursor = true;
        assertThatThrownBy(() -> client.getRiverRaceLog(5)).isInstanceOf(ClashRoyaleException.class);
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void mantemCamposOficiaisDaCorridaAtual() {
        var race = client.getCurrentRiverRace();
        assertThat(race.periodType()).isEqualTo("warDay");
        assertThat(race.clan().participants().getFirst().decksUsed()).isEqualTo(12);
        assertThat(race.clan().participants().getFirst().fame()).isEqualTo(1600);
    }
}
