package com.frotafantasma.analytics.weeklyreview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.config.CollectionProperties;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static com.frotafantasma.analytics.weeklyreview.WeeklyReviewEngine.*;

@Service
public class WeeklyReviewService {
    private final JdbcTemplate sql;
    private final ObjectMapper json;
    private final ClashRoyaleProperties api;
    private final CollectionProperties collections;
    private final Clock clock;
    private record Roster(UUID run, Instant capturedAt, Integer count) {}
    public WeeklyReviewService(JdbcTemplate sql, ObjectMapper json, ClashRoyaleProperties api, CollectionProperties collections, Clock clock) {
        this.sql = sql; this.json = json; this.api = api; this.collections = collections; this.clock = clock;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report report(LocalDate requestedWeek) {
        Instant now = clock.instant();
        LocalDate week = requestedWeek == null ? defaultWeek(now) : requestedWeek;
        if (week.getDayOfWeek() != DayOfWeek.MONDAY || week.isAfter(monday(now)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione a segunda-feira de uma semana atual ou passada.");
        Instant cutoff = cutoff(week, now);
        var roster = sql.query("""
                select h.run_id,h.captured_at,h.member_count from clan_history h join clan c on c.id=h.clan_id
                where c.tag=? and h.captured_at<=? order by h.captured_at desc,h.id desc limit 1
                """, (r,n) -> new Roster(r.getObject(1,UUID.class),r.getTimestamp(2).toInstant(),r.getObject(3,Integer.class)),
                api.clanTag(), Timestamp.from(cutoff)).stream().findFirst().orElse(null);
        List<Member> members = roster == null ? List.of() : sql.query("""
                select p.player_tag,h.name,h.role,h.api_last_seen,
                    (select e.observed_at from membership_event e where e.player_id=h.player_id and e.clan_id=h.clan_id
                     and e.type in ('JOIN','BASELINE') and e.observed_at<=h.captured_at order by e.observed_at desc,e.id desc limit 1)
                from clan_membership_history h join player p on p.id=h.player_id join clan c on c.id=h.clan_id
                where c.tag=? and h.run_id=? order by p.player_tag
                """, (r,n) -> new Member(r.getString(1),r.getString(2),r.getString(3),instant(r.getTimestamp(4)),instant(r.getTimestamp(5))), api.clanTag(),roster.run());
        var race = sql.query("""
                select s.captured_at,s.id,s.period_type,s.payload from river_race_snapshot s join clan c on c.id=s.clan_id
                where c.tag=? and s.captured_at<=? order by s.captured_at desc,s.id desc limit 1
                """, (r,n) -> {
                    try { return new Race(r.getTimestamp(1).toInstant(),r.getLong(2),r.getString(3),json.readTree(r.getString(4))); }
                    catch (JsonProcessingException e) { throw new IllegalStateException("Registro de corrida inválido.",e); }
                },api.clanTag(),Timestamp.from(cutoff)).stream().findFirst().orElse(null);
        return analyze(week,now,collections.enabled(),roster == null ? null : roster.capturedAt(),roster == null ? null : roster.count(),members,race);
    }
    private static Instant instant(Timestamp t) { return t == null ? null : t.toInstant(); }
}
