package com.frotafantasma.analytics;

import com.frotafantasma.analytics.leadership.LeadershipNotes;
import com.frotafantasma.analytics.leadership.LeadershipNotes.*;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"collection.enabled=false","clash-royale.api-token=test-token"})
@Testcontainers
@Import(LeadershipNotesIT.TimeConfig.class)
class LeadershipNotesIT {
    static final Instant NOW=Instant.parse("2026-10-19T12:00:00Z");
    @TestConfiguration static class TimeConfig { @Bean @Primary Clock testClock() { return Clock.fixed(NOW,ZoneOffset.UTC); } }
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",DB::getJdbcUrl);p.add("spring.datasource.username",DB::getUsername);p.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired LeadershipNotes service;
    @Autowired JdbcTemplate sql;
    @Autowired ClashRoyaleProperties config;
    @BeforeEach void seed() {
        sql.execute("TRUNCATE clan,player,collection_run CASCADE");
        sql.update("insert into clan(id,tag) values (1,?)",config.clanTag());
        sql.update("insert into player(id,player_tag,clan_id) values (1,'#P01',1)");
        UUID run=UUID.randomUUID();
        sql.update("insert into collection_run(id,clan_tag,kind,status,started_at) values (?,?,'TEST','SUCCESS',?)",run,config.clanTag(),java.sql.Timestamp.from(NOW));
        sql.update("insert into clan_membership_history(player_id,clan_id,run_id,captured_at) values (1,1,?,?)",run,java.sql.Timestamp.from(NOW));
    }
    Create request() { return new Create(UUID.randomUUID(),Kind.JUSTIFICATION,"Viagem avisada",LocalDate.parse("2026-10-20"),LocalDate.parse("2026-10-19")); }
    @Test void storesAuthorExpiresInLocalTimezoneAndPreservesHistory() {
        var note=service.create("#P01",request(),"lider");
        assertThat(note.createdBy()).isEqualTo("lider");
        assertThat(note.followUpDue()).isTrue();
        assertThat(service.active().notes()).hasSize(1);
        var beforeMidnight=new LeadershipNotes(sql,config,Clock.fixed(Instant.parse("2026-10-21T02:59:59Z"),ZoneOffset.UTC));
        assertThat(beforeMidnight.active().notes()).hasSize(1);
        var tomorrow=new LeadershipNotes(sql,config,Clock.fixed(Instant.parse("2026-10-21T03:00:00Z"),ZoneOffset.UTC));
        assertThat(tomorrow.active().notes()).isEmpty();
        assertThat(tomorrow.history("#P01",0,20).items().getFirst().status()).isEqualTo("EXPIRED");
        var resolved=service.resolve(note.id(),new Resolve("Membro retornou"),"lider");
        assertThat(resolved.body()).isEqualTo("Viagem avisada");
        assertThat(resolved.resolution()).isEqualTo("Membro retornou");
        assertThat(service.resolve(note.id(),new Resolve("Outra tentativa"),"lider")).isEqualTo(resolved);
        assertThat(service.active().notes()).isEmpty();
        assertThat(service.history("#P01",0,20).totalElements()).isEqualTo(1);
        assertThatThrownBy(()->sql.update("update leadership_note set body='alterado' where id=?",note.id())).hasMessageContaining("immutable");
    }
    @Test void concurrentRetriesCreateOneNoteAndRejectReusedKey() throws Exception {
        var request=request();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->service.create("#P01",request,"lider"));
            var b=pool.submit(()->service.create("#P01",request,"lider"));
            assertThat(a.get(10,TimeUnit.SECONDS).id()).isEqualTo(b.get(10,TimeUnit.SECONDS).id());
        }
        assertThat(service.history("#P01",0,20).totalElements()).isEqualTo(1);
        assertThatThrownBy(()->service.create("#P01",new Create(request.requestKey(),Kind.OBSERVATION,"Outro",null,null),"lider")).hasMessageContaining("409");
    }
    @Test void validatesDatesMemberScopeAndPagination() {
        assertThatThrownBy(()->service.create("#UNKNOWN",request(),"lider")).hasMessageContaining("404");
        assertThatThrownBy(()->service.create("#P01",new Create(UUID.randomUUID(),Kind.OBSERVATION,"Texto",LocalDate.parse("2026-10-18"),null),"lider")).hasMessageContaining("400");
        assertThatThrownBy(()->service.create("#P01",new Create(UUID.randomUUID(),Kind.OBSERVATION,"Texto",LocalDate.parse("2026-10-20"),LocalDate.parse("2026-10-21")),"lider")).hasMessageContaining("400");
        service.create("#P01",request(),"lider");
        service.create("#P01",request(),"lider");
        var first=service.history("#P01",0,1);
        var second=service.history("#P01",1,1);
        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.items().getFirst().id()).isNotEqualTo(second.items().getFirst().id());
        var other=mock(ClashRoyaleProperties.class);when(other.clanTag()).thenReturn("#OTHER");
        var isolated=new LeadershipNotes(sql,other,Clock.fixed(NOW,ZoneOffset.UTC));
        assertThat(isolated.history("#P01",0,20).items()).isEmpty();
        assertThat(isolated.active().notes()).isEmpty();
    }
    @Test void candidacyWatchRequiresReviewDateAndKeepsItsKind() {
        assertThatThrownBy(()->service.create("#P01",new Create(UUID.randomUUID(),Kind.CANDIDACY,"Observar consistência",null,null),"lider")).hasMessageContaining("400");
        var note=service.create("#P01",new Create(UUID.randomUUID(),Kind.CANDIDACY,"Observar consistência",null,LocalDate.parse("2026-10-20")),"lider");
        assertThat(note.kind()).isEqualTo(Kind.CANDIDACY);
        assertThat(service.active().notes()).extracting(Note::kind).containsExactly(Kind.CANDIDACY);
        service.resolve(note.id(),new Resolve("Reavaliação concluída"),"lider");
        assertThat(service.active().notes()).isEmpty();
        assertThat(service.history("#P01",0,20).items().getFirst().kind()).isEqualTo(Kind.CANDIDACY);
    }

}
