package com.frotafantasma.analytics.imports.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.imports.dto.BackfillDtos.*;
import com.frotafantasma.analytics.imports.parser.RoyaleApiCsvParser;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HistoricalBackfillService {
    private final JdbcTemplate sql;
    private final ObjectMapper json;
    private final ClashRoyaleProperties config;
    private final Clock clock;
    public HistoricalBackfillService(JdbcTemplate sql,ObjectMapper json,ClashRoyaleProperties config,Clock clock) {
        this.sql=sql; this.json=json; this.config=config; this.clock=clock;
    }
    private record Context(UUID batchId,long resolutionId,long clanId,String format,String targetKind,
                           Integer season,Integer section,Instant at,JsonNode evidence) {}
    private Context context(UUID batch) {
        var rows=sql.query("""
            select b.format,r.*,c.id as own_clan from import_batch b join clan c on c.tag=b.clan_tag
            join lateral(select * from import_resolution where batch_id=b.id order by revision desc limit 1) r on true
            where b.id=? and b.clan_tag=?
            """,(rs,n)-> {
                if(!rs.getString("status").equals("IDENTIFIED") || !rs.getBoolean("manually_confirmed"))
                    throw error(409,"Somente a resolução IDENTIFIED atual pode ser incorporada.");
                var evidence=tree(rs.getString("evidence"));
                Instant at=rs.getTimestamp("captured_at")==null?null:rs.getTimestamp("captured_at").toInstant();
                String kind=rs.getString("target_kind");
                Integer season=rs.getObject("season_id",Integer.class),section=rs.getObject("section_index",Integer.class);
                if("WAR".equals(kind)) {
                    var candidate=evidence.path("candidate");
                    if(!candidate.path("kind").asText().equals("WAR") || candidate.path("targetId").asLong(-1)!=rs.getLong("target_id")
                            || season==null || section==null || candidate.path("seasonId").asInt(-1)!=season || candidate.path("sectionIndex").asInt(-1)!=section)
                        throw error(409,"A resolução não preserva a identidade oficial da guerra.");
                    try { at=Instant.parse(candidate.path("officialAt").asText()); }
                    catch(DateTimeException e) { throw error(409,"Falta a publicação oficial preservada na resolução."); }
                }
                return new Context(batch,rs.getLong("revision"),rs.getLong("own_clan"),rs.getString("format"),kind,season,section,at,evidence);
            },batch,config.clanTag());
        if(rows.isEmpty()) throw error(409,"Lote sem identificação confirmada para este clã.");
        return rows.getFirst();
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Plan preview(UUID batch) { return plan(context(batch)); }
    private Plan plan(Context c) {
        var previous=previous(c);
        var records=sql.query("select row_number,values from import_row where batch_id=? order by row_number",
                (rs,n)->Map.entry(rs.getInt(1),tree(rs.getString(2))),c.batchId());
        var rows=new ArrayList<Row>(); var seen=new HashSet<String>();
        boolean supported=c.format().equals("MEMBERS") && c.at()!=null || c.format().equals("RIVER") && "WAR".equals(c.targetKind()) && c.at()!=null;
        Long warId=warId(c);
        if(warId!=null) {
            var date=sql.queryForObject("select published_at from war where id=?",Timestamp.class,warId).toInstant();
            if(!date.equals(c.at())) throw error(409,"A publicação oficial mudou desde a resolução. Revise a identificação.");
        }
        for(var entry:records) {
            var raw=entry.getValue(); String tag=RoyaleApiCsvParser.tag(raw.path(c.format().equals("RIVER")?"player_tag":"tag").asText());
            var values=values(c,raw);
            String outcome="NEW", reason="Histórico ausente; backfill com procedência preservada.";
            if(!supported) { outcome="IGNORED"; reason="Snapshot de corrida sem temporada/publicação confirmadas não é uma guerra final; permanece fora das métricas."; }
            else if(tag==null || !seen.add(tag)) { outcome="IGNORED"; reason="Tag ausente ou repetida no lote; não cria identidade artificial."; }
            else if(c.format().equals("RIVER") && !config.clanTag().equals(RoyaleApiCsvParser.tag(raw.path("clan_tag").asText()))) {
                outcome="IGNORED"; reason="O clã da linha difere do clã confirmado.";
            } else {
                JsonNode existing=existing(c,tag,warId);
                if(existing!=null) {
                    var differences=new ArrayList<String>();
                    // Names are descriptive, not identity. Null official values are never filled from CSV.
                    for(var field:fields(c)) if(!Objects.equals(existing.get(field),values.get(field)))
                        differences.add(field+": existente="+existing.get(field)+", CSV="+values.get(field));
                    if(differences.isEmpty()) { outcome="DUPLICATE"; reason="Registro já existente ("+existing.path("source").asText()+"); nenhuma segunda observação criada."; }
                    else { outcome="CONFLICT"; reason="Registro existente preservado ("+existing.path("source").asText()+"). "+String.join("; ",differences); }
                }
            }
            rows.add(new Row(entry.getKey(),tag,outcome,reason,values));
        }
        int created=count(rows,"NEW"),duplicates=count(rows,"DUPLICATE"),conflicts=count(rows,"CONFLICT"),ignored=count(rows,"IGNORED");
        String version=RiverIdentityMatcher.digest("backfill-v1:"+c.resolutionId()+":"+c.at()+":"+encode(rows));
        return new Plan(c.batchId(),c.resolutionId(),rows.size(),created,duplicates,conflicts,ignored,
                (int)rows.stream().filter(r->r.outcome().equals("NEW")).map(Row::playerTag).distinct().count(),supported?c.at():null,supported?c.at():null,version,List.copyOf(rows),
                List.of("Supercell prevalece por identidade. Divergências e desconhecidos oficiais não são preenchidos pelo CSV.",
                        "Amostra e cobertura permanecem limitadas. Importações não comprovam disponibilidade individual de decks ou resultado completo.",
                        "Nenhuma promoção, expulsão ou premiação é executada nesta operação."),previous);
    }
    private int count(List<Row> rows,String outcome) { return (int)rows.stream().filter(r->r.outcome().equals(outcome)).count(); }
    private List<String> fields(Context c) {
        return c.format().equals("RIVER")?List.of("contribution","decks_used","decks_used_today","boat_attacks","repair_points")
                :List.of("role","trophies","clan_rank","previous_clan_rank","donations","donations_received","exp_level","api_last_seen");
    }
    private JsonNode values(Context c,JsonNode raw) {
        var result=json.createObjectNode();
        if(c.format().equals("RIVER")) {
            result.put("name",raw.path("player_name").asText());
            String[] csv={"player_fame","player_decks_used","player_decks_used_today","player_boat_attacks","player_repair_points"};
            int i=0; for(String field:fields(c)) result.set(field,json.valueToTree(integer(raw,csv[i++])));
        } else {
            result.put("name",raw.path("name").asText());
            result.set("role",json.valueToTree(raw.path("role").asText().isEmpty()?null:raw.path("role").asText()));
            String[] csv={"trophies","clanRank","previousClanRank","donations","donationsReceived","expLevel"};
            String[] db={"trophies","clan_rank","previous_clan_rank","donations","donations_received","exp_level"};
            for(int i=0;i<csv.length;i++) result.set(db[i],json.valueToTree(integer(raw,csv[i])));
            String lastSeen=raw.path("lastSeen").asText();
            result.set("api_last_seen",json.valueToTree(lastSeen.isEmpty()?null:Instant.from(DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss.SSSX").parse(lastSeen)).toString()));
        }
        return result;
    }
    private Integer integer(JsonNode node,String field) { String value=node.path(field).asText(); return value.isEmpty()?null:Integer.valueOf(value); }
    private Long warId(Context c) {
        if(!"WAR".equals(c.targetKind())) return null;
        var found=sql.queryForList("select id from war where clan_id=? and season_id=? and section_index=?",Long.class,c.clanId(),c.season(),c.section());
        return found.isEmpty()?null:found.getFirst();
    }
    private JsonNode existing(Context c,String tag,Long war) {
        String query=c.format().equals("RIVER")
                ? "select to_jsonb(h)::text from effective_war_participant h join player p on p.id=h.player_id where h.war_id=? and p.player_tag=?"
                : "select to_jsonb(h)::text from effective_membership_history h join player p on p.id=h.player_id where h.clan_id=? and p.player_tag=? and h.captured_at=?";
        if(c.format().equals("RIVER") && war==null) return null;
        var list=c.format().equals("RIVER")?sql.queryForList(query,String.class,war,tag):sql.queryForList(query,String.class,c.clanId(),tag,Timestamp.from(c.at()));
        if(list.isEmpty()) return null;
        var result=(com.fasterxml.jackson.databind.node.ObjectNode)tree(list.getFirst());
        if(!result.path("api_last_seen").isMissingNode() && !result.path("api_last_seen").isNull())
            result.put("api_last_seen",OffsetDateTime.parse(result.path("api_last_seen").asText()).toInstant().toString());
        return result;
    }
    @Transactional
    public Result incorporate(UUID batch,Confirm request,String actor) {
        if(!request.confirmed()) throw error(400,"Confirme explicitamente a incorporação.");
        if(!Boolean.TRUE.equals(sql.queryForObject("select pg_try_advisory_xact_lock(hashtextextended(?,0))",Boolean.class,"frota:collection:"+config.clanTag())))
            throw error(409,"Uma coleta/incorporação está em andamento neste clã. Tente novamente após sua conclusão.");
        sql.queryForList("select id from import_batch where id=? for update",UUID.class,batch);
        Context c=context(batch);
        if(c.resolutionId()!=request.resolutionId()) throw error(409,"A resolução mudou. Analise a incorporação novamente.");
        var previous=previous(c); if(previous!=null) return previous;
        var plan=plan(c);
        if(!plan.evidenceVersion().equals(request.evidenceVersion())) throw error(409,"O histórico mudou desde a prévia. Analise novamente.");
        Instant now=clock.instant(); UUID id=UUID.randomUUID();
        var result=new Result(id,batch,c.resolutionId(),now,actor,plan.rowsConflicted()>0 || plan.rowsIgnored()>0?"PARTIAL":"INCORPORATED",
                plan.rowsNew(),plan.rowsDuplicated(),plan.rowsConflicted(),plan.rowsIgnored(),plan.rows());
        sql.update("insert into import_application(id,batch_id,resolution_id,applied_at,applied_by,result) values(?,?,?,?,?,?::jsonb)",id,batch,c.resolutionId(),Timestamp.from(now),actor,encode(result));
        Long war=plan.rowsNew()>0 && c.format().equals("RIVER")?ensureWar(c,now):null;
        for(var row:plan.rows()) {
            sql.update("insert into import_application_row(application_id,row_number,outcome,evidence) values(?,?,?,?::jsonb)",id,row.line(),row.outcome(),encode(row));
            if(!row.outcome().equals("NEW")) continue;
            long player=ensurePlayer(row);
            var v=row.values();
            if(c.format().equals("RIVER")) {
                sql.update("""
                    insert into war_participant(war_id,player_id,name,contribution,decks_used,decks_used_today,boat_attacks,repair_points,captured_at,source,batch_id,resolution_id,imported_at)
                    values(?,?,?,?,?,?,?,?,?,'ROYALEAPI_IMPORT',?,?,?)
                    on conflict(war_id,player_id) do update set name=excluded.name,contribution=excluded.contribution,decks_used=excluded.decks_used,
                      decks_used_today=excluded.decks_used_today,boat_attacks=excluded.boat_attacks,repair_points=excluded.repair_points,captured_at=excluded.captured_at,
                      batch_id=excluded.batch_id,resolution_id=excluded.resolution_id,imported_at=excluded.imported_at
                    where war_participant.source='ROYALEAPI_IMPORT'
                    """,war,player,text(v,"name"),number(v,"contribution"),number(v,"decks_used"),number(v,"decks_used_today"),number(v,"boat_attacks"),number(v,"repair_points"),Timestamp.from(now),batch,c.resolutionId(),Timestamp.from(now));
            } else sql.update("""
                insert into clan_membership_history(player_id,clan_id,name,role,trophies,clan_rank,previous_clan_rank,donations,donations_received,exp_level,captured_at,api_last_seen,source,batch_id,resolution_id,imported_at)
                values(?,?,?,?,?,?,?,?,?,?,?,?,'ROYALEAPI_IMPORT',?,?,?)
                """,player,c.clanId(),text(v,"name"),text(v,"role"),number(v,"trophies"),number(v,"clan_rank"),number(v,"previous_clan_rank"),number(v,"donations"),number(v,"donations_received"),number(v,"exp_level"),Timestamp.from(c.at()),
                    text(v,"api_last_seen")==null?null:Timestamp.from(Instant.parse(text(v,"api_last_seen"))),batch,c.resolutionId(),Timestamp.from(now));
        }
        return result;
    }
    private long ensurePlayer(Row row) {
        sql.update("insert into player(player_tag,name,active) values(?,?,false) on conflict(player_tag) do nothing",row.playerTag(),text(row.values(),"name"));
        return sql.queryForObject("select id from player where player_tag=?",Long.class,row.playerTag());
    }
    private long ensureWar(Context c,Instant now) {
        sql.update("""
            insert into war(clan_id,season_id,section_index,published_at,captured_at,payload,source,batch_id,resolution_id,imported_at)
            values(?,?,?,?,?,?::jsonb,'ROYALEAPI_IMPORT',?,?,?) on conflict(clan_id,season_id,section_index) do nothing
            """,c.clanId(),c.season(),c.section(),Timestamp.from(c.at()),Timestamp.from(now),encode(c.evidence()),c.batchId(),c.resolutionId(),Timestamp.from(now));
        long id=warId(c);
        // A corrected resolution can restore a previously withdrawn imported context; official context is never changed.
        sql.update("update war set batch_id=?,resolution_id=?,imported_at=? where id=? and source='ROYALEAPI_IMPORT' and not exists(select 1 from active_import_resolution r where r.revision=war.resolution_id)",c.batchId(),c.resolutionId(),Timestamp.from(now),id);
        return id;
    }
    private Result previous(Context c) {
        var result=sql.queryForList("select result::text from import_application where batch_id=? and resolution_id=?",String.class,c.batchId(),c.resolutionId());
        if(result.isEmpty()) return null;
        try { return json.readValue(result.getFirst(),Result.class); } catch(JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    public Summary summary(UUID batch) {
        var results=sql.queryForList("select result::text from import_application where batch_id=? order by applied_at desc,id desc limit 1",String.class,batch);
        if(results.isEmpty()) return null;
        try {
            Result result=json.readValue(results.getFirst(),Result.class);
            long count=sql.queryForObject("select (select count(*) from effective_war_participant where batch_id=?) + (select count(*) from effective_membership_history where batch_id=?)",Long.class,batch,batch);
            return new Summary(result.status(),result.rowsNew(),result.rowsDuplicated(),result.rowsConflicted(),result.rowsIgnored(),count>0,count);
        } catch(JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private Object number(JsonNode v,String field) { return v.path(field).isNull()?null:v.path(field).intValue(); }
    private String text(JsonNode v,String field) { return v.path(field).isNull()?null:v.path(field).asText(); }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(JsonProcessingException e) { throw new IllegalStateException(e); } }
    private JsonNode tree(String value) { try { return json.readTree(value); } catch(JsonProcessingException e) { throw new IllegalStateException(e); } }
    private ResponseStatusException error(int status,String reason) { return new ResponseStatusException(HttpStatus.valueOf(status),reason); }
}
