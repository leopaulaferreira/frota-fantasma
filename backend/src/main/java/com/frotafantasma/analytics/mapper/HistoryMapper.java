package com.frotafantasma.analytics.mapper;

import com.frotafantasma.analytics.dto.*;
import com.frotafantasma.analytics.dto.HistoryDtos.*;
import com.frotafantasma.analytics.entity.*;

public final class HistoryMapper {
    private HistoryMapper() {}
    public static ClanResponse clan(Clan c) {
        return new ClanResponse(c.getTag(), c.getName(), c.getDescription(), c.getType(), c.getClanScore(),
                c.getClanWarTrophies(), c.getRequiredTrophies(), c.getDonationsPerWeek(), c.getMemberCount());
    }
    public static ClanMemberResponse member(ClanMembershipSnapshot m, String tag) {
        return new ClanMemberResponse(tag, m.getName(), m.getRole(), m.getExpLevel(), m.getTrophies(),
                m.getClanRank(), m.getPreviousClanRank(), m.getDonations(), m.getDonationsReceived());
    }
    public static ClanSnapshotResponse clanSnapshot(ClanHistory c) {
        return new ClanSnapshotResponse(c.getId(), c.getRunId(), c.getCapturedAt(), c.getName(), c.getDescription(),
                c.getType(), c.getClanScore(), c.getClanWarTrophies(), c.getRequiredTrophies(), c.getDonationsPerWeek(), c.getMemberCount());
    }
    public static PlayerResponse player(Player p) {
        return new PlayerResponse(p.getId(), p.getPlayerTag(), p.getName(), p.getFirstSeenAt(), p.getLastSeenAt(), p.getActive(), p.getCurrentRole());
    }
    public static MembershipResponse membership(ClanMembershipSnapshot m) {
        return new MembershipResponse(m.getId(), m.getRunId(), m.getCapturedAt(), m.getName(), m.getRole(), m.getTrophies(),
                m.getClanRank(), m.getPreviousClanRank(), m.getDonations(), m.getDonationsReceived(), m.getApiLastSeen());
    }
    public static EventResponse event(MembershipEvent e, String tag) {
        return new EventResponse(e.getId(), tag, e.getType(), e.getPreviousRole(), e.getCurrentRole(), e.getObservedAt(), e.getRunId());
    }
    public static WarSummary war(War w) {
        return new WarSummary(w.getId(), w.getSeasonId(), w.getSectionIndex(), w.getPublishedAt(), w.getStartDate(),
                w.getEndDate(), w.getClanPlacement(), w.getClanScore(), w.getFame(), w.getClanTrophyChange(), w.getCapturedAt());
    }
    public static ParticipantResponse participant(WarParticipant p, String tag) {
        return new ParticipantResponse(tag, p.getName(), p.getContribution(), p.getDecksUsed(), p.getDecksUsedToday(),
                p.getBoatAttacks(), p.getRepairPoints(), p.getBattlesPlayed(), p.getWins(), p.getLosses(), p.getCapturedAt());
    }
    public static RiverSummary river(RiverRaceSnapshot r) {
        return new RiverSummary(r.getId(), r.getRunId(), r.getCapturedAt(), r.getSectionIndex(), r.getPeriodIndex(), r.getPeriodType(), r.getState());
    }
    public static CollectionResult run(CollectionRun r) {
        return new CollectionResult(r.getId(), r.getKind(), r.getStatus(), r.getStartedAt(), r.getCompletedAt(), r.getRecordCount(), r.getErrorCode());
    }
}
