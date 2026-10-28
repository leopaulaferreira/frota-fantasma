package com.frotafantasma.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frotafantasma.analytics.analytics.AnalyticsService;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.imports.dto.IdentificationDtos.*;
import com.frotafantasma.analytics.imports.parser.RoyaleApiCsvParser;
import com.frotafantasma.analytics.imports.service.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
@org.springframework.context.annotation.Import(ImportIdentificationIT.TimeConfig.class)
class ImportIdentificationIT {
    @org.springframework.boot.test.context.TestConfiguration static class TimeConfig {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        Clock testClock() { return Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"),ZoneOffset.UTC); }
    }
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",DB::getJdbcUrl); p.add("spring.datasource.username",DB::getUsername); p.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired JdbcTemplate sql;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RoyaleApiCsvParser parser;
    @Autowired RoyaleApiImportService imports;
    @Autowired ImportIdentificationService service;
    @Autowired AnalyticsService analytics;
    @Autowired ClashRoyaleProperties config;
    @BeforeEach void seed() {
        sql.execute("TRUNCATE import_batch,clan,player,collection_run CASCADE");
        sql.update("insert into clan(id,tag,name) values(1,?,'Frota Fantasma')",config.clanTag());
        sql.update("insert into player(id,player_tag,name,active) values(1,'#P01','Nome oficial alterado',false)");
        sql.update("insert into war(id,clan_id,season_id,section_index,published_at,captured_at,payload) values(1,1,133,3,'2026-10-26T10:00:00Z',now(),'{}')");
        sql.update("insert into war_participant(war_id,player_id,contribution,decks_used,boat_attacks,repair_points,captured_at) values(1,1,3200,16,0,0,now())");
    }
    UUID batch(String name,String contents) {
        return imports.confirm(parser.parse(new MockMultipartFile("file",name,"text/csv",contents.getBytes(StandardCharsets.UTF_8))),"leader").batch().id();
    }
    UUID river(int points) { return batch("race.csv",RoyaleApiCsvParser.RIVER+"\nfull,RQJGC9GV,Frota,16000029,2500,0,,Nome antigo,P01,"+points+",0,"+points+",0,16,4\n"); }
    UUID members() { return batch("clan-RQJGC9GV-2026-10-26-02-10-94.csv",RoyaleApiCsvParser.MEMBERS+"\n#P01,Nome,member,20261026T010000.000Z,0,14000,1,Arena,Arena,1,1,50,50,0\n"); }
    Resolve request(Identification a,String candidate) {
        return new Resolve(UUID.randomUUID(),a.history().isEmpty()?0:a.history().getFirst().revision(),a.evidenceVersion(),"IDENTIFY",candidate,
                config.clanTag(),null,null,null,true,"Conferi a origem e o contexto.");
    }
    @Test void exactMatchIncludesFormerMemberAndIgnoresChangedNameWithoutWritingOfficialData() {
        var before=analytics.report(null);
        UUID id=river(3200);
        assertThat(analytics.report(null).players()).isEqualTo(before.players());
        var a=service.identify(id);
        assertThat(a.match()).isEqualTo("EXACT_MATCH");
        assertThat(a.candidates().getFirst().matchingTags()).isEqualTo(1);
        var request=request(a,"WAR:1");
        var resolution=service.resolve(id,request,"leader");
        assertThat(resolution.status()).isEqualTo("IDENTIFIED");
        assertThat(resolution.resolutionMethod()).isEqualTo("USER_CONFIRMED_EXACT_MATCH");
        assertThat(resolution.seasonId()).isEqualTo(133);
        assertThat(service.resolve(id,request,"leader")).isEqualTo(resolution);
        assertThat(sql.queryForObject("select contribution from war_participant where war_id=1",Integer.class)).isEqualTo(3200);
        assertThat(sql.queryForObject("select active from player where id=1",Boolean.class)).isFalse();
        assertThat(analytics.report(null).players()).isEqualTo(before.players());
        assertThat(imports.history(0,10).items().getFirst().status()).isEqualTo("IDENTIFIED");
        assertThat(sql.queryForObject("select status from import_batch where id=?",String.class,id)).isEqualTo("PENDING_IDENTIFICATION");
    }
    @Test void partialEarlierSnapshotIsPossibleAndHigherValuesAreConflict() {
        var id=river(3000); var a=service.identify(id);
        assertThat(a.match()).isEqualTo("POSSIBLE_MATCH");
        assertThat(a.candidates().getFirst().officialGreaterValues()).isEqualTo(1);
        assertThat(service.resolve(id,request(a,"WAR:1"),"leader").status()).isEqualTo("IDENTIFIED");
        var other=river(3300); var b=service.identify(other);
        assertThat(service.resolve(other,request(b,"WAR:1"),"leader").status()).isEqualTo("CONFLICT");
        assertThat(sql.queryForObject("select contribution from war_participant where war_id=1",Integer.class)).isEqualTo(3200);
    }
    @Test void snapshotTargetNeverInventsSeasonOrCaptureTimestampForCsv() {
        UUID run=UUID.randomUUID();
        sql.update("insert into collection_run(id,clan_tag,kind,status,started_at) values(?,?,'RIVER','SUCCESS',now())",run,config.clanTag());
        sql.update("insert into river_race_snapshot(id,clan_id,run_id,section_index,period_index,period_type,captured_at,payload) values(1,1,?,3,2,'warDay',now(),?::jsonb)",run,
                "{\"clan\":{\"tag\":\"#RQJGC9GV\",\"participants\":[{\"tag\":\"#P01\",\"name\":\"Outro nome\",\"fame\":3000,\"decksUsed\":16,\"boatAttacks\":0,\"repairPoints\":0}]}}");
        var id=river(3000); var a=service.identify(id);
        var result=service.resolve(id,request(a,"SNAPSHOT:1"),"leader");
        assertThat(result.targetKind()).isEqualTo("SNAPSHOT"); assertThat(result.seasonId()).isNull(); assertThat(result.capturedAt()).isNull();
        assertThat(result.evidence().path("candidate").path("periodIndex").asInt()).isEqualTo(2);
    }
    @Test void memberFilenameSuggestionNeedsManualConfirmationAndExplicitTimezone() {
        var id=members(); var a=service.identify(id);
        assertThat(a.suggestedCapturedAt()).isEqualTo(LocalDateTime.parse("2026-10-26T02:10"));
        assertThat(service.identify(id).history()).isEmpty();
        var request=new Resolve(UUID.randomUUID(),0,a.evidenceVersion(),"IDENTIFY",null,config.clanTag(),a.suggestedCapturedAt(),"America/Sao_Paulo","USER_CONFIRMED_FILENAME",true,"Confirmei o arquivo.");
        var result=service.resolve(id,request,"leader");
        assertThat(result.capturedAt()).isEqualTo(Instant.parse("2026-10-26T05:10:00Z"));
        assertThat(result.resolutionMethod()).isEqualTo("USER_CONFIRMED_FILENAME");
        assertThat(result.evidence().path("suggestedCapturedAt").asText()).startsWith("2026-10-26T02:10");
        assertThat(sql.queryForObject("select count(*) from clan_membership_history",Long.class)).isZero();
        var updated=service.identify(id);
        var correction=new Resolve(UUID.randomUUID(),result.revision(),updated.evidenceVersion(),"IDENTIFY",null,config.clanTag(),a.suggestedCapturedAt().plusHours(1),"America/Sao_Paulo","USER_ENTERED",true,"Corrigi a hora após conferir o export.");
        service.resolve(id,correction,"another-admin");
        assertThat(service.identify(id).history()).hasSize(2);
        assertThat(service.identify(id).history().getFirst().resolutionMethod()).isEqualTo("USER_ENTERED");
        assertThatThrownBy(()->sql.update("update import_resolution set reason='alterado' where batch_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void staleEvidenceAndStaleRevisionCannotBeConfirmed() {
        var id=river(3200); var a=service.identify(id); var old=request(a,"WAR:1");
        sql.update("update war_participant set contribution=3300 where war_id=1");
        assertThatThrownBy(()->service.resolve(id,old,"leader")).hasMessageContaining("evidências oficiais mudaram");
        var fresh=service.identify(id); service.resolve(id,request(fresh,"WAR:1"),"leader");
        assertThatThrownBy(()->service.resolve(id,request(fresh,"WAR:1"),"leader")).hasMessageContaining("outra sessão");
    }
    @Test void unknownTargetCannotBeSelectedAndRejectionCanBeReopenedWithAudit() {
        var id=river(3200); var a=service.identify(id);
        assertThatThrownBy(()->service.resolve(id,request(a,"WAR:999"),"leader")).hasMessageContaining("Escolha uma correspondência");
        var rejected=service.resolve(id,new Resolve(UUID.randomUUID(),0,a.evidenceVersion(),"REJECTED",null,null,null,null,null,true,"Arquivo sem contexto confiável."),"leader");
        assertThat(rejected.status()).isEqualTo("REJECTED");
        var current=service.identify(id);
        service.resolve(id,new Resolve(UUID.randomUUID(),rejected.revision(),current.evidenceVersion(),"PENDING_IDENTIFICATION",null,null,null,null,null,true,"Recebi novas evidências."),"leader");
        assertThat(service.identify(id).history()).extracting(Resolution::status).containsExactly("PENDING_IDENTIFICATION","REJECTED");
    }
    @Test void incorrectFilenameProvenanceFutureDateAndUnconfirmedClanAreRejected() {
        var id=members(); var a=service.identify(id);
        for(var request:List.of(
            new Resolve(UUID.randomUUID(),0,a.evidenceVersion(),"IDENTIFY",null,config.clanTag(),a.suggestedCapturedAt().plusHours(1),"UTC","USER_CONFIRMED_FILENAME",true,"Conferido"),
            new Resolve(UUID.randomUUID(),0,a.evidenceVersion(),"IDENTIFY",null,"#OTHER",a.suggestedCapturedAt(),"UTC","USER_ENTERED",true,"Conferido"),
            new Resolve(UUID.randomUUID(),0,a.evidenceVersion(),"IDENTIFY",null,config.clanTag(),LocalDateTime.parse("2030-01-01T00:00"),"UTC","USER_ENTERED",true,"Conferido"),
            new Resolve(UUID.randomUUID(),0,a.evidenceVersion(),"IDENTIFY",null,config.clanTag(),a.suggestedCapturedAt(),"Invalid/Zone","USER_ENTERED",true,"Conferido"))) {
            assertThatThrownBy(()->service.resolve(id,request,"leader")).hasMessageContaining("400");
        }
        assertThat(service.identify(id).history()).isEmpty();
    }
    @Test void concurrentResolutionsDoNotOverwriteOneAnother() throws Exception {
        var id=river(3200); var a=service.identify(id);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var tasks=new ArrayList<java.util.concurrent.Future<String>>();
            for(int i=0;i<2;i++) tasks.add(executor.submit(()-> {
                gate.await();
                try { service.resolve(id,request(a,"WAR:1"),"leader"); return "saved"; }
                catch(org.springframework.web.server.ResponseStatusException e) { return e.getStatusCode().toString(); }
            }));
            gate.countDown();
            assertThat(List.of(tasks.get(0).get(10,java.util.concurrent.TimeUnit.SECONDS),tasks.get(1).get(10,java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("saved","409 CONFLICT");
        }
        assertThat(service.identify(id).history()).hasSize(1);
    }
    @Test void endpointsEnforceAdminCsrfAndExplicitConfirmation() throws Exception {
        var id=river(3200); var a=service.identify(id); var body=json.writeValueAsString(request(a,"WAR:1"));
        String path="/api/imports/royaleapi/"+id+"/identification";
        mvc.perform(get(path).with(user("viewer").roles("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("viewer").roles("VIEWER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("leader").roles("ADMIN")).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("leader").roles("ADMIN")).with(csrf()).contentType("application/json").content(body.replace("\"confirmed\":true","\"confirmed\":false"))).andExpect(status().isBadRequest());
        mvc.perform(post(path).with(user("leader").roles("ADMIN")).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolvedBy").value("leader"));
        mvc.perform(get(path).with(user("leader").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.history[0].manuallyConfirmed").value(true));
    }
}
