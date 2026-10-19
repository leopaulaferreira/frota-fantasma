package com.frotafantasma.analytics.weeklyreview;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** A human review of this week's observed race, never an automatic removal. */
public final class WeeklyReviewEngine {
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    public static final int POINTS_TARGET = 1600;
    public static final int OFFLINE_DAYS = 5;
    public static final Duration MAX_AGE = Duration.ofHours(1);
    public record Member(String tag, String name, String role, Instant lastSeen, Instant observedSince) {}
    public record Race(Instant capturedAt, Long snapshotId, String periodType, JsonNode payload) {}
    public record Row(String tag, String name, String role, Integer points, Integer decksUsed, Instant lastSeen,
                      Long offlineSeconds, Instant observedSince, boolean belowPoints, boolean offlineExceeded,
                      String status, List<String> missingEvidence) {}
    public record Report(LocalDate week, Instant generatedAt, Instant cutoff, Instant deadline, String timezone,
                         String phase, int pointsTarget, int offlineDays, boolean collectionEnabled,
                         Instant membersCapturedAt, Instant riverCapturedAt, Long riverSnapshotId,
                         boolean membersFresh, boolean riverFresh, boolean rosterComplete, List<Row> members) {}
    private WeeklyReviewEngine() {}
    public static LocalDate monday(Instant now) {
        return now.atZone(ZONE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    public static LocalDate defaultWeek(Instant now) {
        var date = now.atZone(ZONE).toLocalDate();
        return date.getDayOfWeek().getValue() < 4 ? monday(now).minusWeeks(1) : monday(now);
    }
    public static Instant deadline(LocalDate week) { return week.plusDays(6).atTime(22, 0).atZone(ZONE).toInstant(); }
    public static Instant cutoff(LocalDate week, Instant now) { return now.isBefore(deadline(week)) ? now : deadline(week); }
    public static boolean fresh(Instant captured, Instant cutoff) {
        return captured != null && !captured.isAfter(cutoff) && !captured.isBefore(cutoff.minus(MAX_AGE));
    }
    public static Report analyze(LocalDate week, Instant now, boolean enabled, Instant membersAt,
                                 Integer expectedMembers, List<Member> members, Race race) {
        Instant deadline = deadline(week), cutoff = cutoff(week, now);
        boolean rosterComplete = expectedMembers != null && expectedMembers == members.size();
        boolean membersFresh = fresh(membersAt, cutoff) && rosterComplete;
        Instant warWeekStart = week.plusDays(3).atStartOfDay(ZONE).toInstant();
        boolean riverFresh = race != null && fresh(race.capturedAt(), cutoff)
                && !race.capturedAt().isBefore(warWeekStart) && "warDay".equalsIgnoreCase(race.periodType());
        var participants = new HashMap<String, JsonNode>();
        if (race != null && race.payload() != null) {
            for (var p : race.payload().path("clan").path("participants")) participants.put(p.path("tag").asText(), p);
        }
        var rows = new ArrayList<Row>();
        for (var member : members) {
            JsonNode p = participants.get(member.tag());
            Integer points = riverFresh ? number(p, "fame") : null;
            Integer decks = riverFresh ? number(p, "decksUsed") : null;
            boolean activityValid = member.lastSeen() != null && membersAt != null && !member.lastSeen().isAfter(membersAt);
            Long offline = activityValid ? Duration.between(member.lastSeen(), membersAt).getSeconds() : null;
            boolean below = membersFresh && points != null && points < POINTS_TARGET;
            boolean inactive = membersFresh && activityValid && Duration.between(member.lastSeen(), membersAt).compareTo(Duration.ofDays(OFFLINE_DAYS)) > 0;
            var missing = new ArrayList<String>();
            if (!membersFresh) missing.add("Elenco ausente, incompleto ou coletado há mais de uma hora em relação ao recorte.");
            if (!riverFresh) missing.add("Sem coleta recente de dia de guerra desta semana até o horário do recorte.");
            else if (points == null) missing.add("Pontuação não informada para este membro; ausência de registro não equivale a zero.");
            if (!activityValid) missing.add("Última atividade oficial ausente ou inválida.");
            rows.add(new Row(member.tag(), member.name(), member.role(), points, decks, member.lastSeen(), offline,
                    member.observedSince(), below, inactive, below || inactive ? "REVIEW" : missing.isEmpty() ? "CLEAR" : "UNKNOWN", List.copyOf(missing)));
        }
        rows.sort(Comparator.comparingInt((Row r) -> switch (r.status()) { case "REVIEW" -> 0; case "UNKNOWN" -> 1; default -> 2; })
                .thenComparing(Comparator.comparingInt((Row r) -> (r.belowPoints() ? 1 : 0) + (r.offlineExceeded() ? 1 : 0)).reversed())
                .thenComparing(Row::points, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Row::offlineSeconds, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Row::tag));
        return new Report(week, now, cutoff, deadline, ZONE.toString(), now.isBefore(deadline) ? "LIVE" : "CLOSED",
                POINTS_TARGET, OFFLINE_DAYS, enabled, membersAt, race == null ? null : race.capturedAt(), race == null ? null : race.snapshotId(),
                membersFresh, riverFresh, rosterComplete, List.copyOf(rows));
    }
    private static Integer number(JsonNode p, String key) {
        if (p == null || !p.path(key).isIntegralNumber() || !p.path(key).canConvertToInt()) return null;
        int n = p.path(key).intValue(); return n >= 0 ? n : null;
    }
}
