CREATE TABLE clan_snapshot (
    tag VARCHAR(32) PRIMARY KEY,
    name VARCHAR(255),
    description TEXT,
    type VARCHAR(40),
    clan_score INTEGER,
    clan_war_trophies INTEGER,
    required_trophies INTEGER,
    donations_per_week INTEGER,
    members INTEGER,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE clan_member_snapshot (
    tag VARCHAR(32) PRIMARY KEY,
    clan_tag VARCHAR(32) NOT NULL,
    name VARCHAR(255),
    role VARCHAR(40),
    exp_level INTEGER,
    trophies INTEGER,
    clan_rank INTEGER,
    previous_clan_rank INTEGER,
    donations INTEGER,
    donations_received INTEGER,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_clan_member_snapshot_clan_tag ON clan_member_snapshot (clan_tag);

