package com.frotafantasma.analytics.leadership;

import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.recommendation.RecommendationDtos.Page;
import jakarta.validation.constraints.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LeadershipNotes {
    public enum Kind { OBSERVATION, JUSTIFICATION, AGREEMENT }
    public record Create(@NotNull UUID requestKey, @NotNull Kind kind, @NotBlank @Size(max=2000) String body,
                         LocalDate expiresOn, LocalDate followUpOn) {}
    public record Resolve(@NotBlank @Size(max=1000) String reason) {}
    public record Note(UUID id, String playerTag, Kind kind, String body, Instant createdAt, String createdBy,
                       LocalDate expiresOn, LocalDate followUpOn, Instant resolvedAt, String resolvedBy,
                       String resolution, String status, boolean followUpDue) {}
    public record Active(Instant generatedAt, String timezone, List<Note> notes) {}
    private static final ZoneId ZONE=ZoneId.of("America/Sao_Paulo");
    private static final String SELECT="""
        select n.*,r.resolved_at,r.resolved_by,r.reason as resolution
        from leadership_note n left join leadership_note_resolution r on r.note_id=n.id and r.clan_tag=n.clan_tag
        """;
    private final JdbcTemplate sql;
    private final ClashRoyaleProperties api;
    private final Clock clock;
    public LeadershipNotes(JdbcTemplate sql, ClashRoyaleProperties api, Clock clock) { this.sql=sql; this.api=api; this.clock=clock; }
    private LocalDate today() { return LocalDate.now(clock.withZone(ZONE)); }
    private Note map(ResultSet r,int i) throws SQLException {
        LocalDate expires=r.getObject("expires_on",LocalDate.class), follow=r.getObject("follow_up_on",LocalDate.class);
        Timestamp resolved=r.getTimestamp("resolved_at");
        String status=resolved!=null?"RESOLVED":expires!=null&&expires.isBefore(today())?"EXPIRED":"ACTIVE";
        return new Note(r.getObject("id",UUID.class),r.getString("player_tag"),Kind.valueOf(r.getString("kind")),r.getString("body"),
            r.getTimestamp("created_at").toInstant(),r.getString("created_by"),expires,follow,resolved==null?null:resolved.toInstant(),
            r.getString("resolved_by"),r.getString("resolution"),status,"ACTIVE".equals(status)&&follow!=null&&!follow.isAfter(today()));
    }
    private void lock() { sql.query("select pg_advisory_xact_lock(hashtextextended(?,0))",r->{},"frota:notes:"+api.clanTag()); }
    public Page<Note> history(String tag,int page,int size) {
        var items=sql.query(SELECT+" where n.clan_tag=? and n.player_tag=? order by n.created_at desc,n.id desc limit ? offset ?",
                this::map,api.clanTag(),tag,size,(long)page*size);
        long total=sql.queryForObject("select count(*) from leadership_note where clan_tag=? and player_tag=?",Long.class,api.clanTag(),tag);
        return new Page<>(items,page,size,total);
    }
    public Active active() {
        return new Active(clock.instant(),ZONE.toString(),sql.query(SELECT+
                " where n.clan_tag=? and r.note_id is null and (n.expires_on is null or n.expires_on>=?) order by n.follow_up_on nulls last,n.created_at desc,n.id",
                this::map,api.clanTag(),today()));
    }
    @Transactional public Note create(String tag,Create request,String author) {
        lock();
        var previous=sql.query(SELECT+" where n.clan_tag=? and n.request_key=?",this::map,api.clanTag(),request.requestKey());
        if (!previous.isEmpty()) {
            var n=previous.getFirst();
            if (!n.playerTag().equals(tag)||n.kind()!=request.kind()||!n.body().equals(request.body().strip())
                ||!Objects.equals(n.expiresOn(),request.expiresOn())||!Objects.equals(n.followUpOn(),request.followUpOn()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Identificador já usado para outra anotação.");
            return n;
        }
        boolean known=Boolean.TRUE.equals(sql.queryForObject("""
            select exists(select 1 from clan_membership_history h join clan c on c.id=h.clan_id
                join player p on p.id=h.player_id where c.tag=? and p.player_tag=?)
            """,Boolean.class,api.clanTag(),tag));
        if (!known) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Membro sem histórico neste clã.");
        if (request.expiresOn()!=null&&request.expiresOn().isBefore(today()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A validade não pode estar no passado.");
        if (request.followUpOn()!=null&&request.followUpOn().isBefore(today()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A data de acompanhamento não pode estar no passado.");
        if(request.expiresOn()!=null&&request.followUpOn()!=null&&request.followUpOn().isAfter(request.expiresOn()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"O acompanhamento deve ocorrer até o fim da validade.");
        UUID id=UUID.randomUUID();
        sql.update("insert into leadership_note(id,clan_tag,player_tag,kind,body,created_at,created_by,expires_on,follow_up_on,request_key) values (?,?,?,?,?,?,?,?,?,?)",
                id,api.clanTag(),tag,request.kind().name(),request.body().strip(),Timestamp.from(clock.instant()),author,request.expiresOn(),request.followUpOn(),request.requestKey());
        return get(id);
    }
    private Note get(UUID id) {
        return sql.query(SELECT+" where n.clan_tag=? and n.id=?",this::map,api.clanTag(),id).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Anotação não encontrada neste clã."));
    }
    @Transactional public Note resolve(UUID id,Resolve request,String author) {
        lock();
        var note=get(id);
        if(note.resolvedAt()!=null) return note;
        sql.update("insert into leadership_note_resolution(note_id,clan_tag,resolved_at,resolved_by,reason) values (?,?,?,?,?)",
                id,api.clanTag(),Timestamp.from(clock.instant()),author,request.reason().strip());
        return get(id);
    }
}
