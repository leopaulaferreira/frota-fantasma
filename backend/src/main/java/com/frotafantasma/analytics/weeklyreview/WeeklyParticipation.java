package com.frotafantasma.analytics.weeklyreview;

import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import static com.frotafantasma.analytics.weeklyreview.WeeklyReviewEngine.*;

/** Read-only evidence at the review cutoff. Official history is never rewritten. */
@Service
public class WeeklyParticipation {
    public record War(long id, Instant publishedAt, Instant capturedAt, Integer points, Integer decks,
                      boolean eligible, String source, String reason) {}
    public record Event(String type, Instant at) {}
    public record Donation(LocalDate week, Long observed, Integer lastCounter, int samples, int resets,
                           boolean complete, boolean zeroConfirmed, String reason) {}
    public record Context(List<War> wars, Double averageFour, int warsConsidered, int belowTarget,
                          int consecutiveZeroAttacks, boolean twoBelow, boolean twoZero,
                          String trend, List<Donation> donationWeeks, Long totalFourWeeks,
                          boolean donationCoverageComplete, int zeroDonationWeeks, boolean twoZeroDonations,
                          List<Event> events, Long tenureDays, boolean newcomer, boolean rejoined,
                          String tenureBasis, String group, List<String> reasons) {}
    record WarInput(long id, Instant published, Instant captured, Instant start, Instant end, String tag, Integer points, Integer decks) {}
    record Sample(Instant at, Integer value) {}
    private final JdbcTemplate sql;
    private final ClashRoyaleProperties api;
    public WeeklyParticipation(JdbcTemplate sql, ClashRoyaleProperties api) { this.sql=sql; this.api=api; }
    public Report enrich(Report report) {
        var cutoff=Timestamp.from(report.cutoff());
        var wars=sql.query("""
            select w.id,w.published_at,w.captured_at,w.start_date,w.end_date,p.player_tag,wp.contribution,wp.decks_used
            from war w join clan c on c.id=w.clan_id
            left join war_participant wp on wp.war_id=w.id and wp.source='DIRECT_API' and wp.captured_at<=?
            left join player p on p.id=wp.player_id
            where c.tag=? and w.source='DIRECT_API' and w.captured_at<=? and w.published_at<=?
            order by w.published_at desc,w.id desc
            """, (r,n)->new WarInput(r.getLong(1),r.getTimestamp(2).toInstant(),r.getTimestamp(3).toInstant(),instant(r.getTimestamp(4)),instant(r.getTimestamp(5)),r.getString(6),number(r.getObject(7,Integer.class)),number(r.getObject(8,Integer.class))),cutoff,api.clanTag(),cutoff,cutoff);
        var events=new HashMap<String,List<Event>>();
        sql.query("""
            select p.player_tag,e.type,e.observed_at from membership_event e join player p on p.id=e.player_id join clan c on c.id=e.clan_id
            where c.tag=? and e.observed_at<=? order by e.observed_at,e.id
            """,r->{events.computeIfAbsent(r.getString(1),k->new ArrayList<>()).add(new Event(r.getString(2),r.getTimestamp(3).toInstant()));},api.clanTag(),cutoff);
        var samples=new HashMap<String,List<Sample>>();
        sql.query("""
            select p.player_tag,h.captured_at,h.donations from clan_membership_history h join player p on p.id=h.player_id join clan c on c.id=h.clan_id
            where c.tag=? and h.source='DIRECT_API' and h.captured_at>=? and h.captured_at<=? order by h.captured_at,h.id
            """,r->{samples.computeIfAbsent(r.getString(1),k->new ArrayList<>()).add(new Sample(r.getTimestamp(2).toInstant(),number(r.getObject(3,Integer.class))));},api.clanTag(),Timestamp.from(report.week().minusWeeks(4).atStartOfDay(ZONE).toInstant()),cutoff);
        var rows=report.members().stream().map(row->row.withParticipation(context(row,report,wars,events.getOrDefault(row.tag(),List.of()),samples.getOrDefault(row.tag(),List.of())))).toList();
        return new Report(report.week(),report.generatedAt(),report.cutoff(),report.deadline(),report.timezone(),report.phase(),report.pointsTarget(),report.offlineDays(),report.collectionEnabled(),report.membersCapturedAt(),report.riverCapturedAt(),report.riverSnapshotId(),report.membersFresh(),report.riverFresh(),report.rosterComplete(),rows);
    }
    static Context context(Row row, Report report, List<WarInput> inputs, List<Event> events, List<Sample> samples) {
        var episode=events.stream().filter(e->Set.of("JOIN","BASELINE","LEAVE").contains(e.type())).reduce((a,b)->b).orElse(null);
        Instant since=episode!=null && !episode.type().equals("LEAVE")?episode.at():null;
        Long days=since==null?null:Math.max(0,Duration.between(since,report.cutoff()).toDays());
        boolean newcomer=days!=null && days<14;
        boolean rejoined=events.stream().anyMatch(e->e.type().equals("LEAVE")) && episode!=null && episode.type().equals("JOIN");
        var unique=new LinkedHashMap<Long,WarInput>();
        for(var w:inputs) unique.putIfAbsent(w.id(),w);
        var playerWars=new HashMap<Long,WarInput>();
        for(var w:inputs) if(row.tag().equals(w.tag())) playerWars.put(w.id(),w);
        var history=new ArrayList<War>();
        for(var w:unique.values()) {
            var p=playerWars.get(w.id());
            // Missing official dates are not invented: a conservative seven-day window guards eligibility.
            Instant guard=w.start()!=null?w.start():w.published().minus(Duration.ofDays(7));
            boolean eligible=since!=null && !since.isAfter(guard);
            String reason=!eligible?"Vínculo não comprovado antes da guerra; não conta como falta.":w.start()==null?"Datas oficiais ausentes; elegibilidade exige vínculo observado ao menos sete dias antes da publicação.":"Vínculo observado antes do início oficial.";
            if(p==null) reason+=" Participante ausente: pontuação e ataques desconhecidos.";
            history.add(new War(w.id(),w.published(),w.captured(),p==null?null:p.points(),p==null?null:p.decks(),eligible,"DIRECT_API",reason));
        }
        var four=history.stream().filter(w->w.eligible() && w.points()!=null).limit(4).toList();
        var scores=four.stream().map(War::points).filter(Objects::nonNull).toList();
        Double average=scores.isEmpty()?null:scores.stream().mapToInt(Integer::intValue).average().orElseThrow();
        int below=(int)scores.stream().filter(p->p<POINTS_TARGET).count();
        int streak=0;
        Instant newer=null;
        for(var w:history) {
            if(!w.eligible() || w.decks()==null || w.decks()!=0
                || (newer!=null && Duration.between(w.publishedAt(),newer).compareTo(Duration.ofDays(8))>0)) break;
            streak++; newer=w.publishedAt();
        }
        boolean twoBelow=history.size()>=2 && history.subList(0,2).stream().allMatch(w->w.eligible() && w.points()!=null && w.points()<POINTS_TARGET);
        String trend="Dados insuficientes";
        if(history.size()>=2) {
            var a=history.get(0);var b=history.get(1);
            if(a.eligible() && b.eligible() && a.decks()!=null && b.decks()!=null)
                trend=a.decks()<b.decks()?"Queda de participação":a.decks()>b.decks()?"Aumento de participação":"Participação estável";
        }
        var donations=new ArrayList<Donation>();
        // Four completed calendar weeks, plus the selected partial week for context.
        for(int i=0;i<=4;i++) donations.add(donation(report.week().minusWeeks(i),report.cutoff(),samples,events,since));
        var closed=donations.subList(1,5);
        boolean coverage=closed.stream().allMatch(Donation::complete);
        Long total=closed.stream().allMatch(d->d.observed()!=null)?closed.stream().mapToLong(Donation::observed).sum():null;
        int zeros=(int)closed.stream().filter(Donation::zeroConfirmed).count();
        boolean twoDonations=closed.get(0).zeroConfirmed() && closed.get(1).zeroConfirmed();
        var reasons=new ArrayList<String>();
        if(twoBelow) reasons.add("Duas últimas guerras oficiais abaixo de 1.600 pontos, com vínculo elegível.");
        if(streak>=2) reasons.add(streak+" guerras oficiais consecutivas com zero decks explícito.");
        if(twoDonations) reasons.add("Zero doações observado com cobertura nas duas últimas semanas completas.");
        if(row.belowPoints()) reasons.add("Corrida do recorte abaixo de 1.600 pontos; resultado parcial, não é uma guerra finalizada.");
        if(row.offlineExceeded()) reasons.add("Mais de cinco dias offline na captura válida.");
        if(trend.equals("Queda de participação")) reasons.add("Menos decks na última guerra oficial que na anterior.");
        if(newcomer) reasons.add("Membro recente: não presumir disponibilidade integral de ataques.");
        if(rejoined) reasons.add("Reingresso observado: histórico anterior ao vínculo atual não conta como falta.");
        boolean priority=twoBelow || streak>=2 || twoDonations;
        boolean follow=row.belowPoints() || row.offlineExceeded() || below>0 || trend.equals("Queda de participação");
        boolean enough=report.membersFresh() && !scores.isEmpty();
        String group=!report.membersFresh()?"INSUFFICIENT":priority?"PRIORITY":follow || enough?"FOLLOW":"INSUFFICIENT";
        if(!priority && !follow && enough) reasons.add("Sem reincidência confirmada; acompanhar normalmente, sem recomendação de dispensa.");
        if(group.equals("INSUFFICIENT")) reasons.add("Evidência insuficiente para recomendar uma decisão.");
        return new Context(List.copyOf(history),average,scores.size(),below,streak,twoBelow,streak>=2,trend,List.copyOf(donations),total,coverage,zeros,twoDonations,List.copyOf(events),days,newcomer,rejoined,episode==null?"Desconhecida":episode.type().equals("BASELINE")?"Observado desde; entrada real desconhecida":"Entrada observada",group,List.copyOf(reasons));
    }
    static Donation donation(LocalDate week, Instant cutoff, List<Sample> all, List<Event> events, Instant since) {
        Instant start=week.atStartOfDay(ZONE).toInstant(),end=week.plusWeeks(1).atStartOfDay(ZONE).toInstant();
        var rows=all.stream().filter(s->!s.at().isBefore(start) && s.at().isBefore(end) && !s.at().isAfter(cutoff)).toList();
        long sum=0;int comparisons=0,resets=0;boolean gaps=false;
        for(int i=1;i<rows.size();i++) {
            var a=rows.get(i-1);var b=rows.get(i);
            boolean crossed=events.stream().anyMatch(e->Set.of("JOIN","LEAVE").contains(e.type()) && e.at().isAfter(a.at()) && !e.at().isAfter(b.at()));
            if(a.value()==null || b.value()==null || crossed || !b.at().isAfter(a.at())) {gaps=true;continue;}
            int delta=b.value()-a.value();sum+=delta<0?b.value():delta;if(delta<0)resets++;comparisons++;
            if(Duration.between(a.at(),b.at()).compareTo(MAX_AGE)>0)gaps=true;
        }
        boolean complete=!cutoff.isBefore(end) && since!=null && !since.isAfter(start) && rows.size()>1 && !gaps && resets==0
            && !rows.getFirst().at().isAfter(start.plus(MAX_AGE)) && !rows.getLast().at().isBefore(end.minus(MAX_AGE))
            && rows.stream().allMatch(s->s.value()!=null)
            && events.stream().noneMatch(e->Set.of("JOIN","LEAVE").contains(e.type()) && !e.at().isBefore(start) && e.at().isBefore(end));
        boolean zero=complete && rows.stream().allMatch(s->s.value()==0);
        return new Donation(week,comparisons==0?null:sum,rows.isEmpty()?null:rows.getLast().value(),rows.size(),resets,complete,zero,
            "Soma de diferenças dentro da semana; reset inicia segmento. Não soma primeiro contador nem cruza reingresso. "
            +(complete?"Cobertura observacional de até uma hora; não garante atividade entre capturas.":"Cobertura incompleta: zero observado não confirma semana sem doações."));
    }
    private static Instant instant(Timestamp t) {return t==null?null:t.toInstant();}
    private static Integer number(Integer n) {return n==null || n<0?null:n;}
}
