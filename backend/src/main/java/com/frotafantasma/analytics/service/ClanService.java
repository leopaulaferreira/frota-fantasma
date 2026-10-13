package com.frotafantasma.analytics.service;

import com.frotafantasma.analytics.dto.ClanMemberResponse;
import com.frotafantasma.analytics.dto.ClanResponse;
import com.frotafantasma.analytics.entity.Player;
import com.frotafantasma.analytics.mapper.HistoryMapper;
import com.frotafantasma.analytics.repository.ClanHistoryRepository;
import com.frotafantasma.analytics.repository.MembershipHistoryRepository;
import com.frotafantasma.analytics.repository.PlayerRepository;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ClanService {
    private final HistoryQueryService history;
    private final ClanHistoryRepository clans;
    private final MembershipHistoryRepository memberships;
    private final PlayerRepository players;

    public ClanService(HistoryQueryService history, ClanHistoryRepository clans,
                       MembershipHistoryRepository memberships, PlayerRepository players) {
        this.history = history; this.clans = clans; this.memberships = memberships; this.players = players;
    }
    public ClanResponse getClan() {
        var clan = history.clan();
        if (clan.getSnapshotDate() == null) throw notCollected();
        return HistoryMapper.clan(clan);
    }
    public List<ClanMemberResponse> getMembers() {
        var last = clans.findFirstByClanIdOrderByCapturedAtDescIdDesc(history.clan().getId()).orElseThrow(this::notCollected);
        var data = memberships.findByRunId(last.getRunId());
        var names = players.findAllById(data.stream().map(m -> m.getPlayerId()).toList()).stream()
                .collect(Collectors.toMap(Player::getId, Player::getPlayerTag));
        return data.stream().map(m -> HistoryMapper.member(m, names.get(m.getPlayerId())))
                .sorted(Comparator.comparing(ClanMemberResponse::clanRank, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
    private ResponseStatusException notCollected() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Dados do clã ainda não coletados.");
    }
}
