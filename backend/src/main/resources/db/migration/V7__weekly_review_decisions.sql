CREATE TABLE weekly_review_decision (
    revision BIGSERIAL PRIMARY KEY,
    clan_tag VARCHAR(32) NOT NULL REFERENCES clan(tag),
    player_tag VARCHAR(32) NOT NULL REFERENCES player(player_tag),
    week DATE NOT NULL CHECK (extract(isodow from week) = 1),
    action VARCHAR(20) NOT NULL CHECK (action IN ('KEEP','TALK','DISMISS','WAIT','PENDING')),
    reason VARCHAR(2000) NOT NULL CHECK (length(trim(reason)) > 0),
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    request_key UUID NOT NULL,
    expected_revision BIGINT NOT NULL CHECK (expected_revision >= 0),
    evidence JSONB NOT NULL,
    UNIQUE(clan_tag, request_key)
);
CREATE INDEX weekly_review_decision_history ON weekly_review_decision(clan_tag, week, player_tag, revision DESC);

CREATE TRIGGER immutable_weekly_review_decision BEFORE UPDATE OR DELETE ON weekly_review_decision
    FOR EACH ROW EXECUTE FUNCTION reject_recommendation_history_change();
