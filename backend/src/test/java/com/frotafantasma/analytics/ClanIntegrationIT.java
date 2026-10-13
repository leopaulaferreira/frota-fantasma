package com.frotafantasma.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sun.net.httpserver.HttpServer;
import com.frotafantasma.analytics.service.CollectionCoordinator;
import com.frotafantasma.analytics.service.CollectionKind;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
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

@SpringBootTest(properties="collection.enabled=false")
@AutoConfigureMockMvc(addFilters=false)
@Testcontainers
class ClanIntegrationIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static final AtomicReference<String> MEMBER_BODY = new AtomicReference<>();
    static final AtomicInteger MEMBER_COUNT = new AtomicInteger(1);
    static final AtomicInteger UPSTREAM_STATUS = new AtomicInteger(200);
    static final AtomicInteger REQUESTS = new AtomicInteger();
    static final HttpServer UPSTREAM = startUpstream();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate database;
    @Autowired DataSource dataSource;
    @Autowired CollectionCoordinator coordinator;

    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("clash-royale.base-url", () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort() + "/v1");
        properties.add("clash-royale.api-token", () -> "test-only-secret");
        properties.add("clash-royale.clan-tag", () -> "#RQJGC9GV");
    }
    @BeforeEach void reset() {
        database.execute("TRUNCATE clan, player, collection_run CASCADE");
        UPSTREAM_STATUS.set(200); REQUESTS.set(0); MEMBER_COUNT.set(1);
        MEMBER_BODY.set("{\"items\":[{\"tag\":\"#P0Y\",\"name\":\"Fenix\",\"role\":\"member\",\"trophies\":9000,\"donations\":198}]}");
    }
    @AfterAll static void stopUpstream() { UPSTREAM.stop(0); }

    @Test void coletaTudoEConsultaSemRequisicoesAdicionais() throws Exception {
        mvc.perform(post("/api/collections/sync")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[1].status").value("SUCCESS"))
                .andExpect(jsonPath("$[2].status").value("SUCCESS"));
        int calls = REQUESTS.get();
        mvc.perform(get("/api/clan")).andExpect(status().isOk()).andExpect(jsonPath("$.members").value(1));
        mvc.perform(get("/api/clan/members")).andExpect(status().isOk()).andExpect(jsonPath("$[0].donations").value(198));
        mvc.perform(get("/api/clan/history")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/clan/events")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].type").value("BASELINE"));
        mvc.perform(get("/api/players/{tag}/history", "#P0Y")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].donations").value(198));
        mvc.perform(get("/api/wars/current")).andExpect(status().isOk()).andExpect(jsonPath("$.race.clan.participants[0].decksUsed").value(12));
        mvc.perform(get("/api/wars")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].seasonId").value(136));
        Long warId = database.queryForObject("select id from war", Long.class);
        mvc.perform(get("/api/wars/{id}", warId)).andExpect(status().isOk()).andExpect(jsonPath("$.participants[0].contribution").value(1600));
        mvc.perform(get("/api/players/{tag}/wars", "#P0Y")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/collections")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        assertThat(REQUESTS.get()).isEqualTo(calls);
    }

    @Test void deduplicaJobPorPeriodoEImportacaoDeGuerra() {
        assertThat(coordinator.collect(CollectionKind.MEMBERS, "members:period1").status()).isEqualTo("SUCCESS");
        int calls = REQUESTS.get();
        assertThat(coordinator.collect(CollectionKind.MEMBERS, "members:period1").status()).isEqualTo("SKIPPED");
        assertThat(REQUESTS.get()).isEqualTo(calls);
        assertThat(database.queryForObject("select count(*) from clan_membership_history", Integer.class)).isEqualTo(1);
        coordinator.collect(CollectionKind.WAR_LOG, "log:1");
        coordinator.collect(CollectionKind.WAR_LOG, "log:2");
        assertThat(database.queryForObject("select count(*) from war", Integer.class)).isEqualTo(1);
    }

    @Test void falhaPreservaDadosEPermiteRetryDoMesmoPeriodo() throws Exception {
        coordinator.collect(CollectionKind.MEMBERS, "first");
        UPSTREAM_STATUS.set(403);
        assertThat(coordinator.collect(CollectionKind.MEMBERS, "next").status()).isEqualTo("FAILED");
        mvc.perform(get("/api/clan/members")).andExpect(status().isOk()).andExpect(jsonPath("$[0].tag").value("#P0Y"));
        assertThat(database.queryForObject("select count(*) from membership_event where type='LEAVE'", Integer.class)).isZero();
        assertThat(database.queryForObject("select error_code from collection_run where status='FAILED'", String.class)).isEqualTo("UPSTREAM_ERROR");
        UPSTREAM_STATUS.set(200);
        assertThat(coordinator.collect(CollectionKind.MEMBERS, "next").status()).isEqualTo("SUCCESS");
        assertThat(database.queryForObject("select count(*) from clan_history", Integer.class)).isEqualTo(2);
    }

    @Test void listaVaziaConfirmadaProduzSaidaMasListaParcialNao() throws Exception {
        coordinator.collect(CollectionKind.MEMBERS, null);
        MEMBER_BODY.set("{\"items\":[]}");
        assertThat(coordinator.collect(CollectionKind.MEMBERS, null).status()).isEqualTo("FAILED");
        MEMBER_COUNT.set(0);
        assertThat(coordinator.collect(CollectionKind.MEMBERS, null).status()).isEqualTo("SUCCESS");
        mvc.perform(get("/api/clan/members")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/analytics/clan")).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentMembers.value").value(0))
                .andExpect(jsonPath("$.currentMembers.origin").value("DIRECT_API"));
        assertThat(database.queryForObject("select count(*) from membership_event where type='LEAVE'", Integer.class)).isEqualTo(1);
    }

    @Test void falhaDeGravacaoReverteSnapshotERegistraFalhaForaDaTransacao() {
        assertThat(coordinator.collect(CollectionKind.MEMBERS, "baseline").status()).isEqualTo("SUCCESS");
        String validMembers = MEMBER_BODY.get();
        // The oversized name fails after the clan snapshot has already been written.
        MEMBER_BODY.set("{\"items\":[{\"tag\":\"#P0Y\",\"name\":\"" + "x".repeat(256)
                + "\",\"role\":\"elder\"}]}");

        var failed = coordinator.collect(CollectionKind.MEMBERS, "retryable");

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.errorCode()).isEqualTo("COLLECTION_STORAGE_ERROR");
        assertThat(database.queryForObject("select count(*) from clan_history", Integer.class)).isEqualTo(1);
        assertThat(database.queryForObject("select count(*) from clan_membership_history", Integer.class)).isEqualTo(1);
        assertThat(database.queryForObject("select role_current from player", String.class)).isEqualTo("member");
        assertThat(database.queryForObject("select count(*) from collection_run where status='FAILED'", Integer.class)).isEqualTo(1);

        MEMBER_BODY.set(validMembers);
        assertThat(coordinator.collect(CollectionKind.MEMBERS, "retryable").status()).isEqualTo("SUCCESS");
        assertThat(database.queryForObject("select count(*) from clan_history", Integer.class)).isEqualTo(2);
    }

    @Test void lockPostgresImpedeColetasConcorrentes() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("select pg_advisory_lock(hashtextextended('frota:collection:#RQJGC9GV', 0))");
            try {
                assertThat(coordinator.collect(CollectionKind.MEMBERS, null).errorCode()).isEqualTo("COLLECTION_IN_PROGRESS");
                assertThat(REQUESTS.get()).isZero();
            } finally {
                statement.execute("select pg_advisory_unlock(hashtextextended('frota:collection:#RQJGC9GV', 0))");
            }
        }
        assertThat(coordinator.collect(CollectionKind.MEMBERS, null).status()).isEqualTo("SUCCESS");
    }

    @Test void recuperaRegistroDeProcessoInterrompido() {
        database.execute("insert into collection_run(id,clan_tag,kind,status,started_at) values (gen_random_uuid(),'#RQJGC9GV','MEMBERS','RUNNING',now())");
        coordinator.collect(CollectionKind.MEMBERS, null);
        assertThat(database.queryForObject("select count(*) from collection_run where error_code='COLLECTION_INTERRUPTED'", Integer.class)).isEqualTo(1);
    }

    @Test void validaParametrosEDocumentacaoSemToken() throws Exception {
        mvc.perform(get("/api/clan/history?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/wars?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/players/invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/wars/current")).andExpect(status().isNotFound());
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/collections/sync'].post").exists()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("test-only-secret", "apiToken");
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test void analyticsUsaLastSeenRealEPreservaLeituraLocalSemAcoesAutomaticas() throws Exception {
        String lastSeen = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSX")
                .withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.now().minus(java.time.Duration.ofDays(6)));
        MEMBER_BODY.set("{\"items\":[{\"tag\":\"#P0Y\",\"name\":\"Fenix\",\"role\":\"member\",\"trophies\":9000,\"donations\":198,\"lastSeen\":\"" + lastSeen + "\"}]}");
        coordinator.sync();
        MEMBER_BODY.set(MEMBER_BODY.get().replace("198", "210"));
        coordinator.collect(CollectionKind.MEMBERS, null);
        int calls = REQUESTS.get();
        int runs = database.queryForObject("select count(*) from collection_run", Integer.class);

        mvc.perform(get("/api/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleVersion").value("analytics-v2"))
                .andExpect(jsonPath("$.players[0].metrics.lastKnownActivity.origin").value("DIRECT_API"))
                .andExpect(jsonPath("$.players[0].metrics.lastKnownActivity.value").isNotEmpty())
                .andExpect(jsonPath("$.players[0].metrics.deckUtilizationPercent.origin").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.players[0].metrics.recentAverage.insufficientSample").value(true))
                .andExpect(jsonPath("$.players[0].metrics.weeklyDonations.value").value(12))
                .andExpect(jsonPath("$.players[0].operationalStatus.value").value("REVISAR"))
                .andExpect(jsonPath("$.players[0].elderCandidate.value").value(false))
                .andExpect(jsonPath("$.clan.membersOffline.value").value(1));
        mvc.perform(get("/api/analytics/players/{tag}", "#P0Y")).andExpect(status().isOk());
        mvc.perform(get("/api/analytics/clan")).andExpect(status().isOk());
        mvc.perform(get("/api/analytics/highlights")).andExpect(status().isOk());
        mvc.perform(get("/api/analytics/promotions")).andExpect(status().isOk());
        mvc.perform(get("/api/analytics/evolution")).andExpect(status().isOk());
        mvc.perform(get("/api/analytics?week=2026-10-06")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analytics?week=invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analytics/players/invalid")).andExpect(status().isBadRequest());
        assertThat(REQUESTS.get()).isEqualTo(calls);
        assertThat(database.queryForObject("select count(*) from collection_run", Integer.class)).isEqualTo(runs);
        assertThat(database.queryForObject("select role_current from player", String.class)).isEqualTo("member");
        assertThat(database.queryForObject("select count(*) from clan_membership_history where api_last_seen is not null", Integer.class)).isEqualTo(2);
    }

    @Test void analyticsSemColetasRetornaDadosInsuficientes() throws Exception {
        mvc.perform(get("/api/analytics")).andExpect(status().isOk())
                .andExpect(jsonPath("$.players").isEmpty())
                .andExpect(jsonPath("$.clan.currentMembers.quality").value("INSUFFICIENT_DATA"))
                .andExpect(jsonPath("$.highlights[0].result.quality").value("INSUFFICIENT_DATA"));
        assertThat(REQUESTS.get()).isZero();
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/clans/", exchange -> {
                REQUESTS.incrementAndGet();
                String path = exchange.getRequestURI().getRawPath();
                boolean valid = "Bearer test-only-secret".equals(exchange.getRequestHeaders().getFirst("Authorization"))
                        && path.startsWith("/v1/clans/%23RQJGC9GV");
                String body;
                if (path.endsWith("/members")) body = MEMBER_BODY.get();
                else if (path.endsWith("/currentriverrace")) body = "{\"sectionIndex\":0,\"periodIndex\":6,\"periodType\":\"warDay\",\"clan\":{\"tag\":\"#RQJGC9GV\",\"participants\":[{\"tag\":\"#P0Y\",\"fame\":1600,\"decksUsed\":12}]}}";
                else if (path.endsWith("/riverracelog")) body = "{\"items\":[{\"seasonId\":136,\"sectionIndex\":3,\"createdDate\":\"20261005T093804.000Z\",\"standings\":[{\"rank\":1,\"trophyChange\":100,\"clan\":{\"tag\":\"#RQJGC9GV\",\"participants\":[{\"tag\":\"#P0Y\",\"fame\":1600,\"decksUsed\":12}]}}]}]}";
                else body = "{\"tag\":\"#RQJGC9GV\",\"name\":\"Frota Fantasma\",\"members\":" + MEMBER_COUNT.get() + "}";
                if (UPSTREAM_STATUS.get() != 200) body = "{\"message\":\"test-only-secret\"}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(valid ? UPSTREAM_STATUS.get() : 403, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start(); return server;
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }
}
