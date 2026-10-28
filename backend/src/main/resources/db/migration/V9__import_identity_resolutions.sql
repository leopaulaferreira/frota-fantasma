CREATE TABLE import_resolution (
    revision BIGSERIAL PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES import_batch(id),
    request_key UUID NOT NULL,
    request JSONB NOT NULL,
    resolved_by VARCHAR(100) NOT NULL,
    resolved_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(40) NOT NULL CHECK (status IN ('PENDING_IDENTIFICATION','IDENTIFIED','CONFLICT','REJECTED')),
    resolution_method VARCHAR(50) NOT NULL,
    target_kind VARCHAR(20),
    target_id BIGINT,
    season_id INTEGER,
    section_index INTEGER,
    captured_at TIMESTAMPTZ,
    timezone VARCHAR(100),
    reason VARCHAR(2000) NOT NULL,
    manually_confirmed BOOLEAN NOT NULL CHECK (manually_confirmed),
    evidence JSONB NOT NULL,
    UNIQUE(batch_id, request_key)
);
CREATE INDEX import_resolution_batch ON import_resolution(batch_id,revision DESC);
CREATE TRIGGER immutable_import_resolution BEFORE UPDATE OR DELETE ON import_resolution
    FOR EACH ROW EXECUTE FUNCTION reject_recommendation_history_change();
