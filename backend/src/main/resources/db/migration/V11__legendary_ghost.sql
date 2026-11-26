-- This project uses PostgreSQL, not MySQL. Tag is the durable player identity.
CREATE TABLE legendary_cycle (
    clan_tag VARCHAR(32) NOT NULL,
    week DATE NOT NULL,
    closes_at TIMESTAMPTZ NOT NULL,
    deadline TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    finalized_at TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL CHECK (status IN ('COLLECTING','AWAITING_WAR','COMPLETE','INCOMPLETE','MISSED')),
    record_source VARCHAR(16) NOT NULL CHECK (record_source IN ('OBSERVED','OFFICIAL_API')),
    record_era VARCHAR(100) NOT NULL,
    era_start TIMESTAMPTZ NOT NULL,
    trophy_limit INTEGER NOT NULL CHECK (trophy_limit > 0),
    official BOOLEAN NOT NULL DEFAULT FALSE,
    war_id BIGINT REFERENCES war(id),
    winner_tag VARCHAR(32),
    result JSONB,
    PRIMARY KEY (clan_tag, week),
    CHECK ((finalized_at IS NULL) = (result IS NULL))
);
CREATE TABLE legendary_snapshot (
    clan_tag VARCHAR(32) NOT NULL,
    week DATE NOT NULL,
    player_tag VARCHAR(32) NOT NULL REFERENCES player(player_tag),
    player_name VARCHAR(255) NOT NULL,
    role VARCHAR(40),
    full_cycle BOOLEAN NOT NULL,
    trophies INTEGER CHECK (trophies >= 0),
    api_best_trophies INTEGER CHECK (api_best_trophies >= 0),
    record_trophies INTEGER CHECK (record_trophies >= trophies),
    captured_at TIMESTAMPTZ,
    profile_clan_tag VARCHAR(32),
    error_code VARCHAR(100),
    attempts INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (clan_tag, week, player_tag),
    FOREIGN KEY (clan_tag, week) REFERENCES legendary_cycle(clan_tag, week),
    CHECK ((captured_at IS NULL) = (trophies IS NULL)),
    CHECK (record_trophies IS NULL OR captured_at IS NOT NULL)
);
CREATE INDEX legendary_player_history ON legendary_snapshot(clan_tag, player_tag, week DESC);
CREATE FUNCTION protect_legendary_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME = 'legendary_cycle' THEN
        IF OLD.finalized_at IS NOT NULL THEN RAISE EXCEPTION 'Finalized legendary cycle is immutable'; END IF;
    ELSE
        IF OLD.captured_at IS NOT NULL OR EXISTS (
            SELECT 1 FROM legendary_cycle WHERE clan_tag=OLD.clan_tag AND week=OLD.week AND finalized_at IS NOT NULL
        ) THEN RAISE EXCEPTION 'Legendary evidence is immutable'; END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_legendary_cycle BEFORE UPDATE OR DELETE ON legendary_cycle
    FOR EACH ROW EXECUTE FUNCTION protect_legendary_history();
CREATE TRIGGER immutable_legendary_snapshot BEFORE UPDATE OR DELETE ON legendary_snapshot
    FOR EACH ROW EXECUTE FUNCTION protect_legendary_history();
