package com.frotafantasma.analytics.weeklyreview;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.frotafantasma.analytics.weeklyreview.WeeklyReviewEngine.*;
import static com.frotafantasma.analytics.weeklyreview.WeeklyParticipation.*;

class WeeklyParticipationTest {
    final LocalDate week=LocalDate.parse("2026-10-26");
    final Instant at=deadline(week);
    Row row=new Row("#P","P","member",0,0,at,0L,null,true,false,"REVIEW",List.of(),0);
    Report report() {return new Report(week,at,at,at,ZONE.toString(),"CLOSED",1600,5,true,at,at,1L,true,true,true,List.of(row));}
    List<WarInput> wars() {return List.of(
        new WarInput(2,Instant.parse("2026-10-26T10:00:00Z"),at,null,null,"#P",0,0),
        new WarInput(1,Instant.parse("2026-10-19T10:00:00Z"),at,null,null,"#P",0,0));}
    @Test void officialRecurrenceRequiresEpisodeBeforeBothWars() {
        var veteran=List.of(new Event("BASELINE",at.minus(Duration.ofDays(60))));
        var c=context(row,report(),wars(),veteran,List.of());
        assertThat(c.twoBelow()).isTrue();assertThat(c.twoZero()).isTrue();assertThat(c.consecutiveZeroAttacks()).isEqualTo(2);
        assertThat(c.group()).isEqualTo("PRIORITY");assertThat(c.averageFour()).isZero();
        var recent=context(row,report(),wars(),List.of(new Event("JOIN",at.minus(Duration.ofDays(3)))),List.of());
        assertThat(recent.newcomer()).isTrue();assertThat(recent.twoBelow()).isFalse();assertThat(recent.warsConsidered()).isZero();
        var rejoined=context(row,report(),wars(),List.of(veteran.getFirst(),new Event("LEAVE",at.minus(Duration.ofDays(4))),new Event("JOIN",at.minus(Duration.ofDays(3)))),List.of());
        assertThat(rejoined.rejoined()).isTrue();assertThat(rejoined.twoZero()).isFalse();
    }
    @Test void absentParticipantBreaksStreakAndLiveCaptureCannotCreateOfficialRecurrence() {
        var inputs=new ArrayList<>(wars());
        inputs.set(0,new WarInput(2,inputs.getFirst().published(),at,null,null,"#OTHER",0,0));
        var c=context(row,report(),inputs,List.of(new Event("BASELINE",at.minus(Duration.ofDays(60)))),List.of());
        assertThat(c.twoZero()).isFalse();assertThat(c.twoBelow()).isFalse();assertThat(c.wars().getFirst().points()).isNull();
        var live=context(row,report(),List.of(),List.of(new Event("BASELINE",at.minus(Duration.ofDays(60)))),List.of());
        assertThat(live.group()).isEqualTo("FOLLOW");assertThat(live.consecutiveZeroAttacks()).isZero();
    }
    @Test void missingWeekBreaksConsecutiveAttackStreak() {
        var data=new ArrayList<>(wars());
        var old=data.get(1);
        data.set(1,new WarInput(old.id(),old.published().minus(Duration.ofDays(7)),old.captured(),null,null,old.tag(),0,0));
        var c=context(row,report(),data,List.of(new Event("BASELINE",at.minus(Duration.ofDays(60)))),List.of());
        assertThat(c.consecutiveZeroAttacks()).isEqualTo(1);assertThat(c.twoZero()).isFalse();
    }
    @Test void donationZerosRequireCoverageAndResetsNeverBridgeEpisodesOrWeeks() {
        var start=week.minusWeeks(1).atStartOfDay(ZONE).toInstant();
        var rows=new ArrayList<Sample>();
        for(int h=0;h<168;h++)rows.add(new Sample(start.plus(Duration.ofHours(h)),0));
        var valid=donation(week.minusWeeks(1),at,rows,List.of(),start.minusSeconds(1));
        assertThat(valid.zeroConfirmed()).isTrue();assertThat(valid.observed()).isZero();
        assertThat(donation(week.minusWeeks(1),start.plus(Duration.ofDays(3)),rows,List.of(),start.minusSeconds(1)).zeroConfirmed()).isFalse();
        rows.remove(40);
        assertThat(donation(week.minusWeeks(1),at,rows,List.of(),start.minusSeconds(1)).zeroConfirmed()).isFalse();
        var reset=List.of(new Sample(start,100),new Sample(start.plusSeconds(1800),5),new Sample(start.plusSeconds(3600),10));
        var d=donation(week.minusWeeks(1),at,reset,List.of(),start.minusSeconds(1));
        assertThat(d.resets()).isEqualTo(1);assertThat(d.observed()).isEqualTo(10);assertThat(d.zeroConfirmed()).isFalse();
        var joined=donation(week.minusWeeks(1),at,reset,List.of(new Event("JOIN",start.plusSeconds(2000))),start.minusSeconds(1));
        assertThat(joined.observed()).isEqualTo(5);
        assertThat(donation(week,at,reset,List.of(),start).observed()).isNull();
        assertThat(donation(week.minusWeeks(1),at,List.of(),List.of(),start).observed()).isNull();
    }
}
