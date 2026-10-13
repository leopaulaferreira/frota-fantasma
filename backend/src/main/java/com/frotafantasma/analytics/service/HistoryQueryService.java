package com.frotafantasma.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.dto.HistoryDtos.*;
import com.frotafantasma.analytics.dto.CollectionResult;
import com.frotafantasma.analytics.dto.clash.RiverRaceDto;
import com.frotafantasma.analytics.entity.*;
import com.frotafantasma.analytics.mapper.HistoryMapper;
import com.frotafantasma.analytics.repository.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HistoryQueryService {
    private final ClanDataRepository clans;
    private final PlayerRepository players;
    private final ClanHistoryRepository clanHistory;
    private final MembershipHistoryRepository memberships;
    private final MembershipEventRepository events;
    private final RiverRaceSnapshotRepository rivers;
    private final WarRepository wars;
    private final WarParticipantRepository participants;
    private final CollectionRunRepository runs;
    private final ClashRoyaleProperties api;
    private final ObjectMapper json;

    public HistoryQueryService(ClanDataRepository clans, PlayerRepository players, ClanHistoryRepository clanHistory,
            MembershipHistoryRepository memberships, MembershipEventRepository events, RiverRaceSnapshotRepository rivers,
            WarRepository wars, WarParticipantRepository participants, CollectionRunRepository runs,
            ClashRoyaleProperties api, ObjectMapper json) {
        this.clans = clans; this.players = players; this.clanHistory = clanHistory; this.memberships = memberships;
        this.events = events; this.rivers = rivers; this.wars = wars; this.participants = participants;
        this.runs = runs; this.api = api; this.json = json;
    }
    public Clan clan() { return clans.findByTag(api.clanTag()).orElseThrow(this::notFound); }
    public Player player(String tag) { return players.findByPlayerTag(tag).orElseThrow(this::notFound); }
    public PageResponse<ClanSnapshotResponse> clanHistory(int page, int size) {
        return PageResponse.from(clanHistory.findByClanIdOrderByCapturedAtDescIdDesc(clan().getId(), PageRequest.of(page, size)), HistoryMapper::clanSnapshot);
    }
    public PageResponse<MembershipResponse> memberships(String tag, int page, int size) {
        return PageResponse.from(memberships.findByPlayerIdOrderByCapturedAtDescIdDesc(player(tag).getId(), PageRequest.of(page, size)), HistoryMapper::membership);
    }
    public PageResponse<EventResponse> events(String tag, int page, int size) {
        Page<MembershipEvent> data = tag == null
                ? events.findByClanIdOrderByObservedAtDescIdDesc(clan().getId(), PageRequest.of(page, size))
                : events.findByPlayerIdOrderByObservedAtDescIdDesc(player(tag).getId(), PageRequest.of(page, size));
        Map<Long, Player> names = playerMap(data.stream().map(MembershipEvent::getPlayerId).toList());
        return PageResponse.from(data, e -> HistoryMapper.event(e, names.get(e.getPlayerId()).getPlayerTag()));
    }
    public PageResponse<WarSummary> wars(int page, int size) {
        return PageResponse.from(wars.findByClanIdOrderByPublishedAtDescIdDesc(clan().getId(), PageRequest.of(page, size)), HistoryMapper::war);
    }
    public WarDetail war(Long id) {
        War war = wars.findById(id).filter(w -> w.getClanId().equals(clan().getId())).orElseThrow(this::notFound);
        var data = participants.findByWarIdOrderByContributionDesc(id);
        Map<Long, Player> names = playerMap(data.stream().map(WarParticipant::getPlayerId).toList());
        return new WarDetail(HistoryMapper.war(war), data.stream().map(p -> HistoryMapper.participant(p, names.get(p.getPlayerId()).getPlayerTag())).toList());
    }
    public PageResponse<PlayerWarResponse> playerWars(String tag, int page, int size) {
        Player player = player(tag);
        var data = participants.findHistoryByPlayerId(player.getId(), PageRequest.of(page, size));
        var matches = wars.findAllById(data.stream().map(WarParticipant::getWarId).toList()).stream()
                .collect(Collectors.toMap(War::getId, w -> w));
        return PageResponse.from(data, p -> new PlayerWarResponse(HistoryMapper.war(matches.get(p.getWarId())), HistoryMapper.participant(p, tag)));
    }
    public RiverResponse currentRiver() {
        var snapshot = rivers.findFirstByClanIdOrderByCapturedAtDescIdDesc(clan().getId()).orElseThrow(this::notFound);
        return river(snapshot);
    }
    public RiverResponse river(Long id) {
        return river(rivers.findById(id).filter(r -> r.getClanId().equals(clan().getId())).orElseThrow(this::notFound));
    }
    private RiverResponse river(RiverRaceSnapshot snapshot) {
        return new RiverResponse(HistoryMapper.river(snapshot), json.convertValue(snapshot.getPayload(), RiverRaceDto.class));
    }
    public PageResponse<RiverSummary> riverHistory(int page, int size) {
        return PageResponse.from(rivers.findByClanIdOrderByCapturedAtDescIdDesc(clan().getId(), PageRequest.of(page, size)), HistoryMapper::river);
    }
    public PageResponse<CollectionResult> runs(int page, int size) {
        return PageResponse.from(runs.findByClanTagOrderByStartedAtDesc(api.clanTag(), PageRequest.of(page, size)), HistoryMapper::run);
    }
    private Map<Long, Player> playerMap(List<Long> ids) {
        return players.findAllById(ids).stream().collect(Collectors.toMap(Player::getId, p -> p));
    }
    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Dados ainda não coletados ou registro não encontrado.");
    }
}
