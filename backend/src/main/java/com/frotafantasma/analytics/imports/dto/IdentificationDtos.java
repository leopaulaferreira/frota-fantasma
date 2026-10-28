package com.frotafantasma.analytics.imports.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class IdentificationDtos {
    private IdentificationDtos() {}
    public record Participant(String tag, Integer fame, Integer decks, Integer boats, Integer repairs) {}
    public record Target(String kind, long id, Integer seasonId, Integer sectionIndex,
                         Integer periodIndex, String periodType, Instant officialAt, String clanTag, List<Participant> participants) {}
    public record Candidate(String key, String kind, long targetId, Integer seasonId, Integer sectionIndex,
                            Integer periodIndex, String periodType, Instant officialAt, String fingerprint,
                            boolean equalFingerprint, int matchingTags, int identicalFame, int identicalDecks,
                            int identicalRows, int officialGreaterValues, int incompatibleValues, int unknownValues,
                            int csvOnlyTags, int officialOnlyTags, List<String> evidence) {}
    public record Resolution(long revision, UUID batchId, String status, String resolvedBy, Instant resolvedAt,
                             String resolutionMethod, String targetKind, Long targetId, Integer seasonId, Integer sectionIndex,
                             Instant capturedAt, String timezone, String reason, boolean manuallyConfirmed, JsonNode evidence) {}
    public record Identification(UUID batchId, String format, String filename, String clanTag, String clanName,
                                 String match, String fingerprint, List<Candidate> candidates, List<String> warnings,
                                 LocalDateTime suggestedCapturedAt, String suggestedTimezone,
                                 String evidenceVersion, List<Resolution> history) {}
    public record Resolve(@NotNull UUID requestKey, @Min(0) long expectedRevision,
                          @NotBlank String evidenceVersion, @NotBlank String action,
                          String candidateKey, String clanTag, LocalDateTime capturedAtLocal, String timezone,
                          String timestampOrigin, boolean confirmed,
                          @NotBlank @Size(max=2000) String reason) {}
}
