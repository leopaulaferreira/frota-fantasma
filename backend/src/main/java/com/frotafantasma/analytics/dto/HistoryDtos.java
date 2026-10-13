package com.frotafantasma.analytics.dto;

import com.frotafantasma.analytics.dto.clash.RiverRaceDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public final class HistoryDtos {
    private HistoryDtos() {}
    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public static <S, T> PageResponse<T> from(Page<S> source, Function<S, T> mapper) {
            return new PageResponse<>(source.getContent().stream().map(mapper).toList(), source.getNumber(),
                    source.getSize(), source.getTotalElements(), source.getTotalPages());
        }
    }
    public record ClanSnapshotResponse(Long id, UUID runId, Instant capturedAt, String name, String description,
            String type, Integer clanScore, Integer clanWarTrophies, Integer requiredTrophies,
            Integer donationsPerWeek, Integer memberCount) {}
    public record PlayerResponse(Long id, String playerTag, String name, Instant firstSeenAt, Instant lastSeenAt,
            boolean active, String currentRole) {}
    public record MembershipResponse(Long id, UUID runId, Instant capturedAt, String name, String role,
            Integer trophies, Integer clanRank, Integer previousClanRank, Integer donations, Integer donationsReceived, Instant apiLastSeen) {}
    public record EventResponse(Long id, String playerTag, String type, String previousRole, String currentRole,
            Instant observedAt, UUID runId) {}
    public record WarSummary(Long id, Integer seasonId, Integer sectionIndex, Instant publishedAt, Instant startDate,
            Instant endDate, Integer clanPlacement, Integer clanScore, Integer fame, Integer clanTrophyChange, Instant capturedAt) {}
    public record ParticipantResponse(String playerTag, String name, Integer contribution, Integer decksUsed,
            Integer decksUsedToday, Integer boatAttacks, Integer repairPoints, Integer battlesPlayed,
            Integer wins, Integer losses, Instant capturedAt) {}
    public record WarDetail(WarSummary war, List<ParticipantResponse> participants) {}
    public record PlayerWarResponse(WarSummary war, ParticipantResponse participation) {}
    public record RiverSummary(Long id, UUID runId, Instant capturedAt, Integer sectionIndex, Integer periodIndex,
            String periodType, String state) {}
    public record RiverResponse(RiverSummary snapshot, RiverRaceDto race) {}
}
