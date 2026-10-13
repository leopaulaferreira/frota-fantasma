package com.frotafantasma.analytics.recommendation;

import static com.frotafantasma.analytics.recommendation.RecommendationDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationRepository {
    private final JdbcTemplate sql;
    private final ObjectMapper json;
    public RecommendationRepository(JdbcTemplate sql, ObjectMapper json) { this.sql = sql; this.json = json; }
    public void lock(String clan) {
        sql.query("select pg_advisory_xact_lock(hashtextextended(?,0))", r -> { }, "frota:recommendations:" + clan);
    }
    private JsonNode tree(String raw) {
        try { return json.readTree(raw); } catch (JsonProcessingException e) { throw new IllegalStateException("Invalid persisted recommendation", e); }
    }
    public String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize recommendation", e); }
    }
    private RuleVersion rule(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new RuleVersion(r.getObject("id", UUID.class), r.getInt("revision"), r.getTimestamp("created_at").toInstant(),
                r.getString("reason"), json.convertValue(tree(r.getString("settings")), Settings.class));
    }
    public Optional<RuleVersion> active(String clan) {
        return sql.query("select v.* from recommendation_rule_version v join recommendation_rule_state s on s.active_version=v.id where s.clan_tag=?", this::rule, clan).stream().findFirst();
    }
    public List<RuleVersion> rules(String clan, int page, int size) {
        return sql.query("select * from recommendation_rule_version where clan_tag=? order by revision desc limit ? offset ?", this::rule, clan, size, (long)page*size);
    }
    public long countRules(String clan) { return sql.queryForObject("select count(*) from recommendation_rule_version where clan_tag=?", Long.class, clan); }
    public void insertRule(String clan, RuleVersion rule) {
        sql.update("insert into recommendation_rule_version values (?,?,?,?,?,?::jsonb)", rule.id(), clan, rule.revision(), Timestamp.from(rule.createdAt()), rule.reason(), encode(rule.settings()));
        sql.update("insert into recommendation_rule_state values (?,?) on conflict(clan_tag) do update set active_version=excluded.active_version", clan, rule.id());
    }
    private Evaluation evaluation(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Evaluation(r.getObject("id", UUID.class), r.getObject("week", LocalDate.class), r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("analysis_cutoff").toInstant(), r.getObject("rule_version_id", UUID.class), r.getString("formula_version"), tree(r.getString("report")));
    }
    public Optional<Evaluation> evaluation(String clan, UUID id) {
        return sql.query("select * from recommendation_evaluation where clan_tag=? and id=?", this::evaluation, clan, id).stream().findFirst();
    }
    public Optional<Evaluation> byKey(String clan, String key) {
        return sql.query("select * from recommendation_evaluation where clan_tag=? and idempotency_key=?", this::evaluation, clan, key).stream().findFirst();
    }
    public List<EvaluationSummary> evaluations(String clan, int page, int size) {
        return sql.query("select id,week,created_at,rule_version_id,formula_version from recommendation_evaluation where clan_tag=? order by created_at desc,id desc limit ? offset ?",
                (r,n) -> new EvaluationSummary(r.getObject(1,UUID.class),r.getObject(2,LocalDate.class),r.getTimestamp(3).toInstant(),r.getObject(4,UUID.class),r.getString(5)), clan,size,(long)page*size);
    }
    public long countEvaluations(String clan) { return sql.queryForObject("select count(*) from recommendation_evaluation where clan_tag=?", Long.class, clan); }
    public void insertEvaluation(String clan, String key, Evaluation e) {
        sql.update("insert into recommendation_evaluation values (?,?,?,?,?,?,?,?,?::jsonb)", e.id(),clan,e.week(),Timestamp.from(e.createdAt()),Timestamp.from(e.analysisCutoff()),e.ruleVersionId(),e.formulaVersion(),key,encode(e.report()));
    }
    public void insertStatus(UUID evaluation, String tag, String status, String color, Object evidence) {
        sql.update("insert into member_status values (?,?,?,?,?::jsonb)",evaluation,tag,status,color,encode(evidence));
    }
    public List<Alert> alerts(String clan, Instant from, Instant to) {
        return sql.query("""
                select e.id,m.player_tag,m.status,e.analysis_cutoff from member_status m join recommendation_evaluation e on e.id=m.evaluation_id
                where e.clan_tag=? and e.analysis_cutoff>=? and e.analysis_cutoff<=? and m.status in ('OBSERVAR','REVISAR')
                order by e.analysis_cutoff desc,e.id,m.player_tag
                """, (r,n) -> new Alert(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getTimestamp(4).toInstant()),clan,Timestamp.from(from),Timestamp.from(to));
    }
    public List<MemberStatus> statuses(String clan, String tag, int page, int size) {
        return sql.query("""
                select e.id,e.week,e.created_at,e.analysis_cutoff,m.player_tag,m.status,m.color,m.evidence
                from member_status m join recommendation_evaluation e on e.id=m.evaluation_id
                where e.clan_tag=? and m.player_tag=? order by e.created_at desc,e.id desc limit ? offset ?
                """,(r,n)->new MemberStatus(r.getObject(1,UUID.class),r.getObject(2,LocalDate.class),r.getTimestamp(3).toInstant(),r.getTimestamp(4).toInstant(),r.getString(5),r.getString(6),r.getString(7),tree(r.getString(8))),clan,tag,size,(long)page*size);
    }
    public long countStatuses(String clan, String tag) {
        return sql.queryForObject("select count(*) from member_status m join recommendation_evaluation e on e.id=m.evaluation_id where e.clan_tag=? and m.player_tag=?",Long.class,clan,tag);
    }
    public Optional<AwardBatch> awards(String clan, LocalDate week) {
        var batches = sql.query("select evaluation_id,finalized_at,timezone from weekly_award_batch where clan_tag=? and week=?",
                (r,n)->new AwardBatch(week,r.getObject(1,UUID.class),r.getTimestamp(2).toInstant(),r.getString(3),List.of()),clan,week);
        if (batches.isEmpty()) return Optional.empty();
        var b=batches.getFirst();
        var awards=sql.query("select title,player_tag,evidence from weekly_award where clan_tag=? and week=? order by title",
                (r,n)->new FinalAward(r.getString(1),r.getString(2),tree(r.getString(3))),clan,week);
        return Optional.of(new AwardBatch(week,b.evaluationId(),b.finalizedAt(),b.timezone(),awards));
    }
    public Page<ReceivedAward> receivedAwards(String clan, String tag, int page, int size) {
        String filter = " where a.clan_tag=?" + (tag == null ? "" : " and a.player_tag=?");
        var args = new java.util.ArrayList<Object>();
        args.add(clan);
        if (tag != null) args.add(tag);
        long total = sql.queryForObject("select count(*) from weekly_award a" + filter, Long.class, args.toArray());
        args.add(size); args.add((long) page * size);
        var items = sql.query("""
                select a.week,b.evaluation_id,b.finalized_at,a.title,a.player_tag,a.evidence
                from weekly_award a join weekly_award_batch b on b.clan_tag=a.clan_tag and b.week=a.week
                """ + filter + " order by a.week desc,a.title,a.player_tag limit ? offset ?",
                (r,n) -> new ReceivedAward(r.getObject(1,LocalDate.class),r.getObject(2,UUID.class),
                        r.getTimestamp(3).toInstant(),r.getString(4),r.getString(5),tree(r.getString(6))),args.toArray());
        return new Page<>(items,page,size,total);
    }
    public void insertAwards(String clan, AwardBatch batch) {
        sql.update("insert into weekly_award_batch values (?,?,?,?,?)",clan,batch.week(),batch.evaluationId(),Timestamp.from(batch.finalizedAt()),batch.timezone());
        for (var award:batch.awards()) sql.update("insert into weekly_award values (?,?,?,?,?::jsonb)",clan,batch.week(),award.title(),award.playerTag(),encode(award.evidence()));
    }
}
