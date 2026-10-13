package com.frotafantasma.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frotafantasma.analytics.client.ClashRoyaleException;
import com.frotafantasma.analytics.dto.clash.*;
import com.frotafantasma.analytics.entity.*;
import com.frotafantasma.analytics.repository.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoryStore {
    private final ClanDataRepository clans;
    private final PlayerRepository players;
    private final ClanHistoryRepository clanHistory;
    private final MembershipHistoryRepository memberships;
    private final MembershipEventRepository events;
    private final RiverRaceSnapshotRepository rivers;
    private final WarRepository wars;
    private final WarParticipantRepository participants;
    private final ObjectMapper json;
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSX").withZone(ZoneOffset.UTC);

    public HistoryStore(ClanDataRepository clans, PlayerRepository players, ClanHistoryRepository clanHistory,
                        MembershipHistoryRepository memberships, MembershipEventRepository events,
                        RiverRaceSnapshotRepository rivers, WarRepository wars, WarParticipantRepository participants,
                        ObjectMapper json) {
        this.clans = clans; this.players = players; this.clanHistory = clanHistory;
        this.memberships = memberships; this.events = events; this.rivers = rivers;
        this.wars = wars; this.participants = participants; this.json = json;
    }

    @Transactional
    public int saveMembers(UUID runId, String clanTag, ClanApiDto source, List<ClanMemberApiDto> members, Instant capturedAt) {
        require(source != null && clanTag.equals(source.tag()) && source.members() != null
                && members != null && source.members() == members.size());
        Set<String> tags = new HashSet<>();
        for (ClanMemberApiDto member : members) {
            require(member != null && validTag(member.tag()) && tags.add(member.tag()));
        }
        Clan previous = clans.findByTag(clanTag).orElse(null);
        boolean baseline = previous == null || !previous.getBaselineEstablished();
        Clan clan = clans.save(new Clan(previous == null ? null : previous.getId(), clanTag, source.name(),
                source.description(), source.type(), source.clanScore(), source.clanWarTrophies(), source.requiredTrophies(),
                source.donationsPerWeek(), source.members(), capturedAt, true));
        Map<String, Player> previousMembers = players.findByClanIdAndActiveTrue(clan.getId()).stream()
                .collect(Collectors.toMap(Player::getPlayerTag, Function.identity()));
        clanHistory.save(new ClanHistory(null, clan.getId(), runId, source.name(), source.description(), source.type(),
                source.clanScore(), source.clanWarTrophies(), source.requiredTrophies(), source.donationsPerWeek(),
                source.members(), capturedAt));
        for (ClanMemberApiDto member : members) {
            Player old = players.findByPlayerTag(member.tag()).orElse(null);
            boolean present = previousMembers.containsKey(member.tag());
            String previousRole = old == null ? null : old.getCurrentRole();
            Player player = players.save(new Player(old == null ? null : old.getId(), member.tag(), member.name(), clan.getId(),
                    old == null || old.getFirstSeenAt() == null ? capturedAt : old.getFirstSeenAt(),
                    capturedAt, true, member.role()));
            if (!present) {
                events.save(new MembershipEvent(null, player.getId(), clan.getId(), runId,
                        baseline ? "BASELINE" : "JOIN", null, member.role(), capturedAt));
            } else if (!Objects.equals(previousRole, member.role())) {
                events.save(new MembershipEvent(null, player.getId(), clan.getId(), runId,
                        "ROLE_CHANGE", previousRole, member.role(), capturedAt));
            }
            var snapshot = new ClanMembershipSnapshot(null, player.getId(), clan.getId(), runId,
                    member.name(), member.role(), member.trophies(), member.clanRank(), member.previousClanRank(),
                    member.donations(), member.donationsReceived(), member.expLevel(), capturedAt);
            snapshot.setApiLastSeen(member.lastSeen() == null ? null : publishedAt(member.lastSeen()));
            memberships.save(snapshot);
        }
        for (Player old : previousMembers.values()) {
            if (!tags.contains(old.getPlayerTag())) {
                players.save(new Player(old.getId(), old.getPlayerTag(), old.getName(), clan.getId(),
                        old.getFirstSeenAt(), old.getLastSeenAt(), false, old.getCurrentRole()));
                events.save(new MembershipEvent(null, old.getId(), clan.getId(), runId,
                        "LEAVE", old.getCurrentRole(), null, capturedAt));
            }
        }
        return members.size();
    }

    @Transactional
    public int saveRiver(UUID runId, String clanTag, RiverRaceDto source, Instant capturedAt) {
        require(source != null && source.clan() != null && clanTag.equals(source.clan().tag())
                && source.sectionIndex() != null && source.periodIndex() != null);
        validateParticipants(source.clan().participants());
        Clan clan = getOrCreateClan(clanTag, source.clan().name());
        rivers.save(new RiverRaceSnapshot(null, clan.getId(), runId, source.sectionIndex(), source.periodIndex(),
                source.periodType(), source.state(), capturedAt, json.valueToTree(source)));
        return source.clan().participants().size();
    }

    @Transactional
    public int saveWarLog(String clanTag, List<RiverLogEntryDto> entries, Instant capturedAt) {
        require(entries != null);
        Set<String> keys = new HashSet<>();
        for (RiverLogEntryDto entry : entries) {
            require(entry != null && entry.seasonId() != null && entry.sectionIndex() != null
                    && entry.seasonId() >= 0 && entry.sectionIndex() >= 0
                    && keys.add(entry.seasonId() + ":" + entry.sectionIndex()));
            publishedAt(entry.createdDate());
            validateParticipants(ownStanding(clanTag, entry).clan().participants());
        }
        int changed = 0;
        for (RiverLogEntryDto entry : entries) {
            RiverStandingDto standing = ownStanding(clanTag, entry);
            RiverClanDto source = standing.clan();
            Clan clan = getOrCreateClan(clanTag, source.name());
            War old = wars.findByClanIdAndSeasonIdAndSectionIndex(clan.getId(), entry.seasonId(), entry.sectionIndex()).orElse(null);
            var payload = json.valueToTree(entry);
            if (old != null && payload.equals(old.getPayload())) continue;
            War war = wars.save(new War(old == null ? null : old.getId(), clan.getId(), entry.seasonId(), entry.sectionIndex(),
                    publishedAt(entry.createdDate()), null, null, standing.rank(), source.clanScore(), source.fame(),
                    standing.trophyChange(), capturedAt, payload));
            participants.deleteByWarId(war.getId());
            participants.flush();
            for (RiverParticipantDto member : source.participants()) {
                Player player = players.findByPlayerTag(member.tag()).orElseGet(() ->
                        players.save(new Player(null, member.tag(), member.name(), null, null, null, false, null)));
                participants.save(new WarParticipant(null, war.getId(), player.getId(), member.name(), member.fame(),
                        member.decksUsed(), member.decksUsedToday(), member.boatAttacks(), member.repairPoints(),
                        null, null, null, capturedAt));
            }
            changed++;
        }
        return changed;
    }

    private Clan getOrCreateClan(String tag, String name) {
        return clans.findByTag(tag).orElseGet(() -> clans.save(
                new Clan(null, tag, name, null, null, null, null, null, null, null, null, false)));
    }

    private RiverStandingDto ownStanding(String tag, RiverLogEntryDto entry) {
        require(entry.standings() != null);
        var matches = entry.standings().stream().filter(s -> s != null && s.clan() != null && tag.equals(s.clan().tag())).toList();
        require(matches.size() == 1);
        return matches.getFirst();
    }

    private Instant publishedAt(String value) {
        try {
            Instant parsed = Instant.from(API_DATE.parse(value));
            require(parsed.isAfter(Instant.EPOCH));
            return parsed;
        } catch (java.time.DateTimeException | NullPointerException exception) {
            throw invalid();
        }
    }

    private void validateParticipants(List<RiverParticipantDto> values) {
        require(values != null);
        Set<String> tags = new HashSet<>();
        for (RiverParticipantDto participant : values) {
            require(participant != null && validTag(participant.tag()) && tags.add(participant.tag())
                    && (participant.decksUsed() == null || participant.decksUsed() >= 0));
        }
    }

    private boolean validTag(String value) { return value != null && value.matches("#[A-Z0-9]{3,31}"); }
    private void require(boolean condition) { if (!condition) throw invalid(); }
    private ClashRoyaleException invalid() {
        return new ClashRoyaleException(HttpStatus.BAD_GATEWAY, "INVALID_UPSTREAM_RESPONSE",
                "A coleta está incompleta ou inconsistente. O histórico anterior foi preservado.");
    }
}
