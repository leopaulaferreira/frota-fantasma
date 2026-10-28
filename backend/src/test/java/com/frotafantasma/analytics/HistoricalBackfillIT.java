package com.frotafantasma.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frotafantasma.analytics.analytics.AnalyticsService;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.dto.clash.*;
import com.frotafantasma.analytics.entity.CollectionRun;
import com.frotafantasma.analytics.imports.dto.BackfillDtos.*;
import com.frotafantasma.analytics.imports.dto.IdentificationDtos.Resolve;
import com.frotafantasma.analytics.imports.parser.RoyaleApiCsvParser;
import com.frotafantasma.analytics.imports.service.*;
import com.frotafantasma.analytics.repository.CollectionRunRepository;
import com.frotafantasma.analytics.service.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
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
@AutoConfigureMockMvc @Testcontainers @Import(HistoricalBackfillIT.TimeConfig.class)
class HistoricalBackfillIT {
    @TestConfiguration static class TimeConfig { @Bean @Primary Clock clock(){return Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"),ZoneOffset.UTC);} }
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) { p.add("spring.datasource.url",DB::getJdbcUrl);p.add("spring.datasource.username",DB::getUsername);p.add("spring.datasource.password",DB::getPassword); }
    @Autowired JdbcTemplate sql; @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Autowired RoyaleApiCsvParser parser; @Autowired RoyaleApiImportService imports;
    @Autowired ImportIdentificationService identities; @Autowired HistoricalBackfillService backfill;
    @Autowired AnalyticsService analytics; @Autowired HistoryStore store; @Autowired HistoryQueryService history;
    @Autowired CollectionRunRepository runs; @Autowired ClashRoyaleProperties config;
    final Instant captured=Instant.parse("2026-10-26T12:00:00Z");
    @BeforeEach void clear() { sql.execute("TRUNCATE import_batch,clan,player,collection_run CASCADE"); }
    RiverLogEntryDto official(int section,int points) throws Exception {
        String participants=java.util.stream.IntStream.rangeClosed(1,5).mapToObj(i->"{\"tag\":\"#P0"+i+"\",\"name\":\"Player\",\"fame\":"+points+",\"decksUsed\":16,\"decksUsedToday\":4,\"boatAttacks\":0,\"repairPoints\":0}").collect(java.util.stream.Collectors.joining(","));
        return json.readValue("{\"seasonId\":133,\"sectionIndex\":"+section+",\"createdDate\":\"202610"+String.format("%02d",5+section*7)+"T100000.000Z\",\"standings\":[{\"rank\":1,\"clan\":{\"tag\":\"#RQJGC9GV\",\"name\":\"Frota\",\"participants\":["+participants+"]}}]}",RiverLogEntryDto.class);
    }
    UUID batch(String filename,String contents) {
        return imports.confirm(parser.parse(new MockMultipartFile("file",filename,"text/csv",contents.getBytes(StandardCharsets.UTF_8))),"leader").batch().id();
    }
    UUID race(int points,boolean extra) {
        var text=new StringBuilder(RoyaleApiCsvParser.RIVER).append('\n');
        for(int i=1;i<=(extra?6:5);i++) text.append("full,RQJGC9GV,Frota,16000029,2500,0,,Nome,P0").append(i).append(',').append(points).append(",0,").append(points).append(",0,16,4\n");
        return batch("race-"+points+"-"+extra+".csv",text.toString());
    }
    void identifyWar(UUID id,int section) {
        var a=identities.identify(id);var target=a.candidates().stream().filter(c->c.kind().equals("WAR") && c.sectionIndex()==section).findFirst().orElseThrow();
        identities.resolve(id,new Resolve(UUID.randomUUID(),0,a.evidenceVersion(),"IDENTIFY",target.key(),config.clanTag(),null,null,null,true,"Contexto oficial conferido."),"leader");
    }
    Result apply(UUID id) { var p=backfill.preview(id);return backfill.incorporate(id,new Confirm(p.resolutionId(),p.evidenceVersion(),true),"leader"); }
    UUID memberBatch(String time,String donations) {
        var id=batch("members-"+time.replace(":","-")+".csv",RoyaleApiCsvParser.MEMBERS+"\n#P01,Nome,member,20261026T010000.000Z,0,14000,1,Arena,Arena,1,1,"+donations+",50,0\n");
        var a=identities.identify(id);
        identities.resolve(id,new Resolve(UUID.randomUUID(),0,a.evidenceVersion(),"IDENTIFY",null,config.clanTag(),LocalDateTime.parse("2026-10-26T"+time),"UTC","USER_ENTERED",true,"Conferi clã e instante."),"leader");
        return id;
    }
    @Test void pendingRejectedAndConflictBatchesCannotBeApplied() throws Exception {
        store.saveWarLog(config.clanTag(),List.of(official(3,3200)),captured);
        var id=race(3200,false);
        assertThatThrownBy(()->backfill.preview(id)).hasMessageContaining("409");
        var a=identities.identify(id);
        identities.resolve(id,new Resolve(UUID.randomUUID(),0,a.evidenceVersion(),"REJECTED",null,null,null,null,null,true,"Sem contexto"),"leader");
        assertThatThrownBy(()->backfill.preview(id)).hasMessageContaining("409");
        var conflict=race(3300,false); identifyWar(conflict,3);
        assertThatThrownBy(()->backfill.preview(conflict)).hasMessageContaining("409");
    }
    @Test void newParticipationDuplicateAndReplayPreserveOfficialAndAudit() throws Exception {
        store.saveWarLog(config.clanTag(),List.of(official(3,3200)),captured);
        var id=race(3200,true);identifyWar(id,3);
        var p=backfill.preview(id); assertThat(p.rowsNew()).isEqualTo(1);assertThat(p.rowsDuplicated()).isEqualTo(5);
        assertThat(sql.queryForObject("select count(*) from import_application",Long.class)).isZero();
        var result=apply(id); assertThat(apply(id).id()).isEqualTo(result.id());
        assertThat(sql.queryForObject("select count(*) from war_participant",Long.class)).isEqualTo(6);
        var player=analytics.report(null).players().stream().filter(x->x.tag().equals("#P06")).findFirst().orElseThrow();
        assertThat(player.wars().getFirst().points().origin().name()).isEqualTo("ROYALEAPI_IMPORT");
        assertThat(player.wars().getFirst().points().dataUsed().toString()).contains(id.toString(),"resolution=");
        assertThat(player.wars().getFirst().points().incompletePeriod()).isTrue();
        assertThat(history.playerWars("#P06",0,20).items().getFirst().participation().source()).isEqualTo("ROYALEAPI_IMPORT");
        assertThat(backfill.summary(id).influencesAnalytics()).isTrue();
    }
    @Test void lowerCsvConflictsWithFinalOfficialValuesInsteadOfChangingAnalytics() throws Exception {
        store.saveWarLog(config.clanTag(),List.of(official(3,3200)),captured);
        var id=race(3100,true);identifyWar(id,3);
        var p=backfill.preview(id);assertThat(p.rowsConflicted()).isEqualTo(5); assertThat(p.rowsNew()).isEqualTo(1);
        assertThat(apply(id).status()).isEqualTo("PARTIAL");
        assertThat(analytics.report(null).players().stream().filter(x->x.tag().equals("#P01")).findFirst().orElseThrow().wars().getFirst().points().value()).isEqualTo(3200);
    }
    @Test void restoredConfirmedWarThenSupercellWinsWithoutLosingAuditOrDuplicatingWar() throws Exception {
        store.saveWarLog(config.clanTag(),List.of(official(3,3200)),captured);
        var id=race(3200,false);identifyWar(id,3);
        // The resolution retains real official identifiers/publication even if that historical record is absent locally later.
        sql.update("delete from war_participant");sql.update("delete from war");
        assertThat(backfill.preview(id).rowsNew()).isEqualTo(5);apply(id);
        assertThat(history.wars(0,20).items().getFirst().source()).isEqualTo("ROYALEAPI_IMPORT");
        store.saveWarLog(config.clanTag(),List.of(official(3,3500)),captured.plusSeconds(3600));
        var report=analytics.report(null);var player=report.players().stream().filter(x->x.tag().equals("#P01")).findFirst().orElseThrow();
        assertThat(player.wars()).hasSize(1);assertThat(player.wars().getFirst().points().value()).isEqualTo(3500);
        assertThat(player.wars().getFirst().points().origin().name()).isEqualTo("DIRECT_API");
        assertThat(history.wars(0,20).items()).hasSize(1);assertThat(history.wars(0,20).items().getFirst().source()).isEqualTo("DIRECT_API");
        assertThat(sql.queryForObject("select count(*) from import_application_row",Long.class)).isEqualTo(5);
        assertThat(sql.queryForObject("select evidence->'values'->>'contribution' from import_application_row order by row_number limit 1",String.class)).isEqualTo("3200");
        assertThat(backfill.summary(id).influencesAnalytics()).isFalse();
    }
    @Test void fourWarAverageAndTrendUseMixedSourcesWithoutNewFormula() throws Exception {
        store.saveWarLog(config.clanTag(),List.of(official(0,1600)),captured);
        var id=race(1600,false);identifyWar(id,0);
        sql.update("delete from war_participant");sql.update("delete from war");apply(id);
        store.saveWarLog(config.clanTag(),List.of(official(1,2000),official(2,2400),official(3,2800)),captured);
        var player=analytics.report(null).players().getFirst();
        assertThat(player.wars()).hasSize(4);assertThat(player.metrics().get("recentAverage").value()).isEqualTo(2200.0);
        assertThat(player.metrics().get("recentTrendPoints").value()).isEqualTo(800.0);
        assertThat(player.metrics().get("recentAverage").sources()).extracting(Enum::name).containsExactly("DIRECT_API","ROYALEAPI_IMPORT");
    }
    @Test void membersRespectNullResetsCoverageAndOfficialSameInstantPriority() throws Exception {
        store.saveWarLog(config.clanTag(),List.of(official(3,3200)),captured);
        apply(memberBatch("02:00","100"));apply(memberBatch("03:00","150"));apply(memberBatch("04:00","20"));
        var missing=memberBatch("05:00","");apply(missing);
        var player=analytics.report(null).players().getFirst();
        assertThat(player.donations().getFirst().observedDonations().value()).isEqualTo(70L);
        assertThat(player.donations().getFirst().observedDonations().incompletePeriod()).isTrue();
        assertThat(player.donations().getFirst().detectedResets()).isEqualTo(1);
        assertThat(player.donations().getFirst().lastCounter().value()).isNull();
        assertThat(history.memberships("#P01",0,20).items().getFirst().donations()).isNull();
        UUID run=UUID.randomUUID();runs.save(new CollectionRun(run,config.clanTag(),"MEMBERS","SUCCESS",captured,null,null,null,null));
        store.saveMembers(run,config.clanTag(),new ClanApiDto(config.clanTag(),"Frota","","open",0,0,0,0,1),
                List.of(new ClanMemberApiDto("#P01","Oficial","elder",15,9000,1,1,30,0)),Instant.parse("2026-10-26T05:00:00Z"));
        assertThat(history.memberships("#P01",0,20).items()).hasSize(4);
        assertThat(history.memberships("#P01",0,20).items().getFirst().source()).isEqualTo("DIRECT_API");
        assertThat(sql.queryForObject("select count(*) from clan_membership_history",Long.class)).isEqualTo(5);
    }
    @Test void rollbackLeavesNeitherApplicationNorNewWar() throws Exception {
        store.saveWarLog(config.clanTag(),List.of(official(3,3200)),captured);var id=race(3200,false);identifyWar(id,3);
        sql.update("delete from war_participant");sql.update("delete from war");
        sql.execute("alter table import_application_row add constraint forced_failure check(row_number<0)");
        try { assertThatThrownBy(()->apply(id)).isInstanceOf(org.springframework.dao.DataAccessException.class); }
        finally { sql.execute("alter table import_application_row drop constraint forced_failure"); }
        assertThat(sql.queryForObject("select count(*) from import_application",Long.class)).isZero();
        assertThat(sql.queryForObject("select count(*) from war",Long.class)).isZero();
    }
    @Test void revisedIdentificationWithdrawsOldImportsAndStalePreviewIsRejected() throws Exception {
        store.saveWarLog(config.clanTag(),List.of(official(3,3200)),captured);var id=race(3200,true);identifyWar(id,3);apply(id);
        var a=identities.identify(id);identities.resolve(id,new Resolve(UUID.randomUUID(),a.history().getFirst().revision(),a.evidenceVersion(),"REJECTED",null,null,null,null,null,true,"Revisão rejeitada"),"leader");
        assertThat(backfill.summary(id).influencesAnalytics()).isFalse();
        assertThat(analytics.report(null).players()).noneMatch(p->p.tag().equals("#P06"));
        assertThat(sql.queryForObject("select count(*) from import_application_row",Long.class)).isEqualTo(6);
    }
    @Test void clanCollectionLockAndChangedOfficialDataBlockStaleWrites() throws Exception {
        store.saveWarLog(config.clanTag(),List.of(official(3,3200)),captured);var id=race(3200,true);identifyWar(id,3);
        var plan=backfill.preview(id);var request=new Confirm(plan.resolutionId(),plan.evidenceVersion(),true);
        try(var connection=sql.getDataSource().getConnection();var lock=connection.prepareStatement("select pg_advisory_lock(hashtextextended(?,0))")) {
            lock.setString(1,"frota:collection:"+config.clanTag());lock.execute();
            try { assertThatThrownBy(()->backfill.incorporate(id,request,"leader")).hasMessageContaining("andamento"); }
            finally { try(var release=connection.prepareStatement("select pg_advisory_unlock(hashtextextended(?,0))")) { release.setString(1,"frota:collection:"+config.clanTag());release.execute(); } }
        }
        store.saveWarLog(config.clanTag(),List.of(official(3,3500)),captured.plusSeconds(3600));
        assertThatThrownBy(()->backfill.incorporate(id,request,"leader")).hasMessageContaining("histórico mudou");
        assertThat(sql.queryForObject("select count(*) from import_application",Long.class)).isZero();
    }
    @Test void riverSnapshotIsNotTurnedIntoPublishedWar() throws Exception {
        var entry=official(3,3200);store.saveWarLog(config.clanTag(),List.of(entry),captured);
        UUID run=UUID.randomUUID();runs.save(new CollectionRun(run,config.clanTag(),"RIVER","SUCCESS",captured,null,null,null,null));
        store.saveRiver(run,config.clanTag(),new RiverRaceDto("full",entry.standings().getFirst().clan(),List.of(),3,0,"warDay",null),captured);
        var id=race(3200,false);var a=identities.identify(id);
        var target=a.candidates().stream().filter(c->c.kind().equals("SNAPSHOT")).findFirst().orElseThrow();
        identities.resolve(id,new Resolve(UUID.randomUUID(),0,a.evidenceVersion(),"IDENTIFY",target.key(),config.clanTag(),null,null,null,true,"Snapshot conferido"),"leader");
        var plan=backfill.preview(id);assertThat(plan.rowsNew()).isZero();assertThat(plan.rowsIgnored()).isEqualTo(5);
        assertThat(apply(id).status()).isEqualTo("PARTIAL");assertThat(backfill.summary(id).influencesAnalytics()).isFalse();
        assertThat(history.wars(0,20).items()).hasSize(1);
    }
    @Test void adminPreviewSessionAndCsrfAreRequired() throws Exception {
        store.saveWarLog(config.clanTag(),List.of(official(3,3200)),captured);var id=race(3200,true);identifyWar(id,3);
        String path="/api/imports/royaleapi/"+id+"/backfill";var session=new MockHttpSession();
        mvc.perform(get(path+"/preview").with(user("viewer").roles("VIEWER"))).andExpect(status().isForbidden());
        var response=mvc.perform(get(path+"/preview").session(session).with(user("leader").roles("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var plan=json.readValue(response,Plan.class);String body=json.writeValueAsString(new Confirm(plan.resolutionId(),plan.evidenceVersion(),true));
        mvc.perform(post(path).session(session).with(user("leader").roles("ADMIN")).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("leader").roles("ADMIN")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(post(path).session(session).with(user("viewer").roles("VIEWER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path).session(session).with(user("leader").roles("ADMIN")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.rowsNew").value(1));
        mvc.perform(get("/api/analytics").with(user("leader").roles("ADMIN"))).andExpect(status().isOk());
    }
}
