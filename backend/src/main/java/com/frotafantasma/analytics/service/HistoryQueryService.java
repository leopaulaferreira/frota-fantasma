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
    private final HistoricalReadService history;

    public HistoryQueryService(ClanDataRepository clans, PlayerRepository players, ClanHistoryRepository clanHistory,
            MembershipHistoryRepository memberships, MembershipEventRepository events, RiverRaceSnapshotRepository rivers,
            WarRepository wars, WarParticipantRepository participants, CollectionRunRepository runs,
            ClashRoyaleProperties api, ObjectMapper json, HistoricalReadService history) {
        this.clans = clans; this.players = players; this.clanHistory = clanHistory; this.memberships = memberships;
        this.events = events; this.rivers = rivers; this.wars = wars; this.participants = participants;
        this.runs = runs; this.api = api; this.json = json; this.history=history;
    }
    public Clan clan() { return clans.findByTag(api.clanTag()).orElseThrow(this::notFound); }
    public Player player(String tag) { return players.findByPlayerTag(tag).orElseThrow(this::notFound); }
    public PageResponse<ClanSnapshotResponse> clanHistory(int page, int size) {
        return PageResponse.from(clanHistory.findByClanIdOrderByCapturedAtDescIdDesc(clan().getId(), PageRequest.of(page, size)), HistoryMapper::clanSnapshot);
    }
    public PageResponse<MembershipResponse> memberships(String tag, int page, int size) {
        long id=player(tag).getId();
        return page(history.members(clan().getId()).stream().filter(m->m.getPlayerId()==id).sorted(Comparator.comparing(ClanMembershipSnapshot::getCapturedAt).reversed()).map(HistoryMapper::membership).toList(),page,size);
    }
    public PageResponse<EventResponse> events(String tag, int page, int size) {
        Page<MembershipEvent> data = tag == null
                ? events.findByClanIdOrderByObservedAtDescIdDesc(clan().getId(), PageRequest.of(page, size))
                : events.findByPlayerIdOrderByObservedAtDescIdDesc(player(tag).getId(), PageRequest.of(page, size));
        Map<Long, Player> names = playerMap(data.stream().map(MembershipEvent::getPlayerId).toList());
        return PageResponse.from(data, e -> HistoryMapper.event(e, names.get(e.getPlayerId()).getPlayerTag()));
    }
    public PageResponse<WarSummary> wars(int page, int size) {
        return page(history.wars(clan().getId()).stream().map(HistoryMapper::war).toList(),page,size);
    }
    public WarDetail war(Long id) {
        War war = history.wars(clan().getId()).stream().filter(w->w.getId().equals(id)).findFirst().orElseThrow(this::notFound);
        var data = history.participants(clan().getId()).stream().filter(p->p.getWarId().equals(id)).sorted(Comparator.comparing(WarParticipant::getContribution,Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        Map<Long, Player> names = playerMap(data.stream().map(WarParticipant::getPlayerId).toList());
        return new WarDetail(HistoryMapper.war(war), data.stream().map(p -> HistoryMapper.participant(p, names.get(p.getPlayerId()).getPlayerTag())).toList());
    }
    public PageResponse<PlayerWarResponse> playerWars(String tag, int page, int size) {
        Player player = player(tag);
        var warsById=history.wars(clan().getId()).stream().collect(Collectors.toMap(War::getId,w->w));
        var data=history.participants(clan().getId()).stream().filter(p->p.getPlayerId().equals(player.getId()))
                .sorted(Comparator.comparing((WarParticipant p)->warsById.get(p.getWarId()).getPublishedAt()).reversed())
                .map(p->new PlayerWarResponse(HistoryMapper.war(warsById.get(p.getWarId())),HistoryMapper.participant(p,tag))).toList();
        return page(data,page,size);
    }
    private <T> PageResponse<T> page(List<T> data,int page,int size) {
        int start=(int)Math.min((long)page*size,data.size()),end=Math.min(start+size,data.size());
        return new PageResponse<>(data.subList(start,end),page,size,data.size(),(data.size()+size-1)/size);
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
