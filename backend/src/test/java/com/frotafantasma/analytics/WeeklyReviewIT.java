package com.frotafantasma.analytics;

import com.frotafantasma.analytics.weeklyreview.*;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
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
@Import(WeeklyReviewIT.TimeConfig.class)
class WeeklyReviewIT {
    static final Instant NOW=Instant.parse("2026-10-19T12:00:00Z");
    static final Instant BEFORE=Instant.parse("2026-10-19T00:59:00Z");
    @TestConfiguration static class TimeConfig { @Bean @Primary Clock testClock(){return Clock.fixed(NOW,ZoneOffset.UTC);} }
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("spring.datasource.url",DB::getJdbcUrl);p.add("spring.datasource.username",DB::getUsername);p.add("spring.datasource.password",DB::getPassword);}
    @Autowired WeeklyReviewService service;
    @Autowired WeeklyReviewDecisions decisions;
    @Autowired JdbcTemplate sql;
    @Autowired ClashRoyaleProperties api;
    @Autowired MockMvc mvc;
    @BeforeEach void seed(){
        sql.execute("TRUNCATE clan,player,collection_run CASCADE");
        sql.update("insert into clan(id,tag) values(1,?)",api.clanTag());
        sql.update("insert into player(id,player_tag,clan_id) values(1,'#P01',1),(2,'#P02',1),(3,'#LEFT',1)");
        roster(BEFORE.minusSeconds(3600),3);
        roster(BEFORE,1);
        race(BEFORE,1599);
        // These observations must never rewrite Sunday's review.
        roster(BEFORE.plusSeconds(120),2);
        race(BEFORE.plusSeconds(120),3000);
    }
    UUID run(Instant at){UUID id=UUID.randomUUID();sql.update("insert into collection_run(id,clan_tag,kind,status,started_at) values(?,?,'TEST','SUCCESS',?)",id,api.clanTag(),Timestamp.from(at));return id;}
    void roster(Instant at,int count){
        UUID id=run(at);
        sql.update("insert into clan_history(clan_id,run_id,member_count,captured_at) values(1,?,?,?)",id,count,Timestamp.from(at));
        for(int i=1;i<=count;i++)sql.update("insert into clan_membership_history(player_id,clan_id,run_id,name,role,captured_at,api_last_seen) values(?,1,?,?,'member',?,?)",i,id,"Membro "+i,Timestamp.from(at),Timestamp.from(at.minusSeconds(30)));
    }
    void race(Instant at,int points){sql.update("insert into river_race_snapshot(clan_id,run_id,period_type,captured_at,payload) values(1,?,'warDay',?,?::jsonb)",run(at),Timestamp.from(at),"{\"clan\":{\"participants\":[{\"tag\":\"#P01\",\"fame\":"+points+"}]}}");}
    @Test void usesRosterAndRaceAtCutoffAndPreservesHistoryAfterNewCollections(){
        var r=service.report(null);
        assertThat(r.week()).isEqualTo(LocalDate.parse("2026-10-12"));assertThat(r.phase()).isEqualTo("CLOSED");
        assertThat(r.members()).hasSize(1);assertThat(r.members().getFirst().tag()).isEqualTo("#P01");
        assertThat(r.members().getFirst().points()).isEqualTo(1599);assertThat(r.members().getFirst().belowPoints()).isTrue();
        assertThat(r.membersCapturedAt()).isEqualTo(BEFORE);assertThat(r.riverCapturedAt()).isEqualTo(BEFORE);
        assertThat(r.collectionEnabled()).isFalse();
        var live=service.report(LocalDate.parse("2026-10-19"));
        assertThat(live.phase()).isEqualTo("LIVE");assertThat(live.members()).hasSize(2);assertThat(live.riverFresh()).isFalse();
        assertThat(service.report(null)).isEqualTo(r);
    }
    @Test void adminOnlyAndInvalidWeeksRejected() throws Exception {
        mvc.perform(get("/api/leadership/weekly-review")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/leadership/weekly-review").with(user("viewer").roles("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/leadership/weekly-review").with(user("leader").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.members[0].points").value(1599));
        mvc.perform(get("/api/leadership/weekly-review?week=2026-10-20").with(user("leader").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/leadership/weekly-review?week=2026-10-26").with(user("leader").roles("ADMIN"))).andExpect(status().isBadRequest());
    }
    @Test void missingSourcesAreVisibleAndReadsDoNotCreateRecords(){
        sql.execute("TRUNCATE clan,player,collection_run CASCADE");
        var r=service.report(null);assertThat(r.members()).isEmpty();assertThat(r.membersFresh()).isFalse();assertThat(r.riverFresh()).isFalse();assertThat(r.rosterComplete()).isFalse();
        assertThat(sql.queryForObject("select count(*) from collection_run",Long.class)).isZero();
    }

    @Test void decisionsPreserveHistoryEvidenceAndRejectStaleWrites() {
        var week=LocalDate.parse("2026-10-12");
        var request=new WeeklyReviewDecisions.Create(week,WeeklyReviewDecisions.Action.WAIT,"Avisou viagem",UUID.randomUUID(),0L);
        var first=decisions.create("#P01",request,"leader");
        assertThat(decisions.create("#P01",request,"leader")).isEqualTo(first);
        assertThat(decisions.history(week)).hasSize(1);
        assertThat(sql.queryForObject("select evidence->'member'->>'points' from weekly_review_decision where revision=?",String.class,first.revision())).isEqualTo("1599");
        var keep=new WeeklyReviewDecisions.Create(week,WeeklyReviewDecisions.Action.KEEP,"Justificativa aceita",UUID.randomUUID(),first.revision());
        var second=decisions.create("#P01",keep,"other-leader");
        assertThat(decisions.history(week)).containsExactly(second,first);
        assertThat(decisions.create("#P01",request,"leader")).isEqualTo(first);
        assertThat(decisions.history(week.plusWeeks(1))).isEmpty();
        assertThatThrownBy(()->decisions.create("#P01",new WeeklyReviewDecisions.Create(week,WeeklyReviewDecisions.Action.DISMISS,"Outra sessão",UUID.randomUUID(),first.revision()),"leader"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("409");
        assertThatThrownBy(()->decisions.create("#P01",new WeeklyReviewDecisions.Create(week,WeeklyReviewDecisions.Action.DISMISS,"Chave reutilizada",request.requestKey(),0L),"leader"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("409");
        assertThatThrownBy(()->decisions.create("#P02",new WeeklyReviewDecisions.Create(week,WeeklyReviewDecisions.Action.KEEP,"Fora do recorte",UUID.randomUUID(),0L),"leader"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(()->sql.update("update weekly_review_decision set reason='alterada' where revision=?",first.revision()))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        var reopened=decisions.create("#P01",new WeeklyReviewDecisions.Create(week,WeeklyReviewDecisions.Action.PENDING,"Reavaliar evidência",UUID.randomUUID(),second.revision()),"leader");
        assertThat(decisions.history(week)).containsExactly(reopened,second,first);
    }
    @Test void decisionEndpointsRequireAdminCsrfAndValidatedInput() throws Exception {
        String base="/api/leadership/weekly-review/decisions";
        String body="""
          {"week":"2026-10-12","action":"KEEP","reason":"Contexto revisado","requestKey":"%s","expectedRevision":0}
          """.formatted(UUID.randomUUID());
        mvc.perform(get(base+"?week=2026-10-12").with(user("viewer").roles("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(post(base+"/#P01").with(user("leader").roles("ADMIN")).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(base+"/{tag}","#P01").with(user("viewer").roles("VIEWER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(base+"/{tag}","#P01").with(user("leader").roles("ADMIN")).with(csrf()).contentType("application/json").content(body.replace("Contexto revisado"," "))).andExpect(status().isBadRequest());
        mvc.perform(post(base+"/{tag}","#P01").with(user("leader").roles("ADMIN")).with(csrf()).contentType("application/json").content(body.replace("2026-10-12","2026-10-26"))).andExpect(status().isBadRequest());
        mvc.perform(post(base+"/{tag}","#P01").with(user("leader").roles("ADMIN")).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.createdBy").value("leader"));
        mvc.perform(get(base+"?week=2026-10-12").with(user("leader").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].action").value("KEEP"));
        assertThat(sql.queryForObject("select count(*) from weekly_review_decision",Long.class)).isEqualTo(1);
        assertThat(service.report(LocalDate.parse("2026-10-12")).members().getFirst().status()).isEqualTo("REVIEW");
    }

    @Test void concurrentSessionsCannotOverwriteEachOther() throws Exception {
        var week=LocalDate.parse("2026-10-12");
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results=new ArrayList<java.util.concurrent.Future<Integer>>();
            for(int i=0;i<2;i++) results.add(executor.submit(()->{
                start.await();
                try {
                    decisions.create("#P01",new WeeklyReviewDecisions.Create(week,WeeklyReviewDecisions.Action.KEEP,"Revisado",UUID.randomUUID(),0L),"leader");
                    return 200;
                } catch(org.springframework.web.server.ResponseStatusException e) {return e.getStatusCode().value();}
            }));
            start.countDown();
            assertThat(List.of(results.get(0).get(10,java.util.concurrent.TimeUnit.SECONDS),results.get(1).get(10,java.util.concurrent.TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200,409);
        }
        assertThat(decisions.history(week)).hasSize(1);
    }
}
