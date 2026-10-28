package com.frotafantasma.analytics.imports.service;

import com.frotafantasma.analytics.imports.dto.IdentificationDtos.*;
import com.frotafantasma.analytics.imports.parser.RoyaleApiCsvParser;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Comparisons are evidence proposals, never writes to official history. */
@Component
public class RiverIdentityMatcher {
    public record Matches(String match, String fingerprint, List<Candidate> candidates, List<String> warnings) {}
    public Matches match(String clanTag, List<Participant> imported, List<Target> targets) {
        String clan = RoyaleApiCsvParser.tag(clanTag);
        var rows = normalize(imported);
        if (clan == null || rows == null || rows.isEmpty())
            return new Matches("UNRESOLVED", null, List.of(), List.of("Tags ausentes/duplicadas ou conjunto de participantes vazio. Corrija a origem antes de identificar."));
        String fingerprint = fingerprint(clan, rows);
        var candidates = new ArrayList<Candidate>();
        for (Target target : targets) {
            if (!clan.equals(RoyaleApiCsvParser.tag(target.clanTag()))) continue;
            var official = normalize(target.participants());
            if (official == null || official.isEmpty()) continue;
            int tags = 0, fame = 0, decks = 0, identical = 0, greater = 0, incompatible = 0, unknown = 0, compared = 0;
            var evidence = new ArrayList<String>();
            for (var row : rows.values()) {
                var other = official.get(row.tag());
                if (other == null) { evidence.add(row.tag() + ": ausente neste registro oficial; não significa zero."); continue; }
                tags++;
                Integer[] a = {row.fame(), row.decks(), row.boats(), row.repairs()};
                Integer[] b = {other.fame(), other.decks(), other.boats(), other.repairs()};
                String[] fields = {"fame", "decksUsed", "boatAttacks", "repairPoints"};
                boolean equal = true;
                for (int i = 0; i < a.length; i++) {
                    if (a[i] == null || b[i] == null || a[i] < 0 || b[i] < 0) { unknown++; equal = false; continue; }
                    compared++;
                    if (a[i].equals(b[i])) { if (i == 0) fame++; if (i == 1) decks++; }
                    else {
                        equal = false;
                        if (b[i] > a[i]) greater++; else incompatible++;
                        evidence.add(row.tag() + ": " + fields[i] + " CSV=" + a[i] + ", oficial=" + b[i]
                                + (b[i] > a[i] ? " (maior; compatibilidade, não prova de captura anterior)." : " (incompatível com progressão crescente)."));
                    }
                }
                if (equal) identical++;
            }
            // A single common player is not strong evidence in a larger export. No roster-size cap.
            if (tags < Math.min(3, rows.size()) || tags * 1.0 / rows.size() < 0.8 || compared == 0) continue;
            String officialFingerprint = fingerprint(clan, official);
            boolean informative = rows.values().stream().anyMatch(p -> p.fame() != null && p.fame() > 0 || p.decks() != null && p.decks() > 0);
            boolean exact = informative && unknown == 0 && rows.size() == official.size() && identical == rows.size()
                    && fingerprint.equals(officialFingerprint);
            candidates.add(new Candidate(target.kind() + ":" + target.id(), target.kind(), target.id(), target.seasonId(),
                    target.sectionIndex(), target.periodIndex(), target.periodType(), target.officialAt(), officialFingerprint,
                    exact, tags, fame, decks, identical, greater, incompatible, unknown, rows.size() - tags,
                    official.size() - tags, List.copyOf(evidence)));
        }
        candidates.sort(Comparator.comparing(Candidate::equalFingerprint).reversed()
                .thenComparingInt(Candidate::incompatibleValues)
                .thenComparing(Comparator.comparingInt(Candidate::matchingTags).reversed())
                .thenComparing(Comparator.comparingInt(Candidate::identicalRows).reversed())
                .thenComparing(Candidate::key));
        long exact = candidates.stream().filter(Candidate::equalFingerprint).count();
        String match = candidates.isEmpty() ? "UNRESOLVED" : exact == 1 ? "EXACT_MATCH" : "POSSIBLE_MATCH";
        return new Matches(match, fingerprint, List.copyOf(candidates), List.of(
                "Igualdade é avaliada entre os registros oficiais disponíveis; não comprova cobertura completa do histórico.",
                "Valores oficiais maiores podem ser compatíveis com uma captura anterior. O CSV não informa sua data; a ordem temporal não foi comprovada.",
                "Snapshots não informam seasonId. Associar um snapshot não identifica automaticamente uma guerra publicada.",
                "Identificação não incorpora dados às métricas nem substitui valores oficiais."));
    }
    private SortedMap<String,Participant> normalize(List<Participant> source) {
        var rows = new TreeMap<String,Participant>();
        for (var row : source) {
            String tag = RoyaleApiCsvParser.tag(row.tag());
            if (tag == null || rows.putIfAbsent(tag, new Participant(tag,row.fame(),row.decks(),row.boats(),row.repairs())) != null) return null;
        }
        return rows;
    }
    private String fingerprint(String clan, SortedMap<String,Participant> rows) {
        var text = new StringBuilder(clan);
        rows.values().forEach(p -> text.append('\n').append(p.tag()).append('|').append(p.fame()).append('|')
                .append(p.decks()).append('|').append(p.boats()).append('|').append(p.repairs()));
        return digest(text.toString());
    }
    public static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
