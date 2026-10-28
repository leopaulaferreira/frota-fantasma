package com.frotafantasma.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frotafantasma.analytics.imports.parser.RoyaleApiCsvParser;
import com.frotafantasma.analytics.imports.service.RoyaleApiImportService;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"collection.enabled=false","clash-royale.api-token=test-token"})
@AutoConfigureMockMvc
@Testcontainers
class RoyaleApiImportIT {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",DB::getJdbcUrl); p.add("spring.datasource.username",DB::getUsername); p.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired JdbcTemplate sql;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RoyaleApiCsvParser parser;
    @Autowired RoyaleApiImportService service;
    @Autowired ClashRoyaleProperties config;
    static final String PATH = "/api/imports/royaleapi";
    @BeforeEach void seed() {
        sql.execute("TRUNCATE import_batch,clan,player,collection_run CASCADE");
        sql.update("insert into clan(id,tag) values(1,?)", config.clanTag());
        sql.update("insert into player(id,player_tag,name) values(1,'#P01','Oficial')");
        sql.update("insert into war(id,clan_id,season_id,section_index,published_at,captured_at,payload) values(1,1,100,2,now(),now(),'{}')");
        sql.update("insert into war_participant(war_id,player_id,contribution,decks_used,captured_at) values(1,1,3200,16,now())");
    }
    MockMultipartFile file(String text) { return new MockMultipartFile("file","clan.csv","text/csv",text.getBytes(StandardCharsets.UTF_8)); }
    MockMultipartFile river(int points) { return file(RoyaleApiCsvParser.RIVER + "\nfull,RQJGC9GV,Frota,16000029,2500,0,,Nome,P01," + points + ",0," + points + ",0,16,4\n"); }
    String preview(MockMultipartFile file, MockHttpSession session) throws Exception {
        var body = mvc.perform(multipart(PATH + "/preview").file(file).session(session).with(user("leader").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("previewToken").asText();
    }
    @Test void previewIsReadOnlyAndDoesNotGuessWarFromEqualOrDifferentPoints() throws Exception {
        for (int points : new int[]{3200,3100}) {
            mvc.perform(multipart(PATH + "/preview").file(river(points)).with(user("leader").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.analysis.playersIdentified").value(1))
                .andExpect(jsonPath("$.analysis.rowsUnmatched").value(1)).andExpect(jsonPath("$.analysis.rowsNew").value(0))
                .andExpect(jsonPath("$.analysis.rowsExisting").value(0)).andExpect(jsonPath("$.analysis.rowsConflicted").value(0));
        }
        assertThat(sql.queryForObject("select count(*) from import_batch",Long.class)).isZero();
        assertOfficialPreserved();
    }
    @Test void confirmationAuditsPendingRowsAndIdenticalFileIsIdempotent() throws Exception {
        var session = new MockHttpSession(); var file = river(3100); String token = preview(file,session);
        for (boolean repeat : new boolean[]{false,true}) {
            mvc.perform(multipart(PATH).file(file).param("previewToken",token).session(session).with(user("leader").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alreadyProcessed").value(repeat))
                .andExpect(jsonPath("$.batch.rowsImported").value(0)).andExpect(jsonPath("$.batch.status").value("PENDING_IDENTIFICATION"))
                .andExpect(jsonPath("$.batch.importedBy").value("leader"));
        }
        assertThat(sql.queryForObject("select count(*) from import_batch",Long.class)).isEqualTo(1);
        assertThat(sql.queryForObject("select count(*) from import_row",Long.class)).isEqualTo(1);
        mvc.perform(get(PATH).with(user("leader").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].source").value("ROYALEAPI_IMPORT"));
        assertThatThrownBy(() -> sql.update("update import_batch set filename='tampered.csv'")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertOfficialPreserved();
    }
    @Test void unknownPlayerStaysPendingAndNeverCreatesMembership() {
        var file = file(RoyaleApiCsvParser.MEMBERS + "\n#UNKNOWN,Outro,member,,0,1000,1,Arena,Arena,1,1,42,50,0\n");
        var preview = service.preview(parser.parse(file));
        assertThat(preview.playersIdentified()).isZero();
        assertThat(preview.rows().getFirst().reason()).contains("desconhecido");
        service.confirm(parser.parse(file),"leader");
        assertThat(sql.queryForObject("select count(*) from player",Long.class)).isEqualTo(1);
        assertThat(sql.queryForObject("select count(*) from clan_membership_history",Long.class)).isZero();
    }
    @Test void invalidRecordRejectsEntireConfirmationAndMalformedHeaderNeverWrites() throws Exception {
        var invalid = river(-1); var session = new MockHttpSession(); var token = preview(invalid,session);
        mvc.perform(multipart(PATH).file(invalid).param("previewToken",token).session(session).with(user("leader").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart(PATH+"/preview").file(file("tag,points\nP01,0")).with(user("leader").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        assertThat(sql.queryForObject("select count(*) from import_batch",Long.class)).isZero();
        assertOfficialPreserved();
    }
    @Test void cannotConfirmWithoutPreviewOrWithDifferentFileOrSession() throws Exception {
        var session = new MockHttpSession(); var token = preview(river(3100),session);
        mvc.perform(multipart(PATH).file(river(3000)).param("previewToken",token).session(session).with(user("leader").roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
        mvc.perform(multipart(PATH).file(river(3100)).param("previewToken",token).with(user("leader").roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
    }
    @Test void adminOnlyAndCsrfRequiredIncludingAuditReads() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(user("viewer").roles("VIEWER"))).andExpect(status().isForbidden());
        for (String path : List.of(PATH,PATH+"/preview")) {
            mvc.perform(multipart(path).file(river(3100)).with(user("viewer").roles("VIEWER")).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(multipart(path).file(river(3100)).with(user("leader").roles("ADMIN"))).andExpect(status().isForbidden());
        }
    }
    @Test void transactionRollsBackBatchIfRowPersistenceFails() {
        sql.execute("alter table import_row add constraint forced_failure check (row_number < 0)");
        try {
            assertThatThrownBy(() -> service.confirm(parser.parse(river(3100)),"leader")).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(sql.queryForObject("select count(*) from import_batch",Long.class)).isZero();
            assertThat(sql.queryForObject("select count(*) from import_row",Long.class)).isZero();
        } finally { sql.execute("alter table import_row drop constraint forced_failure"); }
        assertOfficialPreserved();
    }
    @Test void simultaneousConfirmationsCreateOnlyOneBatch() throws Exception {
        var parsed = parser.parse(river(3100));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate = new java.util.concurrent.CountDownLatch(1);
            var first = executor.submit(() -> { gate.await(); return service.confirm(parsed,"leader"); });
            var second = executor.submit(() -> { gate.await(); return service.confirm(parsed,"leader"); });
            gate.countDown();
            var a = first.get(10,java.util.concurrent.TimeUnit.SECONDS);
            var b = second.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(a.batch().id()).isEqualTo(b.batch().id());
            assertThat(a.alreadyProcessed()).isNotEqualTo(b.alreadyProcessed());
        }
        assertThat(sql.queryForObject("select count(*) from import_batch",Long.class)).isEqualTo(1);
    }
    @Test void emptyAndExcessiveFilesNeverCreateAuditOrDomainRows() throws Exception {
        mvc.perform(multipart(PATH+"/preview").file(file("")).with(user("leader").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart(PATH+"/preview").file(file("x".repeat(RoyaleApiCsvParser.MAX_BYTES+1))).with(user("leader").roles("ADMIN")).with(csrf())).andExpect(status().isPayloadTooLarge());
        assertThat(sql.queryForObject("select count(*) from import_batch",Long.class)).isZero();
        assertOfficialPreserved();
    }
    void assertOfficialPreserved() {
        assertThat(sql.queryForObject("select contribution from war_participant where war_id=1",Integer.class)).isEqualTo(3200);
        assertThat(sql.queryForObject("select count(*) from war",Long.class)).isEqualTo(1);
        assertThat(sql.queryForObject("select count(*) from collection_run",Long.class)).isZero();
    }
}
