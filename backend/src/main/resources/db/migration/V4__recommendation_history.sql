CREATE TABLE recommendation_rule_version (
    id UUID PRIMARY KEY, clan_tag VARCHAR(32) NOT NULL, revision INTEGER NOT NULL CHECK (revision > 0),
    created_at TIMESTAMPTZ NOT NULL, reason VARCHAR(500) NOT NULL, settings JSONB NOT NULL,
    UNIQUE (clan_tag, revision), UNIQUE (id, clan_tag)
);
CREATE TABLE recommendation_rule_state (
    clan_tag VARCHAR(32) PRIMARY KEY, active_version UUID NOT NULL,
    FOREIGN KEY (active_version, clan_tag) REFERENCES recommendation_rule_version(id, clan_tag)
);
CREATE TABLE recommendation_evaluation (
    id UUID PRIMARY KEY, clan_tag VARCHAR(32) NOT NULL, week DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, analysis_cutoff TIMESTAMPTZ NOT NULL,
    rule_version_id UUID NOT NULL, formula_version VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL, report JSONB NOT NULL,
    FOREIGN KEY (rule_version_id, clan_tag) REFERENCES recommendation_rule_version(id, clan_tag),
    UNIQUE (clan_tag, idempotency_key), UNIQUE (id, clan_tag, week)
);
CREATE INDEX recommendation_evaluation_recent ON recommendation_evaluation(clan_tag, created_at DESC, id DESC);
CREATE TABLE member_status (
    evaluation_id UUID NOT NULL REFERENCES recommendation_evaluation(id), player_tag VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('MANTER','OBSERVAR','REVISAR')),
    color VARCHAR(10) NOT NULL CHECK (color IN ('GREEN','YELLOW','RED')),
    evidence JSONB NOT NULL, PRIMARY KEY (evaluation_id, player_tag)
);
CREATE INDEX member_status_player ON member_status(player_tag, evaluation_id);
CREATE TABLE weekly_award_batch (
    clan_tag VARCHAR(32) NOT NULL, week DATE NOT NULL, evaluation_id UUID NOT NULL,
    finalized_at TIMESTAMPTZ NOT NULL, timezone VARCHAR(80) NOT NULL,
    PRIMARY KEY (clan_tag, week),
    FOREIGN KEY (evaluation_id, clan_tag, week) REFERENCES recommendation_evaluation(id, clan_tag, week)
);
CREATE TABLE weekly_award (
    clan_tag VARCHAR(32) NOT NULL, week DATE NOT NULL,
    title VARCHAR(30) NOT NULL CHECK (title IN ('CAPITAO_DA_GUERRA','GUARDIAO_DA_FROTA','ALMA_IMORTAL')),
    player_tag VARCHAR(32) NOT NULL, evidence JSONB NOT NULL,
    PRIMARY KEY (clan_tag, week, title), UNIQUE (clan_tag, week, player_tag),
    FOREIGN KEY (clan_tag, week) REFERENCES weekly_award_batch(clan_tag, week)
);
-- Audit history is append-only, including for accidental direct updates by the application.
CREATE FUNCTION reject_recommendation_history_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Recommendation history is immutable';
END;
$$;
CREATE TRIGGER immutable_rule_version BEFORE UPDATE OR DELETE ON recommendation_rule_version
    FOR EACH ROW EXECUTE FUNCTION reject_recommendation_history_change();
CREATE TRIGGER immutable_evaluation BEFORE UPDATE OR DELETE ON recommendation_evaluation
    FOR EACH ROW EXECUTE FUNCTION reject_recommendation_history_change();
CREATE TRIGGER immutable_member_status BEFORE UPDATE OR DELETE ON member_status
    FOR EACH ROW EXECUTE FUNCTION reject_recommendation_history_change();
CREATE TRIGGER immutable_award_batch BEFORE UPDATE OR DELETE ON weekly_award_batch
    FOR EACH ROW EXECUTE FUNCTION reject_recommendation_history_change();
CREATE TRIGGER immutable_award BEFORE UPDATE OR DELETE ON weekly_award
    FOR EACH ROW EXECUTE FUNCTION reject_recommendation_history_change();
