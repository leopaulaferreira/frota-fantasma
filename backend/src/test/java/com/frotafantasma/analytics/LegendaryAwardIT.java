package com.frotafantasma.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import com.frotafantasma.analytics.client.*;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.dto.clash.PlayerProfileDto;
import com.frotafantasma.analytics.legendary.*;
import com.frotafantasma.analytics.legendary.LegendaryDtos.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(properties={"collection.enabled=false","clash-royale.api-token=test-token","awards.legendary.era-start=2026-11-01T00:00:00Z"})
@AutoConfigureMockMvc
@Testcontainers
@Import(LegendaryAwardIT.TimeConfig.class)
class LegendaryAwardIT {
    static final String CLAN="#RQJGC9GV";
    static final Instant CLOSE=Instant.parse("2026-11-30T09:00:00Z");
    static final LocalDate WEEK=LocalDate.of(2026,11,30);
    static class MutableClock extends Clock {
        volatile Instant now=CLOSE.plusSeconds(60);
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @TestConfiguration static class TimeConfig { @Bean @Primary MutableClock clock() { return new MutableClock(); } }
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",DB::getJdbcUrl);p.add("spring.datasource.username",DB::getUsername);p.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired com.frotafantasma.analytics.config.CollectionProperties collection;
    @Autowired LegendaryService service;
    @Autowired LegendaryRepository repository;
    @Autowired LegendaryProperties settings;
    @Autowired ClashRoyaleProperties api;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate sql;
    @Autowired DataSource database;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @MockitoBean ClashRoyaleClient client;
    long clan;
    @BeforeEach void seed() {
        sql.execute("truncate legendary_cycle,clan,player,collection_run cascade");
        reset(client);clock.now=CLOSE.plusSeconds(60);
        clan=sql.queryForObject("insert into clan(tag) values (?) returning id",Long.class,CLAN);
        for(String tag:List.of("#AAA","#BBB")) sql.update("insert into player(player_tag,name,clan_id,active,role_current) values (?,?,?,true,'member')",tag,tag,clan);
        roster(CLOSE.minus(Duration.ofDays(7)).minusSeconds(60),9000);
        roster(CLOSE.minusSeconds(60),9000);
        profiles(9000,9000);
        war(CLOSE,0);
    }
    void profiles(int trophies,int best) {
        for(String tag:List.of("#AAA","#BBB")) doReturn(new PlayerProfileDto(tag,"Nome "+tag,trophies,best,new PlayerProfileDto.Clan(CLAN))).when(client).getPlayer(tag);
    }
    void roster(Instant at,int trophies) {
        UUID run=UUID.randomUUID();
        sql.update("insert into collection_run(id,clan_tag,kind,status,started_at,completed_at) values (?,?,'MEMBERS','SUCCESS',?,?)",run,CLAN,Timestamp.from(at),Timestamp.from(at));
        sql.update("insert into clan_membership_history(player_id,clan_id,run_id,name,role,trophies,captured_at) select id,?,?,'Nome','member',?,? from player",clan,run,trophies,Timestamp.from(at));
    }
    void war(Instant at,int section) {
        Long id=sql.queryForObject("insert into war(clan_id,season_id,section_index,published_at,captured_at,payload) values (?,140,?,?,?,'{}') returning id",Long.class,clan,section,Timestamp.from(at),Timestamp.from(at));
        sql.update("insert into war_participant(war_id,player_id,name,contribution,decks_used,captured_at) select ?,id,name,1600,16,? from player",id,Timestamp.from(at));
    }
    void advance(int trophies) {
        clock.now=clock.now.plus(Duration.ofDays(7));
        roster(clock.now.minusSeconds(120),trophies);
        war(clock.now.minusSeconds(60),(int)Duration.between(CLOSE,clock.now).toDays()/7);
        profiles(trophies,trophies);
    }
    void event(String type,Instant at) {
        UUID run=UUID.randomUUID();
        sql.update("insert into collection_run(id,clan_tag,kind,status,started_at) values (?,?,'MEMBERS','SUCCESS',?)",run,CLAN,Timestamp.from(at));
        sql.update("insert into membership_event(player_id,clan_id,run_id,type,observed_at) select id,?,?,?,? from player where player_tag='#AAA'",clan,run,type,Timestamp.from(at));
    }
    Candidate a(Result result) { return result.candidates().stream().filter(c->c.tag().equals("#AAA")).findFirst().orElseThrow(); }
    @Test void baselineIdempotenteEImutavelSemPremiosTradicionais() {
        service.collect();service.collect();
        var result=service.result(WEEK);
        assertThat(result.cycle().status()).isEqualTo("COMPLETE");assertThat(result.cycle().winnerTag()).isNull();
        assertThat(a(result).previousRecord()).isNull();verify(client,times(1)).getPlayer("#AAA");
        assertThat(sql.queryForObject("select count(*) from legendary_snapshot",Integer.class)).isEqualTo(2);
        assertThat(sql.queryForObject("select count(*) from weekly_award",Integer.class)).isZero();
        assertThatThrownBy(()->sql.update("update legendary_snapshot set trophies=0")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->sql.update("update legendary_cycle set official=true")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void recordeObservadoIncluiPicoEntreColetasERenomeacaoPreservaTag() {
        roster(CLOSE.minusSeconds(30),11000);profiles(10500,13000);service.collect();
        advance(11200);
        when(client.getPlayer("#AAA")).thenReturn(new PlayerProfileDto("#AAA","Novo nome",11200,13000,new PlayerProfileDto.Clan(CLAN)));
        service.collect();var c=a(service.result(WEEK.plusWeeks(1)));
        assertThat(c.name()).isEqualTo("Novo nome");assertThat(c.previousRecord()).isEqualTo(11000);
        assertThat(c.record()).isEqualTo(11200);assertThat(c.improvement()).isEqualTo(200);assertThat(c.netChange()).isEqualTo(700);
        assertThat(c.apiBestTrophies()).isEqualTo(13000);assertThat(c.eligible()).isTrue();
        assertThat(a(service.result(WEEK)).name()).isEqualTo("Nome #AAA");
    }
    @Test void picosDuranteSemanaNaoSePerdemComDerrotas() {
        service.collect();advance(9200);roster(clock.now.minus(Duration.ofDays(2)),9500);service.collect();
        assertThat(a(service.result(WEEK.plusWeeks(1))).record()).isEqualTo(9500);
        assertThat(a(service.result(WEEK.plusWeeks(1))).improvement()).isEqualTo(500);
    }
    @Test void mesmoJogadorPodeVencerSemanasConsecutivas() {
        service.collect();advance(9200);service.collect();advance(9300);service.collect();
        assertThat(service.result(WEEK.plusWeeks(1)).cycle().winnerTag()).isEqualTo("#AAA");
        assertThat(service.result(WEEK.plusWeeks(2)).cycle().winnerTag()).isEqualTo("#AAA");
    }
    @Test void saidaERetornoInvalidamCicloMesmoComTrofeus() {
        service.collect();advance(9200);event("LEAVE",clock.now.minus(Duration.ofDays(3)));event("JOIN",clock.now.minus(Duration.ofDays(2)));service.collect();
        assertThat(a(service.result(WEEK.plusWeeks(1))).eligible()).isFalse();
        assertThat(service.result(WEEK.plusWeeks(1)).cycle().winnerTag()).isEqualTo("#BBB");
    }
    @Test void liderNaoConcorreEObrigacoesDaGuerraNaoMudam() {
        service.collect();advance(9200);
        sql.update("update clan_membership_history set role='leader' where player_id=(select id from player where player_tag='#AAA')");
        sql.update("update war_participant set decks_used=15 where player_id=(select id from player where player_tag='#BBB')");
        service.collect();assertThat(service.result(WEEK.plusWeeks(1)).cycle().winnerTag()).isNull();
    }
    @Test void falhaParcialRetomaSomentePerfisPendentes() {
        when(client.getPlayer("#BBB")).thenThrow(new ClashRoyaleException(HttpStatus.GATEWAY_TIMEOUT,"UPSTREAM_TIMEOUT","timeout"));
        service.collect();assertThat(service.result(WEEK).cycle().finalizedAt()).isNull();
        profiles(9000,9000);service.collect();verify(client,times(1)).getPlayer("#AAA");verify(client,times(2)).getPlayer("#BBB");
        assertThat(service.result(WEEK).cycle().status()).isEqualTo("COMPLETE");
    }
    @Test void coletaIncompletaNaoDeclaraVencedorNemTransformaAusenciaEmZero() {
        service.collect();advance(9200);
        when(client.getPlayer("#BBB")).thenThrow(new ClashRoyaleException(HttpStatus.GATEWAY_TIMEOUT,"UPSTREAM_TIMEOUT","timeout"));
        service.collect();clock.now=clock.now.plus(Duration.ofHours(3));service.collect();
        var result=service.result(WEEK.plusWeeks(1));
        assertThat(result.cycle().status()).isEqualTo("INCOMPLETE");assertThat(result.cycle().winnerTag()).isNull();
        assertThat(result.candidates().stream().filter(c->c.tag().equals("#BBB")).findFirst().orElseThrow().trophies()).isNull();
    }
    @Test void janelaExpiradaNaoConsultaTrofeusRetroativos() {
        clock.now=clock.now.plus(Duration.ofHours(3));service.collect();verifyNoInteractions(client);
        assertThat(service.result(WEEK).cycle().status()).isEqualTo("MISSED");
    }
    @Test void guerraPublicadaDepoisNaoAdiaCapturaDeTrofeus() {
        sql.execute("truncate war cascade");service.collect();assertThat(service.result(WEEK).cycle().status()).isEqualTo("AWAITING_WAR");
        clock.now=clock.now.plus(Duration.ofHours(4));war(CLOSE.plusSeconds(1800),0);profiles(12000,12000);service.collect();
        assertThat(a(service.result(WEEK)).trophies()).isEqualTo(9000);verify(client,times(1)).getPlayer("#AAA");
    }
    @Test void origemOuEraDiferentesExigemNovaBase() {
        service.collect();advance(9200);
        var officialSource=new LegendaryProperties(true,false,LegendaryProperties.Source.OFFICIAL_API,"new-era",settings.eraStart(),14000,Duration.ofHours(2));
        var other=new LegendaryService(repository,officialSource,api,client,clock,database,transactions,collection);other.collect();
        assertThat(a(other.result(WEEK.plusWeeks(1))).previousRecord()).isNull();
        assertThat(other.result(WEEK.plusWeeks(1)).cycle().winnerTag()).isNull();
    }
    @Test void ativacaoOficialNaoReclassificaHistoricoExperimental() {
        service.collect();advance(9200);
        var official=new LegendaryProperties(true,true,settings.recordSource(),settings.recordEra(),settings.eraStart(),14000,Duration.ofHours(2));
        new LegendaryService(repository,official,api,client,clock,database,transactions,collection).collect();
        assertThat(service.result(WEEK).cycle().official()).isFalse();assertThat(service.result(WEEK.plusWeeks(1)).cycle().official()).isTrue();
        assertThat(sql.queryForObject("select count(*) from weekly_award",Integer.class)).isZero();
    }
    @Test void limiteNovoOuPerfilErradoExigemRevisao() {
        when(client.getPlayer("#AAA")).thenReturn(new PlayerProfileDto("#AAA","Nome",15000,15000,new PlayerProfileDto.Clan(CLAN)));
        when(client.getPlayer("#BBB")).thenReturn(new PlayerProfileDto("#WRONG","Nome",9000,9000,new PlayerProfileDto.Clan(CLAN)));
        service.collect();assertThat(repository.snapshots(CLAN,WEEK)).allMatch(s->s.trophies()==null);
        assertThat(repository.snapshots(CLAN,WEEK)).extracting(Snapshot::errorCode).containsExactly("TROPHY_ROAD_LIMIT_REVIEW_REQUIRED","INVALID_PLAYER_PROFILE");
    }
    @Test void replicasConcorrentesNaoDuplicamColeta() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var tasks=pool.invokeAll(List.of(Executors.callable(service::collect),Executors.callable(service::collect)));
            for(var task:tasks) task.get();
        }
        verify(client,times(1)).getPlayer("#AAA");assertThat(repository.count(CLAN)).isEqualTo(1);
    }
    @Test void recordeOficialValidoUsaBestTrofeusEQuedaNaoGeraNovaBase() {
        var official=new LegendaryProperties(true,false,LegendaryProperties.Source.OFFICIAL_API,"official-test",settings.eraStart(),14000,Duration.ofHours(2));
        var subject=new LegendaryService(repository,official,api,client,clock,database,transactions,collection);
        profiles(10500,11000);subject.collect();advance(11200);subject.collect();
        assertThat(a(subject.result(WEEK.plusWeeks(1))).improvement()).isEqualTo(200);
        advance(10000);subject.collect();
        assertThat(repository.snapshots(CLAN,WEEK.plusWeeks(2))).allMatch(s->"RECORD_ERA_REVIEW_REQUIRED".equals(s.errorCode()));
    }
    @Test void registroOficialSemBestOuAbaixoDosTrofeusERejeitado() {
        var official=new LegendaryProperties(true,false,LegendaryProperties.Source.OFFICIAL_API,"official-test",settings.eraStart(),14000,Duration.ofHours(2));
        when(client.getPlayer("#AAA")).thenReturn(new PlayerProfileDto("#AAA","Nome",9000,null,new PlayerProfileDto.Clan(CLAN)));
        when(client.getPlayer("#BBB")).thenReturn(new PlayerProfileDto("#BBB","Nome",9000,8999,new PlayerProfileDto.Clan(CLAN)));
        new LegendaryService(repository,official,api,client,clock,database,transactions,collection).collect();
        assertThat(repository.snapshots(CLAN,WEEK)).allMatch(s->"INVALID_API_RECORD".equals(s.errorCode()));
    }
    @Test void semanaSemBaseAnteriorNaoPremiaMesmoComHistoricoMaisAntigo() {
        service.collect();advance(9200);clock.now=clock.now.plus(Duration.ofHours(3));service.collect();
        clock.now=clock.now.minus(Duration.ofHours(3));advance(9500);service.collect();
        var c=a(service.result(WEEK.plusWeeks(2)));assertThat(c.previousRecord()).isNull();assertThat(c.eligible()).isFalse();
    }
    @Test void limiteDeSegundaAsSeisHorasUsaSaoPaulo() {
        clock.now=CLOSE.minusSeconds(1);service.collect();verifyNoInteractions(client);
        assertThat(repository.cycle(CLAN,WEEK)).isEmpty();
        clock.now=CLOSE;service.collect();assertThat(repository.cycle(CLAN,WEEK)).isPresent();
    }
    @Test void consultaAutenticadaHistoricoEColetaRestritaAoAdmin() throws Exception {
        mvc.perform(get("/api/legendary-awards")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/legendary-awards").with(user("viewer").roles("VIEWER"))).andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
        mvc.perform(post("/api/legendary-awards/collect").with(user("viewer").roles("VIEWER")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/legendary-awards/collect").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/legendary-awards/collect").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.result.cycle.status").value("COMPLETE"));
        mvc.perform(get("/api/legendary-awards?week=2026-11-30").with(user("viewer").roles("VIEWER"))).andExpect(status().isOk()).andExpect(jsonPath("$.result.candidates[0].previousRecord").isEmpty());
        mvc.perform(get("/api/legendary-awards?page=-1").with(user("viewer").roles("VIEWER"))).andExpect(status().isBadRequest());
    }
}
