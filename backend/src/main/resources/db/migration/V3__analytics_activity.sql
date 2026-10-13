-- API activity is distinct from our observation timestamps. Historical rows stay unknown.
ALTER TABLE clan_membership_history ADD COLUMN api_last_seen TIMESTAMPTZ;
CREATE INDEX membership_analytics ON clan_membership_history(clan_id, player_id, captured_at, id);
