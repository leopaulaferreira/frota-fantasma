CREATE TABLE clan (
    id BIGSERIAL PRIMARY KEY, tag VARCHAR(32) NOT NULL UNIQUE, name VARCHAR(255),
    description TEXT, type VARCHAR(40), clan_score INTEGER, clan_war_trophies INTEGER,
    required_trophies INTEGER, donations_per_week INTEGER, member_count INTEGER,
    snapshot_date TIMESTAMPTZ, baseline_established BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE player (
    id BIGSERIAL PRIMARY KEY, player_tag VARCHAR(32) NOT NULL UNIQUE, name VARCHAR(255),
    clan_id BIGINT REFERENCES clan(id), first_seen_at TIMESTAMPTZ, last_seen_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT FALSE, role_current VARCHAR(40)
);
CREATE TABLE collection_run (
    id UUID PRIMARY KEY, clan_tag VARCHAR(32) NOT NULL, kind VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL, started_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ,
    error_code VARCHAR(100), record_count INTEGER, deduplication_key VARCHAR(255)
);
CREATE UNIQUE INDEX collection_success_key ON collection_run(deduplication_key) WHERE status = 'SUCCESS';
CREATE INDEX collection_recent ON collection_run(clan_tag, started_at DESC);
CREATE TABLE clan_history (
    id BIGSERIAL PRIMARY KEY, clan_id BIGINT NOT NULL REFERENCES clan(id),
    run_id UUID NOT NULL REFERENCES collection_run(id), name VARCHAR(255),
    description TEXT, type VARCHAR(40), clan_score INTEGER, clan_war_trophies INTEGER,
    required_trophies INTEGER, donations_per_week INTEGER, member_count INTEGER,
    captured_at TIMESTAMPTZ NOT NULL, UNIQUE(run_id, clan_id)
);
CREATE INDEX clan_history_recent ON clan_history(clan_id, captured_at DESC, id DESC);
CREATE TABLE clan_membership_history (
    id BIGSERIAL PRIMARY KEY, player_id BIGINT NOT NULL REFERENCES player(id),
    clan_id BIGINT NOT NULL REFERENCES clan(id), run_id UUID NOT NULL REFERENCES collection_run(id),
    name VARCHAR(255), role VARCHAR(40), trophies INTEGER, clan_rank INTEGER, previous_clan_rank INTEGER,
    donations INTEGER, donations_received INTEGER, exp_level INTEGER,
    captured_at TIMESTAMPTZ NOT NULL, UNIQUE(run_id, player_id)
);
CREATE INDEX membership_player_recent ON clan_membership_history(player_id, captured_at DESC, id DESC);
CREATE INDEX membership_clan_recent ON clan_membership_history(clan_id, captured_at DESC, id DESC);
CREATE TABLE membership_event (
    id BIGSERIAL PRIMARY KEY, player_id BIGINT NOT NULL REFERENCES player(id),
    clan_id BIGINT NOT NULL REFERENCES clan(id), run_id UUID NOT NULL REFERENCES collection_run(id),
    type VARCHAR(30) NOT NULL, previous_role VARCHAR(40), role_current VARCHAR(40),
    observed_at TIMESTAMPTZ NOT NULL, UNIQUE(run_id, player_id, type)
);
CREATE INDEX event_player_recent ON membership_event(player_id, observed_at DESC, id DESC);
CREATE INDEX event_clan_recent ON membership_event(clan_id, observed_at DESC, id DESC);
CREATE TABLE river_race_snapshot (
    id BIGSERIAL PRIMARY KEY, clan_id BIGINT NOT NULL REFERENCES clan(id),
    run_id UUID NOT NULL UNIQUE REFERENCES collection_run(id), section_index INTEGER,
    period_index INTEGER, period_type VARCHAR(50), state VARCHAR(50),
    captured_at TIMESTAMPTZ NOT NULL, payload JSONB NOT NULL
);
CREATE INDEX river_recent ON river_race_snapshot(clan_id, captured_at DESC, id DESC);
CREATE TABLE war (
    id BIGSERIAL PRIMARY KEY, clan_id BIGINT NOT NULL REFERENCES clan(id),
    season_id INTEGER NOT NULL, section_index INTEGER NOT NULL, published_at TIMESTAMPTZ NOT NULL,
    start_date TIMESTAMPTZ, end_date TIMESTAMPTZ, clan_placement INTEGER, clan_score INTEGER,
    fame INTEGER, clan_trophy_change INTEGER, captured_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL, UNIQUE(clan_id, season_id, section_index)
);
CREATE INDEX war_recent ON war(clan_id, published_at DESC, id DESC);
CREATE TABLE war_participant (
    id BIGSERIAL PRIMARY KEY, war_id BIGINT NOT NULL REFERENCES war(id),
    player_id BIGINT NOT NULL REFERENCES player(id), name VARCHAR(255), contribution INTEGER,
    decks_used INTEGER, decks_used_today INTEGER, boat_attacks INTEGER, repair_points INTEGER,
    battles_played INTEGER, wins INTEGER, losses INTEGER, captured_at TIMESTAMPTZ NOT NULL,
    UNIQUE(war_id, player_id)
);
CREATE INDEX war_participant_player ON war_participant(player_id, captured_at DESC, id DESC);

-- Preserve V1's last known state as the initial historical baseline.
INSERT INTO clan(tag, name, description, type, clan_score, clan_war_trophies, required_trophies,
                 donations_per_week, member_count, snapshot_date)
SELECT tag, name, description, type, clan_score, clan_war_trophies, required_trophies,
       donations_per_week, members, observed_at FROM clan_snapshot;
INSERT INTO clan(tag) SELECT DISTINCT clan_tag FROM clan_member_snapshot ON CONFLICT(tag) DO NOTHING;
INSERT INTO collection_run(id, clan_tag, kind, status, started_at, completed_at, deduplication_key)
SELECT gen_random_uuid(), tag, 'MIGRATION', 'SUCCESS', now(), now(), 'V2:' || tag FROM clan;
INSERT INTO clan_history(clan_id, run_id, name, description, type, clan_score, clan_war_trophies,
                         required_trophies, donations_per_week, member_count, captured_at)
SELECT c.id, r.id, c.name, c.description, c.type, c.clan_score, c.clan_war_trophies,
       c.required_trophies, c.donations_per_week, c.member_count, c.snapshot_date
FROM clan c JOIN collection_run r ON r.clan_tag = c.tag WHERE c.snapshot_date IS NOT NULL;
INSERT INTO player(player_tag, name, clan_id, first_seen_at, last_seen_at, active, role_current)
SELECT m.tag, m.name, c.id, m.observed_at, m.observed_at, TRUE, m.role
FROM clan_member_snapshot m JOIN clan c ON c.tag = m.clan_tag;
INSERT INTO clan_membership_history(player_id, clan_id, run_id, name, role, trophies, clan_rank,
                                    previous_clan_rank, donations, donations_received, exp_level, captured_at)
SELECT p.id, c.id, r.id, m.name, m.role, m.trophies, m.clan_rank, m.previous_clan_rank,
       m.donations, m.donations_received, m.exp_level, m.observed_at
FROM clan_member_snapshot m JOIN player p ON p.player_tag = m.tag
JOIN clan c ON c.tag = m.clan_tag JOIN collection_run r ON r.clan_tag = c.tag;
INSERT INTO membership_event(player_id, clan_id, run_id, type, role_current, observed_at)
SELECT p.id, p.clan_id, r.id, 'BASELINE', p.role_current, p.first_seen_at
FROM player p JOIN clan c ON c.id = p.clan_id JOIN collection_run r ON r.clan_tag = c.tag;
UPDATE clan c SET baseline_established = TRUE WHERE EXISTS(SELECT 1 FROM player p WHERE p.clan_id = c.id);
