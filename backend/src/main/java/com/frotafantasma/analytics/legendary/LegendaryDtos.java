package com.frotafantasma.analytics.legendary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import static com.frotafantasma.analytics.legendary.LegendaryProperties.Source;

public final class LegendaryDtos {
    private LegendaryDtos() {}
    public record Cycle(LocalDate week, Instant closesAt, Instant deadline, Instant createdAt,
                        Instant finalizedAt, String status, Source recordSource, String recordEra,
                        Instant eraStart, int trophyLimit, boolean official, Long warId, String winnerTag) {}
    public record Snapshot(String tag, String name, String role, boolean fullCycle, Integer trophies,
                           Integer apiBestTrophies, Integer recordTrophies, Instant capturedAt,
                           String profileClanTag, String errorCode, int attempts) {}
    public record Candidate(Integer position, String tag, String name, Integer previousTrophies,
                            Integer trophies, Integer netChange, Integer previousRecord, Integer record,
                            Integer improvement, Integer warMedals, Integer decksUsed, boolean eligible,
                            List<String> reasons, Instant capturedAt, Integer apiBestTrophies) {}
    public record Result(Cycle cycle, List<Candidate> candidates, String notice) {}
    public record Overview(boolean enabled, boolean official, Source recordSource, String recordEra,
                           Instant nextCollectionAt, List<LocalDate> weeks, int page, long totalWeeks, Result result) {}
    public record WarScore(Integer medals, Integer decks) {}
}
