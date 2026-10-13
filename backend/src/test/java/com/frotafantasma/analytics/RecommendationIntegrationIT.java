package com.frotafantasma.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frotafantasma.analytics.analytics.*;
import com.frotafantasma.analytics.dto.clash.*;
import com.frotafantasma.analytics.entity.CollectionRun;
import com.frotafantasma.analytics.recommendation.*;
import com.frotafantasma.analytics.recommendation.RecommendationDtos.*;
import com.frotafantasma.analytics.repository.CollectionRunRepository;
import com.frotafantasma.analytics.service.HistoryStore;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(properties={"collection.enabled=false","clash-royale.api-token=test-token"})
@AutoConfigureMockMvc(addFilters=false)
@Testcontainers
@Import(RecommendationIntegrationIT.TimeConfig.class)
class RecommendationIntegrationIT {
    static final Instant NOW=Instant.parse("2026-10-19T12:00:00Z");
    static final LocalDate WEEK=LocalDate.of(2026,10,12);
    @TestConfiguration static class TimeConfig { @Bean @Primary Clock testClock() { return Clock.fixed(NOW,ZoneOffset.UTC); } }
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",DB::getJdbcUrl); p.add("spring.datasource.username",DB::getUsername); p.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired RecommendationService service;
    @Autowired RecommendationRulesService rules;
    @Autowired AnalyticsService analytics;
    @Autowired HistoryStore store;
    @Autowired CollectionRunRepository runs;
    @Autowired JdbcTemplate sql;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @BeforeEach void seed() throws Exception {
        sql.execute("TRUNCATE recommendation_rule_version,clan,player,collection_run CASCADE");
        members(Instant.parse("2026-08-01T10:00:00Z"),0);
        members(Instant.parse("2026-10-12T10:00:00Z"),0);
        members(Instant.parse("2026-10-18T23:30:00Z"),200);
        for (int i=0;i<4;i++) {
            String date=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSX").withZone(ZoneOffset.UTC).format(Instant.parse("2026-10-12T09:40:00Z").minus(Duration.ofDays(7L*i)));
            var entry=json.readValue("""
                {"seasonId":137,"sectionIndex":%d,"createdDate":"%s","standings":[{"rank":1,"trophyChange":100,
                "clan":{"tag":"#RQJGC9GV","participants":[{"tag":"#P01","name":"A","fame":3200,"decksUsed":16},
                {"tag":"#P02","name":"B","fame":3000,"decksUsed":16},{"tag":"#P03","name":"C","fame":2800,"decksUsed":16}]}}]}
                """.formatted(3-i,date),RiverLogEntryDto.class);
            store.saveWarLog("#RQJGC9GV",List.of(entry),NOW);
        }
    }
    void members(Instant at,int donation) {
        UUID run=UUID.randomUUID();
        runs.save(new CollectionRun(run,"#RQJGC9GV","TEST","SUCCESS",at,at,null,3,null));
        String seen=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSX").withZone(ZoneOffset.UTC).format(at.minusSeconds(60));
        var members=java.util.stream.LongStream.rangeClosed(1,3).mapToObj(i->new ClanMemberApiDto("#P0"+i,"P"+i,"member",15,9000,1,1,donation,0,seen)).toList();
        store.saveMembers(run,"#RQJGC9GV",new ClanApiDto("#RQJGC9GV","Frota","","open",100,3000,0,donation*3,3),members,at);
    }
    RuleVersion change(int target,boolean score) {
        var current=rules.current(); var old=current.settings().analytics();
        var a=new AnalyticsProperties(target,old.offlineDays(),old.minimumWars(),old.immortalMinimumWars(),old.reviewLowWars(),old.adequateStandardDeckPercent(),old.promotionTenureDays(),old.promotionDonations(),old.freshness(),old.timezone());
        return rules.change(new RuleChange(current.id(),new Settings(a,new ScoreSettings(score,45,35,20,3200,200),28,false),"Teste de revisão"));
    }
    @Test void regrasEditaveisValidacaoEControleDeConcorrencia() throws Exception {
        mvc.perform(get("/api/recommendations/rules")).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(0));
        var settings=rules.current().settings();
        var invalid=new RuleChange(null,new Settings(settings.analytics(),new ScoreSettings(true,45,35,19,3200,200),28,false),"inválido");
        mvc.perform(post("/api/recommendations/rules").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        assertThat(sql.queryForObject("select count(*) from recommendation_rule_version",Integer.class)).isZero();
        var v1=change(1600,true);
        mvc.perform(post("/api/recommendations/rules").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new RuleChange(null,v1.settings(),"stale")))).andExpect(status().isConflict());
        assertThat(rules.current().settings().score().enabled()).isTrue();
        assertThat(analytics.report(WEEK).recommendationRules().id()).isEqualTo(v1.id());
        mvc.perform(get("/api/recommendations/rules/history?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/recommendations/rules/history")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void persisteEvidenciasScoreECoresSemAlterarCargo() throws Exception {
        var version=change(1600,true);
        var result=mvc.perform(post("/api/recommendations/evaluations").contentType(MediaType.APPLICATION_JSON).content("{\"week\":\"2026-10-12\",\"idempotencyKey\":\"week-12\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.report.players[0].metrics.frotaScore.value").value(100.0))
                .andExpect(jsonPath("$.report.players[0].metrics.frotaScore.quality").value("INCOMPLETE")).andReturn();
        var e=json.readValue(result.getResponse().getContentAsString(),Evaluation.class);
        assertThat(e.ruleVersionId()).isEqualTo(version.id());
        assertThat(sql.queryForObject("select count(*) from member_status where color='GREEN'",Integer.class)).isEqualTo(3);
        assertThat(sql.queryForList("select role_current from player",String.class)).containsOnly("member");
        mvc.perform(get("/api/recommendations/players/{tag}/history","#P01")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].evidence.elderCandidate.value").value(true));
        mvc.perform(get("/api/recommendations/evaluations/{id}",e.id())).andExpect(status().isOk()).andExpect(jsonPath("$.report.recommendationRules.revision").value(1));
        assertThat(service.evaluate(new EvaluateRequest(WEEK,"week-12")).id()).isEqualTo(e.id());
        analytics.report(WEEK);
        assertThat(sql.queryForObject("select count(*) from recommendation_evaluation",Integer.class)).isEqualTo(1);
    }
    @Test void revisoesNaoReescrevemAvaliacoesEChavesRepetidasMantemOriginais() {
        var original=service.evaluate(new EvaluateRequest(WEEK,"original"));
        change(4000,true);
        var updated=service.evaluate(new EvaluateRequest(WEEK,"updated"));
        assertThat(updated.ruleVersionId()).isNotEqualTo(original.ruleVersionId());
        assertThat(service.evaluation(original.id()).report()).isEqualTo(original.report());
        assertThat(service.evaluate(new EvaluateRequest(WEEK,"original")).ruleVersionId()).isEqualTo(original.ruleVersionId());
        assertThatThrownBy(()->service.evaluate(new EvaluateRequest(WEEK.minusWeeks(1),"original"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->sql.update("update recommendation_evaluation set formula_version='oops' where id=?",original.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void alertasHistoricosBloqueiamCandidaturaMesmoComStatusAtualRecuperado() {
        change(4000,false);
        var first=service.evaluate(new EvaluateRequest(WEEK,"alert"));
        assertThat(first.report().path("players").get(0).path("operationalStatus").path("value").asText()).isEqualTo("REVISAR");
        change(1600,false);
        var recovered=service.evaluate(new EvaluateRequest(WEEK,"recovered"));
        var p=recovered.report().path("players").get(0);
        assertThat(p.path("operationalStatus").path("value").asText()).isEqualTo("MANTER");
        assertThat(p.path("elderCandidate").path("value").asBoolean()).isFalse();
        assertThat(p.path("elderCandidate").path("reason").asText()).contains("Candidatura bloqueada");
        assertThat(p.path("metrics").path("recentAlerts").path("value").asInt()).isEqualTo(1);
    }
    @Test void premiaSemanaEncerradaUmaVezSemDoisTitulosPorJogador() throws Exception {
        var e=service.evaluate(new EvaluateRequest(WEEK,"awards"));
        var batch=service.finalizeAwards(e.id());
        assertThat(batch.awards()).hasSize(3).extracting(FinalAward::playerTag).doesNotHaveDuplicates();
        assertThat(service.finalizeAwards(e.id())).isEqualTo(batch);
        var other=service.evaluate(new EvaluateRequest(WEEK,"other"));
        mvc.perform(post("/api/recommendations/evaluations/{id}/finalize-awards",other.id())).andExpect(status().isConflict());
        change(4000,true);
        assertThat(service.awards(WEEK)).isEqualTo(batch);
        mvc.perform(get("/api/recommendations/awards/2026-10-12")).andExpect(status().isOk()).andExpect(jsonPath("$.awards.length()").value(3));
        assertThatThrownBy(()->sql.update("delete from weekly_award where clan_tag='#RQJGC9GV'" )).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void consultaTitulosPaginadosPorMembroPreservaEvidencias() throws Exception {
        var evaluation=service.evaluate(new EvaluateRequest(WEEK,"received"));
        var batch=service.finalizeAwards(evaluation.id());
        mvc.perform(get("/api/recommendations/awards?size=1&page=0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].week").value("2026-10-12"));
        var winner=batch.awards().getFirst();
        mvc.perform(get("/api/recommendations/players/{tag}/awards",winner.playerTag()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].playerTag").value(winner.playerTag()))
                .andExpect(jsonPath("$.items[0].evaluationId").value(evaluation.id().toString()))
                .andExpect(jsonPath("$.items[0].evidence.result.reason").isNotEmpty());
        mvc.perform(get("/api/recommendations/players/{tag}/awards","#UNKNOWN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/recommendations/awards?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/recommendations/awards?page=3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.totalElements").value(3));
        assertThat(service.receivedAwards(winner.playerTag(),0,20).items().getFirst().evidence()).isEqualTo(winner.evidence());
    }

    @Test void alertaFuturoOuForaDaJanelaNaoBloqueiaCandidatoHistorico() {
        service.evaluate(new EvaluateRequest(null,"future-alert"));
        var historic=analytics.report(WEEK);
        assertThat(historic.players().getFirst().metrics().get("recentAlerts").value()).isEqualTo(0);
        assertThat(historic.players().getFirst().elderCandidate().value()).isTrue();
        service.evaluate(new EvaluateRequest(WEEK.minusWeeks(1),"old-alert"));
        var active=rules.current();
        rules.change(new RuleChange(active.id(),new Settings(active.settings().analytics(),active.settings().score(),1,false),"Janela de um dia"));
        assertThat(analytics.report(WEEK).players().getFirst().metrics().get("recentAlerts").value()).isEqualTo(0);
    }
    @Test void naoFinalizaSemanaAbertaOuSemCandidatos() throws Exception {
        var open=service.evaluate(new EvaluateRequest(null,"open"));
        mvc.perform(post("/api/recommendations/evaluations/{id}/finalize-awards",open.id())).andExpect(status().isConflict());
        var empty=service.evaluate(new EvaluateRequest(LocalDate.of(2026,7,6),"empty"));
        mvc.perform(post("/api/recommendations/evaluations/{id}/finalize-awards",empty.id())).andExpect(status().isConflict());
        assertThat(sql.queryForObject("select count(*) from weekly_award_batch",Integer.class)).isZero();
    }
    @Test void chamadasConcorrentesNaoDuplicamAvaliacaoOuPremiacao() throws Exception {
        try (var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->service.evaluate(new EvaluateRequest(WEEK,"concurrent")));
            var b=executor.submit(()->service.evaluate(new EvaluateRequest(WEEK,"concurrent")));
            var first=a.get(20,TimeUnit.SECONDS); var second=b.get(20,TimeUnit.SECONDS);
            assertThat(first.id()).isEqualTo(second.id());
            var c=executor.submit(()->service.finalizeAwards(first.id()));
            var d=executor.submit(()->service.finalizeAwards(first.id()));
            assertThat(c.get(20,TimeUnit.SECONDS)).isEqualTo(d.get(20,TimeUnit.SECONDS));
        }
        assertThat(sql.queryForObject("select count(*) from recommendation_evaluation",Integer.class)).isEqualTo(1);
        assertThat(sql.queryForObject("select count(*) from weekly_award",Integer.class)).isEqualTo(3);
    }
}
