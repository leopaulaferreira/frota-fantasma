package com.frotafantasma.analytics.weeklyreview;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.frotafantasma.analytics.weeklyreview.WeeklyReviewEngine.*;
import static org.assertj.core.api.Assertions.*;

class WeeklyReviewEngineTest {
    final ObjectMapper json = new ObjectMapper();
    final LocalDate week = LocalDate.parse("2026-10-12");
    final Instant at = Instant.parse("2026-10-19T00:59:00Z"); // Sunday 21:59 São Paulo
    Member member(String tag, long offlineSeconds) { return new Member(tag,tag,"member",at.minusSeconds(offlineSeconds),at.minus(Duration.ofDays(3))); }
    Race race(String payload) throws Exception { return new Race(at,1L,"warDay",json.readTree(payload)); }
    @Test void exactBoundariesAndEitherReasonApplyToEveryRole() throws Exception {
        var members=List.of(member("#EXACT",5*86400),member("#LOW",0),new Member("#LEADER","Líder","leader",at.minusSeconds(5*86400+1),null));
        var report=analyze(week,at,true,at,3,members,race("""
                {"clan":{"participants":[{"tag":"#EXACT","fame":1600},{"tag":"#LOW","fame":1599},{"tag":"#LEADER","fame":3000}]}}
                """));
        var byTag=new HashMap<String,Row>();report.members().forEach(r->byTag.put(r.tag(),r));
        assertThat(byTag.get("#EXACT").status()).isEqualTo("CLEAR");
        assertThat(byTag.get("#LOW").belowPoints()).isTrue();
        assertThat(byTag.get("#LOW").offlineExceeded()).isFalse();
        assertThat(byTag.get("#LEADER").offlineExceeded()).isTrue();
        assertThat(byTag.get("#LEADER").status()).isEqualTo("REVIEW");
    }
    @Test void absentPointsAreUnknownButExplicitZeroIsReviewed() throws Exception {
        var report=analyze(week,at,true,at,3,List.of(member("#ABSENT",0),member("#ZERO",0),member("#OFFLINE",6*86400)),race("""
                {"clan":{"participants":[{"tag":"#ZERO","fame":0}]}}
                """));
        assertThat(report.members()).extracting(Row::tag).containsExactly("#ZERO","#OFFLINE","#ABSENT");
        assertThat(report.members().getLast().points()).isNull();
        assertThat(report.members().getLast().status()).isEqualTo("UNKNOWN");
        assertThat(report.members().get(1).offlineExceeded()).isTrue();
        assertThat(report.members().get(1).missingEvidence()).isNotEmpty();
    }
    @Test void staleRosterNeverAccusesAndOldRaceNeverBecomesThisWeeksScore() throws Exception {
        var members=List.of(member("#P",6*86400));
        var race=race("{\"clan\":{\"participants\":[{\"tag\":\"#P\",\"fame\":0}]}}");
        assertThat(analyze(week,at,true,at.minusSeconds(3601),1,members,race).members().getFirst().status()).isEqualTo("UNKNOWN");
        assertThat(analyze(week,at,true,at,2,members,race).members().getFirst().status()).isEqualTo("UNKNOWN");
        var old=new Race(at.minus(Duration.ofDays(7)),1L,"warDay",race.payload());
        var row=analyze(week,at,true,at,1,List.of(member("#P",0)),old).members().getFirst();
        assertThat(row.points()).isNull();assertThat(row.status()).isEqualTo("UNKNOWN");
        var training=new Race(at,1L,"training",race.payload());
        assertThat(analyze(week,at,true,at,1,List.of(member("#P",0)),training).members().getFirst().points()).isNull();
    }
    @Test void futureOrMissingLastSeenDoesNotBecomeOffline() throws Exception {
        for(Instant lastSeen:Arrays.asList(null,at.plusSeconds(1))) {
            var row=analyze(week,at,true,at,1,List.of(new Member("#P","P","elder",lastSeen,null)),race("{\"clan\":{\"participants\":[{\"tag\":\"#P\",\"fame\":1600}]}}")).members().getFirst();
            assertThat(row.status()).isEqualTo("UNKNOWN");assertThat(row.offlineSeconds()).isNull();assertThat(row.offlineExceeded()).isFalse();
        }
    }
    @Test void cutoffUsesSunday22SaoPauloEvenWhenUtcAlreadyMonday() throws Exception {
        assertThat(deadline(week)).isEqualTo(Instant.parse("2026-10-19T01:00:00Z"));
        assertThat(defaultWeek(Instant.parse("2026-10-19T00:59:59Z"))).isEqualTo(week);
        assertThat(defaultWeek(Instant.parse("2026-10-19T12:00:00Z"))).isEqualTo(week);
        assertThat(defaultWeek(Instant.parse("2026-10-22T12:00:00Z"))).isEqualTo(week.plusWeeks(1));
        var report=analyze(week,Instant.parse("2026-10-21T12:00:00Z"),true,at,1,List.of(member("#P",0)),race("{\"clan\":{\"participants\":[{\"tag\":\"#P\",\"fame\":1599}]}}"));
        assertThat(report.phase()).isEqualTo("CLOSED");assertThat(report.membersFresh()).isTrue();
        assertThat(report.cutoff()).isEqualTo(deadline(week));assertThat(report.members().getFirst().belowPoints()).isTrue();
    }
}
