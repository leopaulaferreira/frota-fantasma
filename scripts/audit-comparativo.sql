BEGIN READ ONLY;
WITH clan_scope AS (SELECT id FROM clan WHERE tag='#RQJGC9GV'),
last_roster AS (SELECT * FROM clan_history WHERE clan_id IN (SELECT id FROM clan_scope) ORDER BY captured_at DESC,id DESC LIMIT 1),
roster AS (
 SELECT p.player_tag AS tag,m.name FROM clan_membership_history m JOIN player p ON p.id=m.player_id
 WHERE m.run_id=(SELECT run_id FROM last_roster)
),
events AS (
 SELECT e.id,p.player_tag AS "playerTag",e.type,e.observed_at AS "observedAt"
 FROM membership_event e JOIN player p ON p.id=e.player_id WHERE e.clan_id IN (SELECT id FROM clan_scope)
),
wars AS (
 SELECT w.id,w.season_id AS "seasonId",w.section_index AS "sectionIndex",w.published_at AS "publishedAt",
 w.start_date AS "startDate",w.end_date AS "endDate",w.captured_at AS "capturedAt",w.source,w.fame,w.clan_score AS "clanScore"
 FROM effective_war w WHERE w.clan_id IN (SELECT id FROM clan_scope)
),
participants AS (
 SELECT wp.war_id,p.player_tag AS "playerTag",wp.name,wp.contribution,wp.decks_used AS "decksUsed",wp.source
 FROM effective_war_participant wp JOIN player p ON p.id=wp.player_id WHERE wp.war_id IN (SELECT id FROM wars)
)
SELECT jsonb_build_object(
 'at',now(),'rosterCapturedAt',(SELECT captured_at FROM last_roster),
 'roster',(SELECT jsonb_agg(to_jsonb(r)) FROM roster r),
 'events',(SELECT jsonb_agg(to_jsonb(e)) FROM events e),
 'details',(SELECT jsonb_agg(jsonb_build_object('war',to_jsonb(w),'participants',
 COALESCE((SELECT jsonb_agg(to_jsonb(p)-'war_id') FROM participants p WHERE p.war_id=w.id),'[]'::jsonb))) FROM wars w)
);
ROLLBACK;
