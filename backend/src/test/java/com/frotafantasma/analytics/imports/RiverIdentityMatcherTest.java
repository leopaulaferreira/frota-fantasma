package com.frotafantasma.analytics.imports;

import com.frotafantasma.analytics.imports.dto.IdentificationDtos.*;
import com.frotafantasma.analytics.imports.service.*;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RiverIdentityMatcherTest {
    final RiverIdentityMatcher matcher=new RiverIdentityMatcher();
    final List<Participant> rows=List.of(new Participant("#P01",100,4,0,0),new Participant("#P02",200,8,0,0),new Participant("#P03",300,12,0,0));
    Target target(long id,List<Participant> participants) { return new Target("WAR",id,133,3,null,null,Instant.parse("2026-10-26T10:00:00Z"),"#RQJGC9GV",participants); }
    @Test void uniqueFullFingerprintIsExactAndIgnoresOrderAndTagPrefix() {
        var reversed=List.of(new Participant("P03",300,12,0,0),new Participant("P02",200,8,0,0),new Participant("P01",100,4,0,0));
        var result=matcher.match("RQJGC9GV",rows,List.of(target(1,reversed)));
        assertThat(result.match()).isEqualTo("EXACT_MATCH");
        assertThat(result.candidates().getFirst().equalFingerprint()).isTrue();
        assertThat(result.candidates().getFirst().identicalRows()).isEqualTo(3);
    }
    @Test void identicalTargetsAreAmbiguousNotArbitrarilySelected() {
        var result=matcher.match("#RQJGC9GV",rows,List.of(target(1,rows),target(2,rows)));
        assertThat(result.match()).isEqualTo("POSSIBLE_MATCH"); assertThat(result.candidates()).hasSize(2);
    }
    @Test void missingEvidenceAndOtherClanNeverMatch() {
        assertThat(matcher.match("#RQJGC9GV",rows,List.of()).match()).isEqualTo("UNRESOLVED");
        assertThat(matcher.match("#OTHER",rows,List.of(target(1,rows))).match()).isEqualTo("UNRESOLVED");
        assertThat(matcher.match("#RQJGC9GV",rows,List.of(target(1,List.of(new Participant("#NOPE",100,4,0,0))))).match()).isEqualTo("UNRESOLVED");
    }
    @Test void lowerImportedCountersArePossibleAndHigherCountersAreIncompatible() {
        var later=rows.stream().map(p->new Participant(p.tag(),p.fame()+100,p.decks()+4,0,0)).toList();
        var result=matcher.match("#RQJGC9GV",rows,List.of(target(1,later)));
        assertThat(result.match()).isEqualTo("POSSIBLE_MATCH");
        assertThat(result.candidates().getFirst().officialGreaterValues()).isEqualTo(6);
        assertThat(result.candidates().getFirst().incompatibleValues()).isZero();
        var conflict=matcher.match("#RQJGC9GV",later,List.of(target(1,rows))).candidates().getFirst();
        assertThat(conflict.incompatibleValues()).isEqualTo(6);
        assertThat(conflict.equalFingerprint()).isFalse();
    }
    @Test void moreThanFiftyParticipantsRemainInFingerprint() {
        var many=IntStream.range(0,81).mapToObj(i->new Participant("#P"+String.format("%03d",i),100+i,4,0,0)).toList();
        assertThat(matcher.match("#RQJGC9GV",many,List.of(target(1,many))).candidates().getFirst().matchingTags()).isEqualTo(81);
    }
    @Test void missingValuesZerosAndDuplicateTagsDoNotFabricateCertainty() {
        var missing=List.of(new Participant("#P01",null,4,0,0));
        assertThat(matcher.match("#RQJGC9GV",missing,List.of(target(1,missing))).match()).isEqualTo("POSSIBLE_MATCH");
        var zeros=List.of(new Participant("#P01",0,0,0,0));
        assertThat(matcher.match("#RQJGC9GV",zeros,List.of(target(1,zeros))).match()).isEqualTo("POSSIBLE_MATCH");
        assertThat(matcher.match("#RQJGC9GV",List.of(rows.getFirst(),rows.getFirst()),List.of(target(1,rows))).match()).isEqualTo("UNRESOLVED");
    }
    @Test void filenameUsesMinuteAndNeverLastComponentAsSeconds() {
        assertThat(ImportFilenameDate.suggest("clan-RQJGC9GV-2026-10-26-02-10-94.csv","#RQJGC9GV")).isEqualTo(LocalDateTime.parse("2026-10-26T02:10"));
        assertThat(ImportFilenameDate.suggest("clan-RQJGC9GV-2026-10-26-02-10-30.csv","#RQJGC9GV")).isEqualTo(LocalDateTime.parse("2026-10-26T02:10"));
        for(String filename:List.of("export.csv","clan-RQJGC9GV-2026-02-30-02-10-94.csv","clan-OTHER-2026-10-26-02-10-94.csv"))
            assertThat(ImportFilenameDate.suggest(filename,"#RQJGC9GV")).isNull();
    }
}
