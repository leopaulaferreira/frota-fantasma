package com.frotafantasma.analytics;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frotafantasma.analytics.client.ClashRoyaleException;
import com.frotafantasma.analytics.dto.clash.*;
import com.frotafantasma.analytics.entity.CollectionRun;
import com.frotafantasma.analytics.repository.CollectionRunRepository;
import com.frotafantasma.analytics.service.HistoryStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"collection.enabled=false", "clash-royale.api-token=test-token"})
@Testcontainers
class HistoryStoreIT {
    @Container static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", db::getJdbcUrl);
        p.add("spring.datasource.username", db::getUsername);
        p.add("spring.datasource.password", db::getPassword);
    }
    @Autowired HistoryStore store;
    @Autowired CollectionRunRepository runs;
    @Autowired JdbcTemplate sql;
    @Autowired ObjectMapper json;
    final Instant day1 = Instant.parse("2026-10-01T12:00:00Z");
    final Instant day2 = day1.plusSeconds(86400);

    @BeforeEach void clear() {
        sql.execute("TRUNCATE clan, player, collection_run CASCADE");
    }
    UUID run() {
        UUID id = UUID.randomUUID();
        runs.save(new CollectionRun(id, "#RQJGC9GV", "TEST", "RUNNING", day1, null, null, null, null));
        return id;
    }
    ClanApiDto clan(int size) { return new ClanApiDto("#RQJGC9GV", "Frota", "desc", "open", 100, 3000, 0, 198, size); }
    ClanMemberApiDto member(String tag, String role) { return new ClanMemberApiDto(tag, "Nome", role, 15, 9000, 1, 1, 198, 0); }

    @Test void preservaSnapshotsEDetectaSaidaEntradaECargo() {
        store.saveMembers(run(), "#RQJGC9GV", clan(2), List.of(member("#P0Y", "member"), member("#L0Y", "member")), day1);
        store.saveMembers(run(), "#RQJGC9GV", clan(2), List.of(member("#P0Y", "elder"), member("#Q0Y", "member")), day2);
        assertThat(sql.queryForObject("select count(*) from clan_membership_history", Integer.class)).isEqualTo(4);
        assertThat(sql.queryForList("select type from membership_event order by id", String.class))
                .containsExactly("BASELINE", "BASELINE", "ROLE_CHANGE", "JOIN", "LEAVE");
        assertThat(sql.queryForObject("select active from player where player_tag='#L0Y'", Boolean.class)).isFalse();
        assertThat(sql.queryForObject("select last_seen_at from player where player_tag='#L0Y'", java.sql.Timestamp.class).toInstant()).isEqualTo(day1);
        assertThat(sql.queryForObject("select role from clan_membership_history where player_id=(select id from player where player_tag='#P0Y') order by id limit 1", String.class)).isEqualTo("member");
        store.saveMembers(run(), "#RQJGC9GV", clan(2), List.of(member("#P0Y", "elder"), member("#L0Y", "member")), day2.plusSeconds(1));
        assertThat(sql.queryForObject("select first_seen_at from player where player_tag='#L0Y'", java.sql.Timestamp.class).toInstant()).isEqualTo(day1);
        assertThat(sql.queryForObject("select count(*) from membership_event where type='JOIN'", Integer.class)).isEqualTo(2);
    }

    @Test void respostaParcialOuDuplicadaNaoRemoveNinguem() {
        store.saveMembers(run(), "#RQJGC9GV", clan(1), List.of(member("#P0Y", "member")), day1);
        assertThatThrownBy(() -> store.saveMembers(run(), "#RQJGC9GV", clan(2), List.of(member("#P0Y", "member")), day2))
                .isInstanceOf(ClashRoyaleException.class);
        assertThatThrownBy(() -> store.saveMembers(run(), "#RQJGC9GV", clan(2), List.of(member("#P0Y", "member"), member("#P0Y", "member")), day2))
                .isInstanceOf(ClashRoyaleException.class);
        assertThat(sql.queryForObject("select count(*) from clan_history", Integer.class)).isEqualTo(1);
        assertThat(sql.queryForObject("select active from player", Boolean.class)).isTrue();
    }

    @Test void importacaoDeGuerrasEIdempotenteENaoCriaAssociacaoFalsa() throws Exception {
        var entry = json.readValue("""
            {"seasonId":136,"sectionIndex":3,"createdDate":"20261005T093804.000Z",
             "standings":[{"rank":1,"trophyChange":100,"clan":{"tag":"#RQJGC9GV","name":"Frota",
             "fame":1600,"clanScore":2890,"finishTime":"19691231T235959.000Z",
             "participants":[{"tag":"#P0Y","name":"Fenix","fame":1600,"decksUsed":12}]}}]}
            """, RiverLogEntryDto.class);
        assertThat(store.saveWarLog("#RQJGC9GV", List.of(entry), day1)).isEqualTo(1);
        assertThat(store.saveWarLog("#RQJGC9GV", List.of(entry), day2)).isZero();
        assertThat(sql.queryForObject("select count(*) from war", Integer.class)).isEqualTo(1);
        assertThat(sql.queryForObject("select count(*) from war_participant", Integer.class)).isEqualTo(1);
        assertThat(sql.queryForObject("select active from player", Boolean.class)).isFalse();
        assertThat(sql.queryForObject("select first_seen_at from player", java.sql.Timestamp.class)).isNull();
        assertThat(sql.queryForObject("select end_date from war", java.sql.Timestamp.class)).isNull();
        assertThat(sql.queryForObject("select battles_played from war_participant", Integer.class)).isNull();
    }

    @Test void mantemObservacoesDaCorridaSemInventarTemporada() throws Exception {
        var race = json.readValue("""
            {"sectionIndex":0,"periodIndex":6,"periodType":"warDay","state":"full",
             "clan":{"tag":"#RQJGC9GV","participants":[{"tag":"#P0Y","fame":1600,"decksUsed":12}]}}
            """, RiverRaceDto.class);
        store.saveRiver(run(), "#RQJGC9GV", race, day1);
        store.saveRiver(run(), "#RQJGC9GV", race, day2);
        assertThat(sql.queryForObject("select count(*) from river_race_snapshot", Integer.class)).isEqualTo(2);
        assertThat(sql.queryForObject("select count(*) from war", Integer.class)).isZero();
    }

    @Test void migracaoPreservaRetratoDaFase1() {
        String schema = "migration_test";
        Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .schemas(schema).defaultSchema(schema).target("1").load().migrate();
        sql.execute("insert into migration_test.clan_snapshot(tag,name,members,observed_at) values ('#RQJGC9GV','Frota',1,'2026-10-01T12:00:00Z')");
        sql.execute("insert into migration_test.clan_member_snapshot(tag,clan_tag,name,role,exp_level,donations,observed_at) values ('#P0Y','#RQJGC9GV','Fenix','elder',15,198,'2026-10-01T12:00:00Z')");
        Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();
        assertThat(sql.queryForObject("select donations from migration_test.clan_membership_history", Integer.class)).isEqualTo(198);
        assertThat(sql.queryForObject("select exp_level from migration_test.clan_membership_history", Integer.class)).isEqualTo(15);
        assertThat(sql.queryForObject("select type from migration_test.membership_event", String.class)).isEqualTo("BASELINE");
        assertThat(sql.queryForObject("select active from migration_test.player", Boolean.class)).isTrue();
        assertThat(sql.queryForObject("select api_last_seen from migration_test.clan_membership_history", java.sql.Timestamp.class)).isNull();
    }
}
