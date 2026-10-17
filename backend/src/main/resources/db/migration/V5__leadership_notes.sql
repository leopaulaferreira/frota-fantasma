CREATE TABLE leadership_note (
    id UUID PRIMARY KEY,
    clan_tag VARCHAR(32) NOT NULL REFERENCES clan(tag),
    player_tag VARCHAR(32) NOT NULL REFERENCES player(player_tag),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('OBSERVATION','JUSTIFICATION','AGREEMENT')),
    body VARCHAR(2000) NOT NULL CHECK (length(trim(body)) > 0),
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    expires_on DATE,
    follow_up_on DATE,
    request_key UUID NOT NULL,
    UNIQUE (clan_tag, request_key),
    UNIQUE (id, clan_tag)
);
CREATE INDEX leadership_note_player ON leadership_note(clan_tag, player_tag, created_at DESC, id DESC);
CREATE TABLE leadership_note_resolution (
    note_id UUID PRIMARY KEY,
    clan_tag VARCHAR(32) NOT NULL,
    resolved_at TIMESTAMPTZ NOT NULL,
    resolved_by VARCHAR(255) NOT NULL,
    reason VARCHAR(1000) NOT NULL CHECK (length(trim(reason)) > 0),
    FOREIGN KEY (note_id, clan_tag) REFERENCES leadership_note(id, clan_tag)
);
CREATE TRIGGER immutable_leadership_note BEFORE UPDATE OR DELETE ON leadership_note
    FOR EACH ROW EXECUTE FUNCTION reject_recommendation_history_change();
CREATE TRIGGER immutable_leadership_note_resolution BEFORE UPDATE OR DELETE ON leadership_note_resolution
    FOR EACH ROW EXECUTE FUNCTION reject_recommendation_history_change();
