package com.frotafantasma.analytics.imports.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.imports.dto.IdentificationDtos.*;
import com.frotafantasma.analytics.imports.parser.RoyaleApiCsvParser;
import com.frotafantasma.analytics.recommendation.RecommendationRulesService;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImportIdentificationService {
    private final JdbcTemplate sql;
    private final ObjectMapper json;
    private final ClashRoyaleProperties config;
    private final RecommendationRulesService rules;
    private final RiverIdentityMatcher matcher;
    private final Clock clock;
    public ImportIdentificationService(JdbcTemplate sql, ObjectMapper json, ClashRoyaleProperties config,
            RecommendationRulesService rules, RiverIdentityMatcher matcher, Clock clock) {
        this.sql=sql; this.json=json; this.config=config; this.rules=rules; this.matcher=matcher; this.clock=clock;
    }
    private record Batch(String filename, String format, String checksum) {}
    private Batch batch(UUID id) {
        var rows=sql.query("select filename,format,checksum from import_batch where id=? and clan_tag=?",
                (rs,n)->new Batch(rs.getString(1),rs.getString(2),rs.getString(3)),id,config.clanTag());
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Lote não encontrado no clã configurado.");
        return rows.getFirst();
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Identification identify(UUID id) { return analyze(id); }
    private Identification analyze(UUID id) {
        var batch=batch(id);
        var rows=sql.query("select values from import_row where batch_id=? order by row_number",(rs,n)->tree(rs.getString(1)),id);
        String clanName=sql.query("select name from clan where tag=?",(rs,n)->rs.getString(1),config.clanTag()).stream()
                .filter(Objects::nonNull).findFirst().orElse("Clã configurado");
        String match="UNRESOLVED", fingerprint=null;
        List<Candidate> candidates=List.of(); List<String> warnings;
        LocalDateTime suggestion=null;
        if(batch.format().equals("RIVER")) {
            boolean ownClan=rows.stream().allMatch(r->config.clanTag().equals(RoyaleApiCsvParser.tag(r.path("clan_tag").asText())));
            if(!ownClan) warnings=List.of("O lote contém outro clã. Nenhuma associação é permitida ao clã configurado.");
            else {
                var participants=rows.stream().map(r->new Participant(r.path("player_tag").asText(), number(r,"player_fame"),
                        number(r,"player_decks_used"),number(r,"player_boat_attacks"),number(r,"player_repair_points"))).toList();
                var result=matcher.match(config.clanTag(),participants,targets());
                match=result.match(); fingerprint=result.fingerprint(); candidates=result.candidates(); warnings=result.warnings();
            }
        } else {
            suggestion=ImportFilenameDate.suggest(batch.filename(),config.clanTag());
            warnings=List.of("Clã e data precisam de confirmação explícita. O nome do arquivo oferece apenas uma sugestão sem fuso.",
                    "lastSeen e data de upload não são usados como data da captura.",
                    "Identificação não cria membros, eventos, doações nem alimenta analytics.");
        }
        String zone=rules.current().settings().analytics().timezone().getId();
        var base=new Identification(id,batch.format(),batch.filename(),config.clanTag(),clanName,match,fingerprint,candidates,warnings,suggestion,zone,null,List.of());
        String version=RiverIdentityMatcher.digest("river-identity-v1:"+batch.checksum()+encode(base));
        return new Identification(id,base.format(),base.filename(),base.clanTag(),base.clanName(),match,fingerprint,candidates,warnings,suggestion,zone,version,history(id));
    }
    private List<Target> targets() {
        var targets=new ArrayList<Target>();
        targets.addAll(sql.query("""
            select w.*, coalesce((select jsonb_agg(jsonb_build_object('tag',p.player_tag,'fame',wp.contribution,
              'decksUsed',wp.decks_used,'boatAttacks',wp.boat_attacks,'repairPoints',wp.repair_points))
              from war_participant wp join player p on p.id=wp.player_id where wp.war_id=w.id),'[]'::jsonb) as participants
            from war w join clan c on c.id=w.clan_id where c.tag=? order by w.id
            """,(rs,n)->new Target("WAR",rs.getLong("id"),rs.getObject("season_id",Integer.class),rs.getObject("section_index",Integer.class),
                    null,null,rs.getTimestamp("published_at").toInstant(),config.clanTag(),participants(tree(rs.getString("participants")))),config.clanTag()));
        targets.addAll(sql.query("""
            select r.* from river_race_snapshot r join clan c on c.id=r.clan_id where c.tag=? order by r.id
            """,(rs,n)-> {
                var payload=tree(rs.getString("payload")).path("clan");
                return new Target("SNAPSHOT",rs.getLong("id"),null,rs.getObject("section_index",Integer.class),rs.getObject("period_index",Integer.class),
                        rs.getString("period_type"),rs.getTimestamp("captured_at").toInstant(),payload.path("tag").asText(),participants(payload.path("participants")));
            },config.clanTag()));
        return targets;
    }
    private List<Participant> participants(JsonNode array) {
        var rows=new ArrayList<Participant>();
        if(array.isArray()) for(var row:array) rows.add(new Participant(row.path("tag").asText(),number(row,"fame"),number(row,"decksUsed"),number(row,"boatAttacks"),number(row,"repairPoints")));
        return rows;
    }
    private Integer number(JsonNode row,String field) {
        var value=row.get(field);
        if(value==null || value.isNull() || value.asText().isEmpty()) return null;
        try { return Integer.valueOf(value.asText()); } catch(NumberFormatException e) { return null; }
    }
    @Transactional
    public Resolution resolve(UUID id, Resolve request, String actor) {
        batch(id);
        sql.queryForObject("select id from import_batch where id=? for update",UUID.class,id);
        var replay=sql.query("select * from import_resolution where batch_id=? and request_key=?",(rs,n)-> {
            if(!actor.equals(rs.getString("resolved_by")) || !tree(rs.getString("request")).equals(tree(encode(request))))
                throw error(HttpStatus.CONFLICT,"Chave de confirmação reutilizada para outra decisão.");
            return resolution(rs);
        },id,request.requestKey());
        if(!replay.isEmpty()) return replay.getFirst();
        var analysis=analyze(id);
        long revision=analysis.history().isEmpty()?0:analysis.history().getFirst().revision();
        if(request.expectedRevision()!=revision) throw error(HttpStatus.CONFLICT,"O lote foi revisado em outra sessão. Atualize as evidências.");
        if(!analysis.evidenceVersion().equals(request.evidenceVersion())) throw error(HttpStatus.CONFLICT,"As evidências oficiais mudaram. Atualize antes de confirmar.");
        if(!request.confirmed()) throw error(HttpStatus.BAD_REQUEST,"Confirmação explícita é obrigatória.");
        String state, method; Candidate target=null; Instant captured=null; String zone=null;
        if(Set.of("REJECTED","PENDING_IDENTIFICATION").contains(request.action())) {
            state=request.action(); method="USER_REVIEW";
        } else if(!request.action().equals("IDENTIFY")) throw error(HttpStatus.BAD_REQUEST,"Ação de identificação inválida.");
        else if(analysis.format().equals("RIVER")) {
            target=analysis.candidates().stream().filter(c->c.key().equals(request.candidateKey())).findFirst()
                    .orElseThrow(()->error(HttpStatus.BAD_REQUEST,"Escolha uma correspondência apresentada nas evidências."));
            state=target.incompatibleValues()>0?"CONFLICT":"IDENTIFIED";
            method=analysis.match().equals("EXACT_MATCH") && target.equalFingerprint()?"USER_CONFIRMED_EXACT_MATCH":"USER_CONFIRMED_POSSIBLE_MATCH";
        } else {
            if(!config.clanTag().equals(request.clanTag())) throw error(HttpStatus.BAD_REQUEST,"Confirme o clã configurado.");
            if(request.capturedAtLocal()==null || request.timezone()==null || request.timezone().length()>100)
                throw error(HttpStatus.BAD_REQUEST,"Informe a data da captura e seu fuso horário.");
            try {
                var timezone=ZoneId.of(request.timezone()); var offsets=timezone.getRules().getValidOffsets(request.capturedAtLocal());
                if(offsets.size()!=1) throw error(HttpStatus.BAD_REQUEST,"Horário inexistente ou ambíguo nesse fuso. Informe um horário inequívoco.");
                captured=request.capturedAtLocal().toInstant(offsets.getFirst()); zone=timezone.getId();
            } catch(DateTimeException e) { throw error(HttpStatus.BAD_REQUEST,"Fuso horário inválido."); }
            if(!captured.isAfter(Instant.EPOCH) || captured.isAfter(clock.instant())) throw error(HttpStatus.BAD_REQUEST,"A captura deve ser uma data passada válida.");
            method=request.timestampOrigin();
            if("USER_CONFIRMED_FILENAME".equals(method)) {
                if(analysis.suggestedCapturedAt()==null || !analysis.suggestedCapturedAt().equals(request.capturedAtLocal()))
                    throw error(HttpStatus.BAD_REQUEST,"A data alterada deve ser registrada como USER_ENTERED.");
            } else if(!"USER_ENTERED".equals(method)) throw error(HttpStatus.BAD_REQUEST,"Procedência de data inválida.");
            state="IDENTIFIED";
        }
        var evidence=json.createObjectNode();
        evidence.put("policyVersion","river-identity-v1");
        evidence.set("consideredCandidates",json.valueToTree(analysis.candidates().stream().map(c -> Map.of(
                "key",c.key(),"fingerprint",c.fingerprint(),"equalFingerprint",c.equalFingerprint(),
                "matchingTags",c.matchingTags(),"incompatibleValues",c.incompatibleValues(),"officialAt",c.officialAt().toString())).toList()));
        evidence.put("match",analysis.match()); evidence.put("fingerprint",analysis.fingerprint());
        evidence.put("evidenceVersion",analysis.evidenceVersion()); evidence.put("clanTag",analysis.clanTag());
        evidence.put("filename",analysis.filename()); evidence.set("suggestedCapturedAt",json.valueToTree(analysis.suggestedCapturedAt()));
        evidence.put("suggestedTimezone",analysis.suggestedTimezone()); evidence.set("warnings",json.valueToTree(analysis.warnings()));
        evidence.set("candidate",json.valueToTree(target)); evidence.set("submitted",json.valueToTree(request));
        Instant now=clock.instant();
        return sql.queryForObject("""
            insert into import_resolution(batch_id,request_key,request,resolved_by,resolved_at,status,resolution_method,
              target_kind,target_id,season_id,section_index,captured_at,timezone,reason,manually_confirmed,evidence)
            values(?,?,?::jsonb,?,?,?,?,?,?,?,?,?,?,?,true,?::jsonb) returning *
            """,(rs,n)->resolution(rs),id,request.requestKey(),encode(request),actor,Timestamp.from(now),state,method,
                target==null?null:target.kind(),target==null?null:target.targetId(),target==null?null:target.seasonId(),target==null?null:target.sectionIndex(),
                captured==null?null:Timestamp.from(captured),zone,request.reason().trim(),encode(evidence));
    }
    private List<Resolution> history(UUID id) {
        return sql.query("select * from import_resolution where batch_id=? order by revision desc",(rs,n)->resolution(rs),id);
    }
    private Resolution resolution(ResultSet rs) throws SQLException {
        var captured=rs.getTimestamp("captured_at");
        return new Resolution(rs.getLong("revision"),rs.getObject("batch_id",UUID.class),rs.getString("status"),rs.getString("resolved_by"),
                rs.getTimestamp("resolved_at").toInstant(),rs.getString("resolution_method"),rs.getString("target_kind"),rs.getObject("target_id",Long.class),
                rs.getObject("season_id",Integer.class),rs.getObject("section_index",Integer.class),captured==null?null:captured.toInstant(),rs.getString("timezone"),
                rs.getString("reason"),rs.getBoolean("manually_confirmed"),tree(rs.getString("evidence")));
    }
    private JsonNode tree(String value) { try { return json.readTree(value); } catch(JsonProcessingException e) { throw new IllegalStateException("Auditoria inválida.",e); } }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(JsonProcessingException e) { throw new IllegalStateException("Falha de serialização.",e); } }
    private ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
