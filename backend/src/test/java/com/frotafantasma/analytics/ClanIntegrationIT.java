package com.frotafantasma.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ClanIntegrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static final AtomicReference<String> MEMBER_BODY = new AtomicReference<>();
    static final AtomicInteger UPSTREAM_STATUS = new AtomicInteger(200);
    static final HttpServer UPSTREAM = startUpstream();

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate database;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("clash-royale.base-url", () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort() + "/v1");
        properties.add("clash-royale.api-token", () -> "test-only-secret");
        properties.add("clash-royale.clan-tag", () -> "#RQJGC9GV");
    }

    @BeforeEach
    void reset() {
        database.update("delete from clan_member_snapshot");
        database.update("delete from clan_snapshot");
        UPSTREAM_STATUS.set(200);
        MEMBER_BODY.set("""
                {"items":[{"tag":"#P0Y","name":"Fenix","role":"member","trophies":9000,"donations":198}],
                 "paging":{"cursors":{}}}
                """);
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop(0);
    }

    @Test
    void migraBancoEGravaDadosDosDoisEndpoints() throws Exception {
        mvc.perform(get("/api/clan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tag").value("#RQJGC9GV"))
                .andExpect(jsonPath("$.members").value(1));
        mvc.perform(get("/api/clan/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].donations").value(198));

        assertThat(database.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isEqualTo(2);
        assertThat(database.queryForObject("select name from clan_snapshot", String.class)).isEqualTo("Frota Fantasma");
        assertThat(database.queryForObject("select donations from clan_member_snapshot", Integer.class)).isEqualTo(198);
        assertThat(database.queryForObject("select count(*) from clan_member_snapshot where observed_at is not null", Integer.class)).isEqualTo(1);
    }

    @Test
    void atualizaRetratoSemDuplicarEMantemApenasMembrosAtuais() throws Exception {
        mvc.perform(get("/api/clan/members")).andExpect(status().isOk());
        MEMBER_BODY.set("""
                {"items":[{"tag":"#L0Y","name":"Capitao","role":"elder","donations":250}]}
                """);
        mvc.perform(get("/api/clan/members")).andExpect(status().isOk());
        mvc.perform(get("/api/clan/members")).andExpect(status().isOk());

        assertThat(database.queryForObject("select count(*) from clan_member_snapshot", Integer.class)).isEqualTo(1);
        assertThat(database.queryForObject("select tag from clan_member_snapshot", String.class)).isEqualTo("#L0Y");
        assertThat(database.queryForObject("select role from clan_member_snapshot", String.class)).isEqualTo("elder");
    }

    @Test
    void preservaDadosQuandoApiExternaFalhaESanitizaErro() throws Exception {
        mvc.perform(get("/api/clan/members")).andExpect(status().isOk());
        UPSTREAM_STATUS.set(403);
        MEMBER_BODY.set("{\"message\":\"test-only-secret\"}");
        var result = mvc.perform(get("/api/clan/members"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("test-only-secret");
        assertThat(database.queryForObject("select tag from clan_member_snapshot", String.class)).isEqualTo("#P0Y");
    }

    @Test
    void distingueRespostaInvalidaDeListaVazia() throws Exception {
        mvc.perform(get("/api/clan/members")).andExpect(status().isOk());
        MEMBER_BODY.set("{\"items\":null}");
        mvc.perform(get("/api/clan/members"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("INVALID_UPSTREAM_RESPONSE"));
        assertThat(database.queryForObject("select count(*) from clan_member_snapshot", Integer.class)).isEqualTo(1);
        MEMBER_BODY.set("{\"items\":[]}");
        mvc.perform(get("/api/clan/members")).andExpect(status().isOk());
        assertThat(database.queryForObject("select count(*) from clan_member_snapshot", Integer.class)).isZero();
    }

    @Test
    void publicaDocumentacaoSemTokenDaSupercell() throws Exception {
        var result = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Frota Fantasma Analytics"))
                .andExpect(jsonPath("$.paths['/api/clan'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/clan/members'].get.responses['503']").exists())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("test-only-secret", "apiToken");
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/clans/", exchange -> {
                String path = exchange.getRequestURI().getRawPath();
                boolean valid = "Bearer test-only-secret".equals(exchange.getRequestHeaders().getFirst("Authorization"))
                        && (path.equals("/v1/clans/%23RQJGC9GV") || path.equals("/v1/clans/%23RQJGC9GV/members"));
                String body = path.endsWith("/members") ? MEMBER_BODY.get()
                        : "{\"tag\":\"#RQJGC9GV\",\"name\":\"Frota Fantasma\",\"members\":1,\"clanWarTrophies\":3000}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(valid ? UPSTREAM_STATUS.get() : 403, bytes.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível iniciar a API simulada", exception);
        }
    }
}
