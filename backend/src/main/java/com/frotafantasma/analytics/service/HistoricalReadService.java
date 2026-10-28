package com.frotafantasma.analytics.service;

import com.frotafantasma.analytics.entity.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One effective history for analytics, profiles and comparison; official identity wins. */
@Service
@Transactional(readOnly=true)
public class HistoricalReadService {
    private final EntityManager entities;
    public HistoricalReadService(EntityManager entities) { this.entities=entities; }
    @SuppressWarnings("unchecked")
    public List<War> wars(long clanId) {
        return entities.createNativeQuery("select * from effective_war where clan_id=:clan order by published_at desc,id desc",War.class).setParameter("clan",clanId).getResultList();
    }
    @SuppressWarnings("unchecked")
    public List<WarParticipant> participants(long clanId) {
        return entities.createNativeQuery("select p.* from effective_war_participant p join effective_war w on w.id=p.war_id where w.clan_id=:clan",WarParticipant.class).setParameter("clan",clanId).getResultList();
    }
    @SuppressWarnings("unchecked")
    public List<ClanMembershipSnapshot> members(long clanId) {
        return entities.createNativeQuery("select * from effective_membership_history where clan_id=:clan order by captured_at,id",ClanMembershipSnapshot.class).setParameter("clan",clanId).getResultList();
    }
}
