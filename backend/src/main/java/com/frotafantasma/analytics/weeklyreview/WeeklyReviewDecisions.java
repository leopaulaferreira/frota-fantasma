package com.frotafantasma.analytics.weeklyreview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import jakarta.validation.constraints.*;
import java.time.*;
import java.sql.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WeeklyReviewDecisions {
    public enum Action { KEEP, TALK, DISMISS, WAIT, PENDING }
    public record Create(@NotNull LocalDate week, @NotNull Action action,
        @NotBlank @Size(max=2000) String reason, @NotNull UUID requestKey,
        @NotNull @Min(0) Long expectedRevision) {}
    public record Decision(long revision, String playerTag, LocalDate week, Action action,
        String reason, Instant createdAt, String createdBy) {}
    private record Evidence(WeeklyReviewEngine.Row member, Instant cutoff, Instant membersCapturedAt, Instant riverCapturedAt) {}
    private final JdbcTemplate sql;
    private final ClashRoyaleProperties api;
    private final WeeklyReviewService review;
    private final Clock clock;
    private final ObjectMapper json;
    public WeeklyReviewDecisions(JdbcTemplate sql, ClashRoyaleProperties api, WeeklyReviewService review, Clock clock, ObjectMapper json) {
        this.sql=sql; this.api=api; this.review=review; this.clock=clock; this.json=json;
    }
    private Decision map(ResultSet r,int i) throws SQLException {
        return new Decision(r.getLong("revision"),r.getString("player_tag"),r.getObject("week",LocalDate.class),
            Action.valueOf(r.getString("action")),r.getString("reason"),r.getTimestamp("created_at").toInstant(),r.getString("created_by"));
    }
    private void validateWeek(LocalDate week) {
        if(week.getDayOfWeek()!=DayOfWeek.MONDAY || week.isAfter(WeeklyReviewEngine.monday(clock.instant())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecione uma segunda-feira atual ou passada.");
    }
    public List<Decision> history(LocalDate week) {
        validateWeek(week);
        return sql.query("select * from weekly_review_decision where clan_tag=? and week=? order by revision desc",this::map,api.clanTag(),week);
    }
    @Transactional
    public Decision create(String tag,Create request,String author) {
        validateWeek(request.week());
        sql.query("select pg_advisory_xact_lock(hashtextextended(?,0))",r->{},"frota:weekly-decisions:"+api.clanTag());
        var previous=sql.query("select * from weekly_review_decision where clan_tag=? and request_key=?",this::map,api.clanTag(),request.requestKey());
        if(!previous.isEmpty()) {
            var d=previous.getFirst();
            Long expected=sql.queryForObject("select expected_revision from weekly_review_decision where revision=?",Long.class,d.revision());
            if(!d.playerTag().equals(tag)||!d.week().equals(request.week())||d.action()!=request.action()||!d.reason().equals(request.reason().strip())||!d.createdBy().equals(author)||!Objects.equals(expected,request.expectedRevision()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Identificador já utilizado para outra decisão.");
            return d;
        }
        long latest=sql.queryForObject("select coalesce(max(revision),0) from weekly_review_decision where clan_tag=? and week=? and player_tag=?",Long.class,api.clanTag(),request.week(),tag);
        if(latest!=request.expectedRevision())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A decisão foi alterada por outra sessão. Recarregue as decisões antes de salvar.");
        var report=review.report(request.week());
        var member=report.members().stream().filter(m->m.tag().equals(tag)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Membro não encontrado no recorte desta semana."));
        String evidence;
        try { evidence=json.writeValueAsString(new Evidence(member,report.cutoff(),report.membersCapturedAt(),report.riverCapturedAt())); }
        catch(Exception e) { throw new IllegalStateException("Não foi possível preservar as evidências da decisão.",e); }
        return sql.query("""
            insert into weekly_review_decision(clan_tag,player_tag,week,action,reason,created_at,created_by,request_key,expected_revision,evidence)
            values(?,?,?,?,?,?,?,?,?,?::jsonb) returning *
            """,this::map,api.clanTag(),tag,request.week(),request.action().name(),request.reason().strip(),Timestamp.from(clock.instant()),author,request.requestKey(),request.expectedRevision(),evidence).getFirst();
    }
}
